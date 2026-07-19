package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.command.ConfirmTransferCommand;
import minhtc.vn.transferservice.dto.command.CreateTransferCommand;
import minhtc.vn.transferservice.dto.transfer.TransferResponse;

import java.util.UUID;

public interface TransferSagaOrchestrator {

    TransferResponse startTransfer(
            CreateTransferCommand command
    );

    TransferResponse confirmTransfer(
            UUID transferId,
            ConfirmTransferCommand command
    );

    void recoverTransfer(UUID transferId);

    void compensateTransfer(UUID transferId);
}
