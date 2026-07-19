package minhtc.vn.transferservice.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class WalletClientTimeoutException extends RuntimeException {

    private final UUID commandId;

    private final String operation;

    public WalletClientTimeoutException(
            UUID commandId,
            String operation,
            Throwable cause
    ) {
        super(
                "Wallet Service request timed out. operation="
                        + operation
                        + ", commandId="
                        + commandId,
                cause
        );

        this.commandId = commandId;
        this.operation = operation;
    }
}
