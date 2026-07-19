package minhtc.vn.transferservice.exception;

public class TransferOwnershipException
        extends RuntimeException {

    public TransferOwnershipException() {
        /*
         * Không trả thông tin chi tiết về owner thực tế để tránh
         * lộ dữ liệu bảo mật.
         */
        super("You do not have permission to access this resource");
    }
}