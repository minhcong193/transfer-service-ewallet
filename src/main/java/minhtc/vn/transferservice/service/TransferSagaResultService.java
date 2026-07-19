package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.integration.wallet.WalletCreditResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletFinalizeResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReleaseResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReservationResult;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransferSagaResultService {

    void markSourceReserved(
            UUID transferId,
            WalletReservationResult result,
            LocalDateTime reservedAt
    );

    void markTargetCredited(
            UUID transferId,
            WalletCreditResult result,
            LocalDateTime creditedAt
    );

    void markCompleted(
            UUID transferId,
            WalletFinalizeResult result,
            LocalDateTime finalizedAt
    );

    void markCompensated(
            UUID transferId,
            WalletReleaseResult result,
            LocalDateTime releasedAt
    );

    void markSourceReserveFailed(
            UUID transferId,
            String failureCode,
            String failureMessage
    );

    void markTargetCreditFailed(
            UUID transferId,
            String failureCode,
            String failureMessage
    );

    void markCompensationFailed(
            UUID transferId,
            String errorCode,
            String errorMessage,
            LocalDateTime nextRetryAt
    );

    void markManualReview(
            UUID transferId,
            String errorCode,
            String errorMessage
    );
}