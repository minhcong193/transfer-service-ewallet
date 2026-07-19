package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.request.WalletSummary;
import minhtc.vn.transferservice.domain.Transfer;

import java.util.UUID;

public interface TransferOwnershipService {

    void assertSourceWalletOwnership(
            UUID currentUserId,
            WalletSummary sourceWallet,
            UUID correlationId
    );

    void assertCanViewTransfer(
            UUID currentUserId,
            Transfer transfer,
            UUID correlationId
    );

    void assertCanManageTransfer(
            UUID currentUserId,
            Transfer transfer,
            UUID correlationId
    );
}