package minhtc.vn.transferservice.exception;

import minhtc.vn.transferservice.enums.TransferStatus;

import java.util.UUID;

public class InvalidTransferStateException
        extends RuntimeException {

    public InvalidTransferStateException(
            TransferStatus currentStatus,
            TransferStatus targetStatus
    ) {
        super(
                "Cannot transition transfer from %s to %s"
                        .formatted(
                                currentStatus,
                                targetStatus
                        )
        );
    }

    public InvalidTransferStateException(
            UUID transferId,
            TransferStatus currentStatus,
            String message
    ) {
        super(
                "Transfer %s in status %s: %s"
                        .formatted(
                                transferId,
                                currentStatus,
                                message
                        )
        );
    }
}
