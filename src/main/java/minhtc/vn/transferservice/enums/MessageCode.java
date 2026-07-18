package minhtc.vn.transferservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageCode {
    BANK_NOT_FOUND ("WA-0001", "WA-0001"),
    USER_NOT_HAVE_WALLET ("WA-0002", "WA-0002"),
    WALLET_NOT_FOUND("WA-0003", "WA-0003"),
    OWNERSHIP_VIOLATION("WA-0004", "WA-0004"),
    BANK_LINK_NOT_FOUND("WA-0005", "WA-0005"),

    // Top-Up Specific
    WALLET_LOCKED("WA-0006", "WA-0006"),
    BANK_LINK_WALLET_MISMATCH("WA-0007", "WA-0007"),
    BANK_LINK_NOT_VERIFIED("WA-0008", "WA-0008"),
    IDEMPOTENCY_KEY_REQUIRED("WA-0009", "WA-0009"),
    IDEMPOTENCY_KEY_TOO_LONG("WA-0010", "WA-0010"),
    DUPLICATE_IDEMPOTENCY_KEY("WA-0011", "WA-0011"),
    INVALID_AMOUNT("WA-0012", "WA-0012"),
    BANK_LINK_OWNERSHIP_VIOLATION("WA-0017", "WA-0017"),
    TOP_UP_AMOUNT_TOO_LOW("WA-0018", "WA-0018"),
    TOP_UP_OWNERSHIP_VIOLATION("WA-0019", "WA-0019"),
    NOT_A_TOP_UP_ORDER("WA-0020", "WA-0020"),

    // Bank Gateway Failures
    BANK_INSUFFICIENT_FUNDS("WA-0013", "WA-0013"),
    BANK_ACCOUNT_BLOCKED("WA-0014", "WA-0014"),
    BANK_PROVIDER_UNAVAILABLE("WA-0015", "WA-0015"),
    BANK_PROVIDER_DECLINED("WA-0016", "WA-0016")
    ;

    private final String code;
    private final String messageKey;
}
