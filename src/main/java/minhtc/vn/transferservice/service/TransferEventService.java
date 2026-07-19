package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.integration.wallet.WalletCreditResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletFinalizeResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReleaseResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReservationResult;

import java.util.UUID;

public interface TransferEventService {

    void appendTransferCreated(UUID transferId);

    void appendOtpPending(UUID transferId);

    void appendOtpVerified(UUID transferId);

    void appendSourceReserved(
            UUID transferId,
            WalletReservationResult result
    );

    void appendTargetCredited(
            UUID transferId,
            WalletCreditResult result
    );

    void appendTransferCompleted(
            UUID transferId,
            WalletFinalizeResult result
    );

    void appendTransferCompensated(
            UUID transferId,
            WalletReleaseResult result
    );

    void appendTransferFailed(
            UUID transferId,
            String failureCode,
            String failureMessage
    );

    void appendManualReviewRequired(
            UUID transferId,
            String errorCode,
            String errorMessage
    );

    void appendTransferCancelled(UUID transferId);
}