package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.client.WalletClient;
import minhtc.vn.transferservice.dto.response.WalletCommandResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletCreditResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletFinalizeResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReleaseResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReservationResult;
import minhtc.vn.transferservice.config.TransferRecoveryProperties;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.enums.TransferStatus;
import minhtc.vn.transferservice.enums.WalletCommandStatus;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.exception.WalletClientException;
import minhtc.vn.transferservice.exception.WalletClientTimeoutException;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.service.TransferRecoveryService;
import minhtc.vn.transferservice.service.TransferSagaPersistenceService;
import minhtc.vn.transferservice.service.TransferSagaResultService;
import minhtc.vn.transferservice.service.TransferSagaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferRecoveryServiceImpl
        implements TransferRecoveryService {

    private final TransferRepository transferRepository;

    private final WalletClient walletClient;

    private final TransferSagaService transferSagaService;

    private final TransferSagaResultService sagaResultService;

    private final TransferSagaPersistenceService persistenceService;

    private final TransferRecoveryProperties properties;

    private final TransactionTemplate transactionTemplate;

    private final Clock clock;

    /**
     * Claim các transfer đến hạn recovery rồi xử lý từng transfer bên ngoài
     * transaction claim.
     */
    @Override
    public void recoverDueTransfers() {
        List<UUID> transferIds =
                claimDueTransfers();

        for (UUID transferId : transferIds) {
            try {
                recoverOneTransfer(transferId);
            } catch (RuntimeException exception) {
                log.error(
                        "Recover transfer failed unexpectedly. transferId={}",
                        transferId,
                        exception
                );
            }
        }
    }

    /**
     * Recovery một transfer cụ thể.
     *
     * <p>Method này có thể được gọi bởi scheduler hoặc admin retry.</p>
     */
    @Override
    public void recoverOneTransfer(UUID transferId) {
        Transfer transfer =
                transferRepository.findById(transferId)
                        .orElseThrow(
                                () -> new TransferNotFoundException(
                                        transferId
                                )
                        );

        if (transfer.isTerminal()) {
            log.info(
                    "Skip recovery because transfer is terminal. transferId={}, status={}",
                    transfer.getId(),
                    transfer.getStatus()
            );
            return;
        }

        switch (transfer.getStatus()) {
            case SOURCE_RESERVE_PENDING ->
                    recoverSourceReserve(transfer);

            case TARGET_CREDIT_PENDING ->
                    recoverTargetCredit(transfer);

            case SOURCE_FINALIZE_PENDING ->
                    recoverSourceFinalize(transfer);

            case COMPENSATION_PENDING ->
                    transferSagaService.compensateSourceReservation(
                            transfer.getId(),
                            "RECOVERY_COMPENSATION"
                    );

            case COMPENSATING,
                 COMPENSATION_FAILED ->
                    recoverSourceRelease(transfer);

            default -> log.info(
                    "Transfer status does not require recovery. transferId={}, status={}",
                    transfer.getId(),
                    transfer.getStatus()
            );
        }
    }

    private List<UUID> claimDueTransfers() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = now();

            List<Transfer> transfers =
                    transferRepository.findDueForRecovery(
                            List.of(
                                    TransferStatus.SOURCE_RESERVE_PENDING,
                                    TransferStatus.TARGET_CREDIT_PENDING,
                                    TransferStatus.SOURCE_FINALIZE_PENDING,
                                    TransferStatus.COMPENSATION_PENDING,
                                    TransferStatus.COMPENSATING,
                                    TransferStatus.COMPENSATION_FAILED
                            ),
                            now,
                            PageRequest.of(
                                    0,
                                    properties.batchSize()
                            )
                    );

            LocalDateTime leaseUntil =
                    now.plus(
                            properties.processingLease()
                    );

            for (Transfer transfer : transfers) {
                transfer.acquireRecoveryLease(
                        leaseUntil
                );
            }

            return transfers.stream()
                    .map(Transfer::getId)
                    .toList();
        });
    }

    private void recoverSourceReserve(Transfer transfer) {
        WalletCommandResult commandResult =
                getCommandStatusOrScheduleRecovery(
                        transfer,
                        transfer.getSourceReserveCommandId(),
                        "SOURCE_RESERVE_RECOVERY"
                );

        if (commandResult == null) {
            return;
        }

        switch (commandResult.commandStatus()) {
            case SUCCEEDED -> {
                WalletReservationResult result =
                        toReservationResult(commandResult);

                LocalDateTime reservedAt =
                        commandResult.completedAt() != null
                                ? commandResult.completedAt()
                                : fallbackTime(commandResult);

                sagaResultService.markSourceReserved(
                        transfer.getId(),
                        result,
                        reservedAt
                );

                transferSagaService.creditTargetWallet(
                        transfer.getId()
                );
            }

            case FAILED -> {
                sagaResultService.markSourceReserveFailed(
                        transfer.getId(),
                        failureCode(commandResult),
                        failureMessage(commandResult)
                );
            }

            case NOT_FOUND -> {
                /*
                 * Wallet Service chưa từng nhận command hoặc đã mất record.
                 * Retry lại reserve bằng chính sourceReserveCommandId cũ.
                 */
                transferSagaService.reserveSourceWallet(
                        transfer.getId()
                );
            }

            case PENDING,
                 PROCESSING -> scheduleRecovery(
                    transfer.getId(),
                    "SOURCE_RESERVE_STILL_PROCESSING",
                    "Source reserve command is still processing"
            );
        }
    }

    private void recoverTargetCredit(Transfer transfer) {
        WalletCommandResult commandResult =
                getCommandStatusOrScheduleRecovery(
                        transfer,
                        transfer.getTargetCreditCommandId(),
                        "TARGET_CREDIT_RECOVERY"
                );

        if (commandResult == null) {
            return;
        }

        switch (commandResult.commandStatus()) {
            case SUCCEEDED -> {
                WalletCreditResult result =
                        toCreditResult(commandResult);

                LocalDateTime creditedAt =
                        commandResult.completedAt() != null
                                ? commandResult.completedAt()
                                : fallbackTime(commandResult);

                sagaResultService.markTargetCredited(
                        transfer.getId(),
                        result,
                        creditedAt
                );

                transferSagaService.finalizeSourceReservation(
                        transfer.getId()
                );
            }

            case FAILED -> {
                sagaResultService.markTargetCreditFailed(
                        transfer.getId(),
                        failureCode(commandResult),
                        failureMessage(commandResult)
                );

                transferSagaService.compensateSourceReservation(
                        transfer.getId(),
                        failureCode(commandResult)
                );
            }

            case NOT_FOUND -> {
                /*
                 * Nếu command chưa có ở Wallet Service thì retry credit bằng
                 * targetCreditCommandId cũ.
                 */
                transferSagaService.creditTargetWallet(
                        transfer.getId()
                );
            }

            case PENDING,
                 PROCESSING -> scheduleRecovery(
                    transfer.getId(),
                    "TARGET_CREDIT_STILL_PROCESSING",
                    "Target credit command is still processing"
            );
        }
    }

    private void recoverSourceFinalize(Transfer transfer) {
        WalletCommandResult commandResult =
                getCommandStatusOrScheduleRecovery(
                        transfer,
                        transfer.getSourceFinalizeCommandId(),
                        "SOURCE_FINALIZE_RECOVERY"
                );

        if (commandResult == null) {
            return;
        }

        switch (commandResult.commandStatus()) {
            case SUCCEEDED -> {
                WalletFinalizeResult result =
                        toFinalizeResult(commandResult);

                LocalDateTime finalizedAt =
                        commandResult.completedAt() != null
                                ? commandResult.completedAt()
                                : fallbackTime(commandResult);

                sagaResultService.markCompleted(
                        transfer.getId(),
                        result,
                        finalizedAt
                );
            }

            case FAILED -> {
                /*
                 * Target wallet đã được credit.
                 * Không được release source reservation.
                 *
                 * Nếu còn retry attempt thì schedule tiếp.
                 * Nếu quá nhiều lần thì chuyển manual review.
                 */
                if (
                        transfer.getRecoveryAttempts()
                                >= properties.maxRecoveryAttempts()
                ) {
                    sagaResultService.markManualReview(
                            transfer.getId(),
                            failureCode(commandResult),
                            failureMessage(commandResult)
                    );
                    return;
                }

                scheduleRecovery(
                        transfer.getId(),
                        failureCode(commandResult),
                        failureMessage(commandResult)
                );
            }

            case NOT_FOUND -> {
                /*
                 * Retry finalize bằng sourceFinalizeCommandId cũ.
                 * Không release.
                 */
                transferSagaService.finalizeSourceReservation(
                        transfer.getId()
                );
            }

            case PENDING,
                 PROCESSING -> scheduleRecovery(
                    transfer.getId(),
                    "SOURCE_FINALIZE_STILL_PROCESSING",
                    "Source finalize command is still processing"
            );
        }
    }

    private void recoverSourceRelease(Transfer transfer) {
        WalletCommandResult commandResult =
                getCommandStatusOrScheduleRecovery(
                        transfer,
                        transfer.getSourceReleaseCommandId(),
                        "SOURCE_RELEASE_RECOVERY"
                );

        if (commandResult == null) {
            return;
        }

        switch (commandResult.commandStatus()) {
            case SUCCEEDED -> {
                WalletReleaseResult result =
                        toReleaseResult(commandResult);

                LocalDateTime releasedAt =
                        commandResult.completedAt() != null
                                ? commandResult.completedAt()
                                : fallbackTime(commandResult);

                sagaResultService.markCompensated(
                        transfer.getId(),
                        result,
                        releasedAt
                );
            }

            case FAILED -> {
                if (
                        transfer.getCompensationAttempts()
                                >= properties.maxCompensationAttempts()
                ) {
                    sagaResultService.markManualReview(
                            transfer.getId(),
                            failureCode(commandResult),
                            failureMessage(commandResult)
                    );
                    return;
                }

                LocalDateTime nextRetryAt =
                        now().plus(
                                properties.retryDelay()
                        );

                sagaResultService.markCompensationFailed(
                        transfer.getId(),
                        failureCode(commandResult),
                        failureMessage(commandResult),
                        nextRetryAt
                );
            }

            case NOT_FOUND -> {
                transferSagaService.compensateSourceReservation(
                        transfer.getId(),
                        "RECOVERY_RELEASE_NOT_FOUND"
                );
            }

            case PENDING,
                 PROCESSING -> {
                LocalDateTime nextRetryAt =
                        now().plus(
                                properties.retryDelay()
                        );

                sagaResultService.markCompensationFailed(
                        transfer.getId(),
                        "SOURCE_RELEASE_STILL_PROCESSING",
                        "Source release command is still processing",
                        nextRetryAt
                );
            }
        }
    }

    private WalletCommandResult getCommandStatusOrScheduleRecovery(
            Transfer transfer,
            UUID commandId,
            String operation
    ) {
        try {
            WalletCommandResult result =
                    walletClient.getCommandStatus(
                            commandId
                    );

            validateCommandResult(
                    transfer,
                    commandId,
                    result
            );

            return result;

        } catch (WalletClientTimeoutException exception) {
            scheduleRecovery(
                    transfer.getId(),
                    operation + "_TIMEOUT",
                    exception.getMessage()
            );
            return null;

        } catch (WalletClientException exception) {
            if (exception.isRetryable()) {
                scheduleRecovery(
                        transfer.getId(),
                        exception.getErrorCode(),
                        exception.getMessage()
                );
                return null;
            }

            /*
             * Không chuyển FAILED tùy tiện khi query command status lỗi 4xx.
             * Đưa manual review nếu không thể xác định trạng thái command.
             */
            sagaResultService.markManualReview(
                    transfer.getId(),
                    exception.getErrorCode(),
                    exception.getMessage()
            );

            return null;
        }
    }

    private void validateCommandResult(
            Transfer transfer,
            UUID expectedCommandId,
            WalletCommandResult result
    ) {
        if (result == null) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_COMMAND_EMPTY_RESPONSE",
                    "Wallet command status returned empty response"
            );
        }

        if (
                result.commandId() == null
                        || !expectedCommandId.equals(
                        result.commandId()
                )
        ) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_COMMAND_ID_MISMATCH",
                    "Wallet command status commandId mismatch"
            );
        }

        if (
                result.transferId() != null
                        && !transfer.getId().equals(
                        result.transferId()
                )
        ) {
            throw new WalletClientException(
                    org.springframework.http.HttpStatusCode.valueOf(502),
                    "WALLET_COMMAND_TRANSFER_ID_MISMATCH",
                    "Wallet command status transferId mismatch"
            );
        }
    }

    private WalletReservationResult toReservationResult(
            WalletCommandResult command
    ) {
        return new WalletReservationResult(
                command.commandId(),
                command.reservationId(),
                command.walletTransactionId(),
                command.walletId(),
                command.transferId(),
                command.counterpartyWalletId(),
                command.amount(),
                command.currency(),
                null,
                null,
                null,
                null,
                command.reservationStatus(),
                command.transactionStatus(),
                command.failureCode(),
                command.failureMessage(),
                fallbackTime(command)
        );
    }

    private WalletCreditResult toCreditResult(
            WalletCommandResult command
    ) {
        return new WalletCreditResult(
                command.commandId(),
                command.walletTransactionId(),
                command.walletId(),
                command.transferId(),
                command.amount(),
                command.currency(),
                null,
                null,
                command.transactionStatus() != null
                        ? command.transactionStatus().name()
                        : null,
                command.failureCode(),
                command.failureMessage()
        );
    }

    private WalletFinalizeResult toFinalizeResult(
            WalletCommandResult command
    ) {
        return new WalletFinalizeResult(
                command.commandId(),
                command.reservationId(),
                command.walletTransactionId(),
                command.walletId(),
                command.transferId(),
                command.amount(),
                command.currency(),
                null,
                null,
                null,
                null,
                command.reservationStatus(),
                command.transactionStatus(),
                command.failureCode(),
                command.failureMessage(),
                command.completedAt()
        );
    }

    private WalletReleaseResult toReleaseResult(
            WalletCommandResult command
    ) {
        return new WalletReleaseResult(
                command.commandId(),
                command.reservationId(),
                command.walletTransactionId(),
                command.walletId(),
                command.transferId(),
                command.amount(),
                command.currency(),
                null,
                null,
                null,
                null,
                command.reservationStatus(),
                command.transactionStatus(),
                command.failureCode(),
                command.failureMessage(),
                command.completedAt()
        );
    }

    private void scheduleRecovery(
            UUID transferId,
            String errorCode,
            String errorMessage
    ) {
        LocalDateTime nextRetryAt =
                now().plus(
                        properties.retryDelay()
                );

        persistenceService.update(
                transferId,
                transfer -> transfer.scheduleRecovery(
                        errorCode,
                        errorMessage,
                        nextRetryAt
                )
        );

        log.warn(
                "Transfer scheduled for recovery. transferId={}, retryAt={}, code={}, message={}",
                transferId,
                nextRetryAt,
                errorCode,
                errorMessage
        );
    }

    private String failureCode(
            WalletCommandResult command
    ) {
        return command.failureCode() != null
                ? command.failureCode().name()
                : "WALLET_COMMAND_FAILED";
    }

    private String failureMessage(
            WalletCommandResult command
    ) {
        return command.failureMessage() != null
                ? command.failureMessage()
                : "Wallet command failed";
    }

    private LocalDateTime fallbackTime(
            WalletCommandResult command
    ) {
        if (command.completedAt() != null) {
            return command.completedAt();
        }

        if (command.createdAt() != null) {
            return command.createdAt();
        }

        return now();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}