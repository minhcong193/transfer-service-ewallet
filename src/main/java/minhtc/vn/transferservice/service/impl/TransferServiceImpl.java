package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.client.WalletClient;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.TransferTransitionContext;
import minhtc.vn.transferservice.dto.request.CancelTransferRequest;
import minhtc.vn.transferservice.dto.request.ConfirmTransferRequest;
import minhtc.vn.transferservice.dto.request.CreateTransferRequest;
import minhtc.vn.transferservice.dto.request.OtpChallenge;
import minhtc.vn.transferservice.dto.request.WalletSummary;
import minhtc.vn.transferservice.dto.response.OtpVerificationResult;
import minhtc.vn.transferservice.dto.transfer.TransferResponse;
import minhtc.vn.transferservice.enums.OtpVerificationStatus;
import minhtc.vn.transferservice.enums.TransferStatus;
import minhtc.vn.transferservice.exception.IdempotencyKeyConflictException;
import minhtc.vn.transferservice.exception.InvalidTransferStateException;
import minhtc.vn.transferservice.exception.TransferForbiddenException;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.exception.TransferValidationException;
import minhtc.vn.transferservice.mapper.TransferMapper;
import minhtc.vn.transferservice.otp.OtpRecipient;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.service.OtpService;
import minhtc.vn.transferservice.service.TransferEventService;
import minhtc.vn.transferservice.service.TransferLimitService;
import minhtc.vn.transferservice.service.TransferSagaService;
import minhtc.vn.transferservice.service.TransferService;
import minhtc.vn.transferservice.service.TransferStateMachineService;
import minhtc.vn.transferservice.util.SecurityUtil;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
     * Dùng để lấy:
     *
     * - sub
     * - email
     * - email_verified
     * - name/preferred_username
     *
     * trực tiếp từ JWT.
     */

    /**
     * Tạo transfer mới và gửi OTP tới email lấy từ JWT.
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

        /*
         * Idempotency replay phải được kiểm tra trước khi resolve email.
         *
         * Nếu transfer đã tồn tại thì trả lại kết quả cũ, không gửi thêm
         * OTP và không phụ thuộc email claim hiện tại.
         */
        Optional<Transfer> existing =
                transferRepository
                        .findBySourceOwnerKeycloakUserIdAndRequestIdempotencyKey(
                                ownerKeycloakUserId,
                                requestIdempotencyKey
                        );

        if (existing.isPresent()) {
            Transfer transfer = existing.get();

            if (!Objects.equals(
                    transfer.getRequestHash(),
                    requestHash
            )) {
                throw new IdempotencyKeyConflictException(
                        "Idempotency-Key was used with a different transfer request"
                );
            }

            return transferMapper.toResponse(transfer);
        }

        /*
         * Email chỉ lấy từ JWT đã được Spring Security validate.
         * Không nhận email từ request và không gọi User Service.
         */
        OtpRecipient otpRecipient = new OtpRecipient(ownerKeycloakUserId,
                SecurityUtil.getEmail(), SecurityUtil.getFullName());

        validateOtpRecipientOwner(
                ownerKeycloakUserId,
                otpRecipient
        );

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
                                    requestIdempotencyKey
                            );

                    /*
                     * saveAndFlush để Hibernate sinh UUIDv7 từ BaseEntity
                     * trước khi tạo Redis OTP key.
                     */
                    created =
                            transferRepository.saveAndFlush(
                                    created
                            );

                    /*
                     * OtpService sẽ:
                     *
                     * 1. Sinh raw OTP.
                     * 2. Hash và lưu Redis.
                     * 3. Gọi OtpDeliveryPort.
                     * 4. BrevoEmailOtpDeliveryAdapter gửi email SMTP.
                     *
                     * Email và tên người nhận nằm trong otpRecipient,
                     * được lấy từ JWT.
                     */
                    OtpChallenge challenge =
                            otpService.createOtp(
                                    created,
                                    otpRecipient
                            );

                    created.attachOtpChallenge(
                            challenge.challengeId(),
                            challenge.expiresAt()
                    );

                    stateMachineService.transitionManaged(
                            created,
                            TransferStatus.OTP_PENDING,
                            TransferTransitionContext.user(
                                    "OTP challenge created and sent to registered email",
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
     * Sau khi OTP verified, transaction được commit trước khi gọi Saga,
     * tránh giữ DB transaction khi gọi Wallet Service qua HTTP.
     */
    @Override
    public TransferResponse confirmTransfer(
            Jwt jwt,
            UUID transferId,
            ConfirmTransferRequest request
    ) {
        UUID ownerKeycloakUserId =
                currentUserId(jwt);

        validateConfirmRequest(request);

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

                    /*
                     * Trường hợp request confirm trước đó đã verify OTP
                     * nhưng service chưa bắt đầu hoặc chưa hoàn tất Saga.
                     */
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

        /*
         * Saga chạy sau khi transaction OTP đã commit.
         */
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
     * Gửi lại OTP tới email lấy từ JWT.
     */
    @Override
    public TransferResponse resendOtp(
            Jwt jwt,
            UUID transferId
    ) {
        /*
         * Resolver vừa lấy userId vừa lấy email/name từ JWT.
         */
        OtpRecipient otpRecipient = new OtpRecipient(UUID.fromString(SecurityUtil.getKeycloakUserId()),
                SecurityUtil.getEmail(), SecurityUtil.getFullName());

        UUID ownerKeycloakUserId =
                otpRecipient.ownerKeycloakUserId();

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

                    /*
                     * Email/name lấy từ otpRecipient, không truyền email
                     * từ request body.
                     */
                    OtpChallenge challenge =
                            otpService.resendOtp(
                                    transfer,
                                    otpRecipient
                            );

                    transfer.attachOtpChallenge(
                            challenge.challengeId(),
                            challenge.expiresAt()
                    );

                    stateMachineService.transitionManaged(
                            transfer,
                            TransferStatus.OTP_PENDING,
                            TransferTransitionContext.user(
                                    "OTP resent to registered email",
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

                    /*
                     * Xóa challenge để OTP cũ không thể được dùng lại
                     * sau khi transfer đã bị hủy.
                     */
                    otpService.deleteOtp(
                            transfer.getId()
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
        if (result.status()
                == OtpVerificationStatus.EXPIRED) {
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

    private Transfer getForUpdate(
            UUID transferId
    ) {
        if (transferId == null) {
            throw new TransferValidationException(
                    "transferId is required"
            );
        }

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
        if (request == null) {
            throw new TransferValidationException(
                    "Transfer request is required"
            );
        }

        if (request.sourceWalletId() == null) {
            throw new TransferValidationException(
                    "sourceWalletId is required"
            );
        }

        if (request.targetWalletId() == null) {
            throw new TransferValidationException(
                    "targetWalletId is required"
            );
        }

        if (request.sourceWalletId()
                .equals(request.targetWalletId())) {
            throw new TransferValidationException(
                    "Source wallet and target wallet must be different"
            );
        }

        if (request.amount() == null) {
            throw new TransferValidationException(
                    "amount is required"
            );
        }

        if (request.amount()
                .compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferValidationException(
                    "amount must be greater than zero"
            );
        }
    }

    private void validateConfirmRequest(
            ConfirmTransferRequest request
    ) {
        if (
                request == null
                        || request.otp() == null
                        || request.otp().isBlank()
        ) {
            throw new TransferValidationException(
                    "otp is required"
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

        if (!request.sourceWalletId()
                .equals(sourceWallet.id())) {
            throw new TransferValidationException(
                    "Source wallet response does not match request"
            );
        }

        if (!request.targetWalletId()
                .equals(targetWallet.id())) {
            throw new TransferValidationException(
                    "Target wallet response does not match request"
            );
        }

        if (
                sourceWallet.currency() == null
                        || targetWallet.currency() == null
        ) {
            throw new TransferValidationException(
                    "Wallet currency is missing"
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
        if (!transfer.isOwnedBySource(
                ownerKeycloakUserId
        )) {
            throw new TransferForbiddenException(
                    "You are not the owner of this transfer"
            );
        }
    }

    private void validateOtpRecipientOwner(
            UUID expectedOwnerKeycloakUserId,
            OtpRecipient recipient
    ) {
        if (recipient == null) {
            throw new TransferForbiddenException(
                    "OTP recipient cannot be resolved from JWT"
            );
        }

        if (!expectedOwnerKeycloakUserId.equals(
                recipient.ownerKeycloakUserId()
        )) {
            throw new TransferForbiddenException(
                    "OTP recipient does not match authenticated user"
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

    private UUID currentUserId(
            Jwt jwt
    ) {
        if (jwt == null) {
            throw new TransferForbiddenException(
                    "JWT authentication is required"
            );
        }

        String subject =
                jwt.getSubject();

        if (
                subject == null
                        || subject.isBlank()
        ) {
            throw new TransferForbiddenException(
                    "JWT subject is missing"
            );
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new TransferForbiddenException(
                    "JWT subject is not a valid UUID"
            );
        }
    }

    private String normalizeCurrency(
            String currency
    ) {
        if (
                currency == null
                        || currency.isBlank()
        ) {
            return DEFAULT_CURRENCY;
        }

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeCancelReason(
            CancelTransferRequest request
    ) {
        if (
                request == null
                        || request.reason() == null
        ) {
            return "Transfer cancelled by user";
        }

        String reason =
                request.reason().trim();

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
                        + normalizeNullable(
                        request.description()
                );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            canonical.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(hash);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate transfer request hash",
                    exception
            );
        }
    }

    private String normalizeNullable(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private String generateTransferCode() {
        return "TRF-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);
    }
}