package minhtc.vn.transferservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageCode {
    BANK_NOT_FOUND ("WA-0001", "WA-0001"),
    ;

    private final String code;
    private final String messageKey;
}
