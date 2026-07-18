package minhtc.vn.transferservice.exception;

import lombok.Data;
import minhtc.vn.walletservice.enums.MessageCode;

@Data
public class BadRequestException extends RuntimeException {
    private String code;
    private String messageKey;

    private Object[] messageArgs;



    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(MessageCode messageCode) {
        super();
        this.code = messageCode.getCode();
        this.messageKey = messageCode.getMessageKey();
    }

    public BadRequestException(MessageCode messageCode, Object[] messageArgs) {
        super();
        this.code = messageCode.getCode();
        this.messageKey = messageCode.getMessageKey();
        this.messageArgs = messageArgs;
    }
}
