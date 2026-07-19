package minhtc.vn.transferservice.exception;

public class InvalidTransferAmountException
        extends RuntimeException {

    public InvalidTransferAmountException(String message) {
        super(message);
    }
}
