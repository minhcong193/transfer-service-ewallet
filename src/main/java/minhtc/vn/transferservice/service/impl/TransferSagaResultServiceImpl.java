package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;

import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.integration.wallet.WalletCreditResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletFinalizeResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReleaseResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReservationResult;
import minhtc.vn.transferservice.enums.TransferStatus;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.dto.TransferTransitionContext;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.service.TransferEventService;
import minhtc.vn.transferservice.service.TransferSagaResultService;
import minhtc.vn.transferservice.service.TransferStateMachineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferSagaResultServiceImpl
        implements TransferSagaResultService {

    private final TransferRepository transferRepository;

    private final TransferStateMachineService stateMachineService;

    private final TransferEventService transferEventService;

    /**
     * Reserve ví nguồn thành công.
     *
     * <p>Trong cùng một transaction:</p>
     *
     * <ul>
     *     <li>Lưu reservationId và sourceReserveTransactionId.</li>
     *     <li>Chuyển trạng thái sang SOURCE_RESERVED.</li>
     *     <li>Ghi status history.</li>
     *     <li>Append outbox event TRANSFER_SOURCE_RESERVED.</li>
     * </ul>
     */
    @Override
    @Transactional
    public void markSourceReserved(
            UUID transferId,
            WalletReservationResult result,
            LocalDateTime reservedAt
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.applySourceReservation(
                result.reservationId(),
                result.walletTransactionId(),
                reservedAt
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.SOURCE_RESERVED,
                TransferTransitionContext.saga(
                        "Source wallet amount reserved successfully",
                        transfer.getCorrelationId()
                )
        );

        transferEventService.appendSourceReserved(
                transferId,
                result
        );
    }

    /**
     * Credit ví đích thành công.
     *
     * <p>Sau bước này chưa được coi là Transfer hoàn tất. Transfer chỉ hoàn
     * tất sau khi finalize reservation ở ví nguồn thành công.</p>
     */
    @Override
    @Transactional
    public void markTargetCredited(
            UUID transferId,
            WalletCreditResult result,
            LocalDateTime creditedAt
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.applyTargetCreditResult(
                result.walletTransactionId(),
                creditedAt
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.TARGET_CREDITED,
                TransferTransitionContext.saga(
                        "Target wallet credited successfully",
                        transfer.getCorrelationId()
                )
        );

        transferEventService.appendTargetCredited(
                transferId,
                result
        );
    }

    /**
     * Finalize ví nguồn thành công và hoàn tất Transfer.
     *
     * <p>Method này thực hiện hai transition trong cùng transaction:</p>
     *
     * <pre>
     * SOURCE_FINALIZE_PENDING -> SOURCE_FINALIZED
     * SOURCE_FINALIZED        -> COMPLETED
     * </pre>
     */
    @Override
    @Transactional
    public void markCompleted(
            UUID transferId,
            WalletFinalizeResult result,
            LocalDateTime finalizedAt
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.applySourceFinalizeResult(
                result.walletTransactionId(),
                finalizedAt
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.SOURCE_FINALIZED,
                TransferTransitionContext.saga(
                        "Source reservation finalized successfully",
                        transfer.getCorrelationId()
                )
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.COMPLETED,
                TransferTransitionContext.saga(
                        "Transfer completed successfully",
                        transfer.getCorrelationId()
                )
        );

        transferEventService.appendTransferCompleted(
                transferId,
                result
        );
    }

    /**
     * Compensation thành công.
     *
     * <p>Được gọi khi Wallet Service release reservation ở ví nguồn thành
     * công sau khi credit target thất bại chắc chắn.</p>
     */
    @Override
    @Transactional
    public void markCompensated(
            UUID transferId,
            WalletReleaseResult result,
            LocalDateTime releasedAt
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.applySourceReleaseResult(
                result.walletTransactionId(),
                releasedAt
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.COMPENSATED,
                TransferTransitionContext.saga(
                        "Source reservation released successfully",
                        transfer.getCorrelationId()
                )
        );

        transferEventService.appendTransferCompensated(
                transferId,
                result
        );
    }

    /**
     * Reserve ví nguồn thất bại chắc chắn.
     *
     * <p>Ví dụ:</p>
     *
     * <ul>
     *     <li>Không đủ số dư.</li>
     *     <li>Ví nguồn bị khóa.</li>
     *     <li>Wallet Service trả business failure rõ ràng.</li>
     * </ul>
     *
     * <p>Luồng trạng thái:</p>
     *
     * <pre>
     * SOURCE_RESERVE_PENDING -> SOURCE_RESERVE_FAILED -> FAILED
     * </pre>
     */
    @Override
    @Transactional
    public void markSourceReserveFailed(
            UUID transferId,
            String failureCode,
            String failureMessage
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.markFailure(
                failureCode,
                failureMessage
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.SOURCE_RESERVE_FAILED,
                TransferTransitionContext.failure(
                        "Source wallet reservation failed",
                        failureCode,
                        failureMessage,
                        transfer.getCorrelationId()
                )
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.FAILED,
                TransferTransitionContext.failure(
                        "Transfer failed because source reservation was rejected",
                        failureCode,
                        failureMessage,
                        transfer.getCorrelationId()
                )
        );

        transferEventService.appendTransferFailed(
                transferId,
                failureCode,
                failureMessage
        );
    }

    /**
     * Credit ví đích thất bại chắc chắn.
     *
     * <p>Sau khi reserve ví nguồn đã thành công mà credit ví đích bị từ
     * chối rõ ràng, Transfer phải chuyển sang compensation.</p>
     *
     * <pre>
     * TARGET_CREDIT_PENDING -> TARGET_CREDIT_FAILED
     * TARGET_CREDIT_FAILED  -> COMPENSATION_PENDING
     * </pre>
     */
    @Override
    @Transactional
    public void markTargetCreditFailed(
            UUID transferId,
            String failureCode,
            String failureMessage
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.markFailure(
                failureCode,
                failureMessage
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.TARGET_CREDIT_FAILED,
                TransferTransitionContext.failure(
                        "Target wallet credit failed",
                        failureCode,
                        failureMessage,
                        transfer.getCorrelationId()
                )
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.COMPENSATION_PENDING,
                TransferTransitionContext.failure(
                        "Source reservation must be released",
                        failureCode,
                        failureMessage,
                        transfer.getCorrelationId()
                )
        );
    }

    /**
     * Release reservation lỗi hoặc chưa xác định.
     *
     * <p>Luồng trạng thái:</p>
     *
     * <pre>
     * COMPENSATING -> COMPENSATION_FAILED
     * </pre>
     *
     * <p>Sau đó Recovery Scheduler sẽ retry release bằng đúng
     * sourceReleaseCommandId cũ.</p>
     */
    @Override
    @Transactional
    public void markCompensationFailed(
            UUID transferId,
            String errorCode,
            String errorMessage,
            LocalDateTime nextRetryAt
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.scheduleCompensationRetry(
                errorCode,
                errorMessage,
                nextRetryAt
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.COMPENSATION_FAILED,
                TransferTransitionContext.recovery(
                        "Compensation failed and will be retried",
                        errorCode,
                        errorMessage,
                        transfer.getCorrelationId()
                )
        );
    }

    /**
     * Chuyển Transfer sang MANUAL_REVIEW.
     *
     * <p>Dùng khi dữ liệu không nhất quán hoặc không thể tự recovery an
     * toàn.</p>
     */
    @Override
    @Transactional
    public void markManualReview(
            UUID transferId,
            String errorCode,
            String errorMessage
    ) {
        Transfer transfer =
                getForUpdate(transferId);

        transfer.markFailure(
                errorCode,
                errorMessage
        );

        stateMachineService.transitionManaged(
                transfer,
                TransferStatus.MANUAL_REVIEW,
                TransferTransitionContext.failure(
                        "Transfer requires manual review",
                        errorCode,
                        errorMessage,
                        transfer.getCorrelationId()
                )
        );

        transferEventService.appendManualReviewRequired(
                transferId,
                errorCode,
                errorMessage
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
}