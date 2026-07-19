package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.client.WalletClient;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.request.CancelTransferRequest;
import minhtc.vn.transferservice.dto.request.ConfirmTransferRequest;
import minhtc.vn.transferservice.dto.request.CreateTransferRequest;
import minhtc.vn.transferservice.dto.request.WalletSummary;
import minhtc.vn.transferservice.dto.transfer.TransferResponse;
import minhtc.vn.transferservice.enums.OtpVerificationStatus;
import minhtc.vn.transferservice.enums.TransferStatus;
import minhtc.vn.transferservice.exception.IdempotencyKeyConflictException;
import minhtc.vn.transferservice.exception.InvalidTransferStateException;
import minhtc.vn.transferservice.exception.TransferForbiddenException;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.exception.TransferValidationException;
import minhtc.vn.transferservice.mapper.TransferMapper;
import minhtc.vn.transferservice.dto.request.OtpChallenge;
import minhtc.vn.transferservice.dto.response.OtpVerificationResult;
import minhtc.vn.transferservice.dto.TransferTransitionContext;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.service.OtpService;
import minhtc.vn.transferservice.service.TransferEventService;
import minhtc.vn.transferservice.service.TransferLimitService;
import minhtc.vn.transferservice.service.TransferSagaService;
import minhtc.vn.transferservice.service.TransferService;
import minhtc.vn.transferservice.service.TransferStateMachineService;
import minhtc.vn.transferservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private static final String DEFAULT_CURRENCY = "VND";

    private final TransferRepository transferRepository;

    private final WalletClient walletClient;

    private final OtpService otpService;

    private final TransferSagaService transferSagaService;

    private final TransferStateMachineService stateMachineService;

    private final TransferEventService transferEventService;

    private final TransferLimitService transferLimitService;

    private final TransferMapper transferMapper;

    private final TransactionTemplate transactionTemplate;

    /**
     * Tạo transfer mới và gửi OTP.
     *
     * <p>Luồng:</p>
     *
     * <pre>
     * validate idempotency key
     * validate amount/currency
     * get source wallet
     * get target wallet
     * check ownership source wallet
     * check transfer limit
     * create Transfer status CREATED
     * create OTP in Redis
     * attach OTP challenge
     * CREATED -> OTP_PENDING
     * append outbox events
     * </pre>
     */
    @Override
    public TransferResponse createTransfer(
            Jwt jwt,
            String idempotencyKey,
            CreateTransferRequest request
    ) {
        UUID ownerKeycloakUserId =
                currentUserId(jwt);

        UUID requestIdempotencyKey =
                parseIdempotencyKey(idempotencyKey);

        validateCreateRequest(request);

        String normalizedCurrency =
                normalizeCurrency(request.currency());

        String requestHash =
                buildCreateRequestHash(
                        request,
                        normalizedCurrency
                );

        Optional<Transfer> existing =
                transferRepository
                        .findBySourceOwnerKeycloakUserIdAndRequestIdempotencyKey(
                                ownerKeycloakUserId,
                                requestIdempotencyKey
                        );

        if (existing.isPresent()) {
            Transfer transfer = existing.get();

            if (!transfer.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException(
                        "Idempotency-Key was used with a different transfer request"
                );
            }

            return transferMapper.toResponse(transfer);
        }

        WalletSummary sourceWallet =
                walletClient.getWallet(
                        request.sourceWalletId()
                );

        WalletSummary targetWallet =
                walletClient.getWallet(
                        request.targetWalletId()
                );

        validateWalletsForCreate(
                ownerKeycloakUserId,
                sourceWallet,
                targetWallet,
                request
        );

        transferLimitService.validateTransferLimit(
                ownerKeycloakUserId,
                request.amount(),
                normalizedCurrency
        );

        Transfer transfer =
                transactionTemplate.execute(status -> {
                    Transfer created =
                            Transfer.create(
                                    generateTransferCode(),
                                    request.sourceWalletId(),
                                    request.targetWalletId(),
                                    ownerKeycloakUserId,
                                    targetWallet.ownerKeycloakUserId(),
                                    request.amount(),
                                    normalizedCurrency,
                                    request.description(),
                                    requestIdempotencyKey,
                                    requestHash,
                                    correlationIdFromJwt(jwt)
                            );

                    /*
                     * saveAndFlush để Hibernate sinh UUIDv7 từ BaseEntity.
                     */
                    created =
                            transferRepository.saveAndFlush(
                                    created
                            );

                    OtpChallenge challenge =
                            otpService.createOtp(
                                    created.getId(),
                                    ownerKeycloakUserId
                            );

                    created.attachOtpChallenge(
                            challenge.challengeId(),
                            challenge.expiresAt()
                    );

                    stateMachineService.transitionManaged(
                            created,
                            TransferStatus.OTP_PENDING,
                            TransferTransitionContext.user(
                                    "OTP challenge created for transfer confirmation",
                                    created.getCorrelationId()
                            )
                    );

                    transferEventService.appendTransferCreated(
                            created.getId()
                    );

                    transferEventService.appendOtpPending(
                            created.getId()
                    );

                    return created;
                });

        return transferMapper.toResponse(
                Objects.requireNonNull(transfer)
        );
    }

    /**
     * Xác nhận OTP và bắt đầu Saga.
     *
     * <p>Sau khi OTP verified, method này commit transaction trước, sau đó
     * mới gọi Saga để tránh giữ database transaction trong lúc gọi Wallet
     * Service qua HTTP.</p>
     */
    @Override
    public TransferResponse confirmTransfer(
            Jwt jwt,
            UUID transferId,
            ConfirmTransferRequest request
    ) {
        UUID ownerKeycloakUserId =
                currentUserId(jwt);

        Boolean shouldStartSaga =
                transactionTemplate.execute(status -> {
                    Transfer transfer =
                            getForUpdate(transferId);

                    validateSourceOwner(
                            transfer,
                            ownerKeycloakUserId
                    );

                    if (transfer.isTerminal()) {
                        return false;
                    }

                    if (transfer.getStatus()
                            == TransferStatus.OTP_VERIFIED) {
                        return true;
                    }

                    if (
                            transfer.getStatus()
                                    != TransferStatus.OTP_PENDING
                                    && transfer.getStatus()
                                    != TransferStatus.OTP_FAILED
                    ) {
                        throw new InvalidTransferStateException(
                                transfer.getId(),
                                transfer.getStatus(),
                                "Transfer is not waiting for OTP confirmation"
                        );
                    }

                    OtpVerificationResult verificationResult =
                            otpService.verifyOtp(
                                    transfer.getId(),
                                    request.otp()
                            );

                    if (
                            verificationResult.status()
                                    == OtpVerificationStatus.VERIFIED
                    ) {
                        stateMachineService.transitionManaged(
                                transfer,
                                TransferStatus.OTP_VERIFIED,
                                TransferTransitionContext.user(
                                        "OTP verified successfully",
                                        transfer.getCorrelationId()
                                )
                        );

                        transferEventService.appendOtpVerified(
                                transfer.getId()
                        );

                        return true;
                    }

                    handleOtpVerificationFailure(
                            transfer,
                            verificationResult
                    );

                    return false;
                });

        if (Boolean.TRUE.equals(shouldStartSaga)) {
            transferSagaService.execute(transferId);
        }

        Transfer latest =
                transferRepository.findById(transferId)
                        .orElseThrow(
                                () -> new TransferNotFoundException(
                                        transferId
                                )
                        );

        return transferMapper.toResponse(latest);
    }

    /**
     * Gửi lại OTP.
     *
     * <p>Chỉ cho phép resend trước khi OTP verified.</p>
     */
    @Override
    public TransferResponse resendOtp(
            Jwt jwt,
            UUID transferId
    ) {
        UUID ownerKeycloakUserId =
                currentUserId(jwt);

        Transfer updated =
                transactionTemplate.execute(status -> {
                    Transfer transfer =
                            getForUpdate(transferId);

                    validateSourceOwner(
                            transfer,
                            ownerKeycloakUserId
                    );

                    if (transfer.isTerminal()) {
                        throw new InvalidTransferStateException(
                                transfer.getId(),
                                transfer.getStatus(),
                                "Cannot resend OTP for terminal transfer"
                        );
                    }

                    if (
                            transfer.getStatus()
                                    != TransferStatus.OTP_PENDING
                                    && transfer.getStatus()
                                    != TransferStatus.OTP_FAILED
                                    && transfer.getStatus()
                                    != TransferStatus.OTP_EXPIRED
                    ) {
                        throw new InvalidTransferStateException(
                                transfer.getId(),
                                transfer.getStatus(),
                                "Cannot resend OTP after transfer has passed OTP step"
                        );
                    }

                    OtpChallenge challenge =
                            otpService.resendOtp(
                                    transfer.getId(),
                                    ownerKeycloakUserId
                            );

                    transfer.attachOtpChallenge(
                            challenge.challengeId(),
                            challenge.expiresAt()
                    );

                    stateMachineService.transitionManaged(
                            transfer,
                            TransferStatus.OTP_PENDING,
                            TransferTransitionContext.user(
                                    "OTP resent for transfer confirmation",
                                    transfer.getCorrelationId()
                            )
                    );

                    transferEventService.appendOtpPending(
                            transfer.getId()
                    );

                    return transfer;
                });

        return transferMapper.toResponse(
                Objects.requireNonNull(updated)
        );
    }

    /**
     * Hủy transfer trước khi OTP được xác nhận.
     *
     * <p>Không cho hủy khi tiền đã bắt đầu reserve/credit vì lúc đó phải
     * đi theo Saga hoặc Recovery.</p>
     */
    @Override
    public TransferResponse cancelTransfer(
            Jwt jwt,
            UUID transferId,
            CancelTransferRequest request
    ) {
        UUID ownerKeycloakUserId =
                currentUserId(jwt);

        Transfer cancelled =
                transactionTemplate.execute(status -> {
                    Transfer transfer =
                            getForUpdate(transferId);

                    validateSourceOwner(
                            transfer,
                            ownerKeycloakUserId
                    );

                    if (transfer.isTerminal()) {
                        return transfer;
                    }

                    if (
                            transfer.getStatus()
                                    != TransferStatus.CREATED
                                    && transfer.getStatus()
                                    != TransferStatus.OTP_PENDING
                                    && transfer.getStatus()
                                    != TransferStatus.OTP_FAILED
                                    && transfer.getStatus()
                                    != TransferStatus.OTP_EXPIRED
                    ) {
                        throw new InvalidTransferStateException(
                                transfer.getId(),
                                transfer.getStatus(),
                                "Transfer cannot be cancelled after OTP was verified"
                        );
                    }

                    stateMachineService.transitionManaged(
                            transfer,
                            TransferStatus.CANCELLED,
                            TransferTransitionContext.user(
                                    normalizeCancelReason(request),
                                    transfer.getCorrelationId()
                            )
                    );

                    transferEventService.appendTransferCancelled(
                            transfer.getId()
                    );

                    return transfer;
                });

        return transferMapper.toResponse(
                Objects.requireNonNull(cancelled)
        );
    }

    private void handleOtpVerificationFailure(
            Transfer transfer,
            OtpVerificationResult result
    ) {
        if (
                result.status()
                        == OtpVerificationStatus.EXPIRED
        ) {
            stateMachineService.transitionManaged(
                    transfer,
                    TransferStatus.OTP_EXPIRED,
                    TransferTransitionContext.failure(
                            "OTP expired",
                            "OTP_EXPIRED",
                            "OTP has expired",
                            transfer.getCorrelationId()
                    )
            );

            return;
        }

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.OTP_FAILED,
                TransferTransitionContext.failure(
                        "OTP verification failed",
                        "OTP_INVALID",
                        "OTP is invalid or verification limit exceeded",
                        transfer.getCorrelationId()
                )
        );
    }

    private Transfer getForUpdate(UUID transferId) {
        return transferRepository
                .findByIdForUpdate(transferId)
                .orElseThrow(
                        () -> new TransferNotFoundException(
                                transferId
                        )
                );
    }

    private void validateCreateRequest(
            CreateTransferRequest request
    ) {
        if (request.sourceWalletId().equals(request.targetWalletId())) {
            throw new TransferValidationException(
                    "Source wallet and target wallet must be different"
            );
        }

        if (request.amount() == null) {
            throw new TransferValidationException(
                    "amount is required"
            );
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferValidationException(
                    "amount must be greater than zero"
            );
        }
    }

    private void validateWalletsForCreate(
            UUID ownerKeycloakUserId,
            WalletSummary sourceWallet,
            WalletSummary targetWallet,
            CreateTransferRequest request
    ) {
        if (sourceWallet == null) {
            throw new TransferValidationException(
                    "Source wallet does not exist"
            );
        }

        if (targetWallet == null) {
            throw new TransferValidationException(
                    "Target wallet does not exist"
            );
        }

        if (!ownerKeycloakUserId.equals(
                sourceWallet.ownerKeycloakUserId()
        )) {
            throw new TransferForbiddenException(
                    "You are not the owner of source wallet"
            );
        }

        if (!request.sourceWalletId().equals(sourceWallet.id())) {
            throw new TransferValidationException(
                    "Source wallet response does not match request"
            );
        }

        if (!request.targetWalletId().equals(targetWallet.id())) {
            throw new TransferValidationException(
                    "Target wallet response does not match request"
            );
        }

        if (!sourceWallet.currency().equalsIgnoreCase(
                targetWallet.currency()
        )) {
            throw new TransferValidationException(
                    "Source wallet and target wallet currency must match"
            );
        }
    }

    private void validateSourceOwner(
            Transfer transfer,
            UUID ownerKeycloakUserId
    ) {
        if (!transfer.isOwnedBySource(ownerKeycloakUserId)) {
            throw new TransferForbiddenException(
                    "You are not the owner of this transfer"
            );
        }
    }

    private UUID parseIdempotencyKey(
            String idempotencyKey
    ) {
        if (
                idempotencyKey == null
                        || idempotencyKey.isBlank()
        ) {
            throw new TransferValidationException(
                    "Idempotency-Key header is required"
            );
        }

        try {
            return UUID.fromString(
                    idempotencyKey.trim()
            );
        } catch (IllegalArgumentException exception) {
            throw new TransferValidationException(
                    "Idempotency-Key must be a valid UUID"
            );
        }
    }

    private UUID currentUserId(Jwt jwt) {
        /*
         * Nếu bạn đã có SecurityUtil.getKeycloakUserId() thì có thể thay
         * toàn bộ method này bằng SecurityUtil.
         */
        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new TransferForbiddenException(
                    "JWT subject is missing"
            );
        }

        return UUID.fromString(subject);
    }

    private UUID correlationIdFromJwt(Jwt jwt) {
        /*
         * Nếu API Gateway truyền X-Correlation-Id vào RequestContext thì
         * nên lấy từ RequestContextProvider thay vì JWT.
         *
         * Ở đây để null cũng được, vì Wallet request body/header vẫn có thể
         * lấy từ request context.
         */
        return null;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeCancelReason(
            CancelTransferRequest request
    ) {
        if (request == null || request.reason() == null) {
            return "Transfer cancelled by user";
        }

        String reason = request.reason().trim();

        return reason.isBlank()
                ? "Transfer cancelled by user"
                : reason;
    }

    private String buildCreateRequestHash(
            CreateTransferRequest request,
            String normalizedCurrency
    ) {
        String canonical =
                request.sourceWalletId()
                        + "|"
                        + request.targetWalletId()
                        + "|"
                        + request.amount().setScale(4)
                        + "|"
                        + normalizedCurrency
                        + "|"
                        + normalizeNullable(request.description());

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            canonical.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of().formatHex(hash);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate transfer request hash",
                    exception
            );
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private String generateTransferCode() {
        return "TRF-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);
    }
}