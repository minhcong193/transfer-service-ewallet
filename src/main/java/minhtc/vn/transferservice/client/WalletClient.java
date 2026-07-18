package minhtc.vn.transferservice.client;

import minhtc.vn.transferservice.dto.request.*;
import minhtc.vn.transferservice.dto.response.*;

import java.util.UUID;

public interface WalletClient {

    WalletSummary getWallet(UUID walletId);

    WalletReservationResult reserve(
            UUID walletId,
            ReserveWalletRequest request
    );

    WalletCreditResult credit(
            UUID walletId,
            CreditWalletRequest request
    );

    WalletFinalizeResult finalizeReservation(
            UUID walletId,
            UUID reservationId,
            FinalizeReservationRequest request
    );

    WalletReleaseResult releaseReservation(
            UUID walletId,
            UUID reservationId,
            ReleaseReservationRequest request
    );

    WalletCommandResult getCommandStatus(UUID commandId);
}
