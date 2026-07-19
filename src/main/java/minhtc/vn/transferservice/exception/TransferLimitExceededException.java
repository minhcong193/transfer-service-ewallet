package minhtc.vn.transferservice.exception;

import java.math.BigDecimal;

public class TransferLimitExceededException
        extends RuntimeException {

    public TransferLimitExceededException(
            BigDecimal dailyLimit,
            BigDecimal currentUsage,
            BigDecimal requestedAmount
    ) {
        super(
                """
                Transfer daily limit exceeded. \
                limit=%s, currentUsage=%s, requestedAmount=%s
                """.formatted(
                        dailyLimit,
                        currentUsage,
                        requestedAmount
                )
        );
    }
}
