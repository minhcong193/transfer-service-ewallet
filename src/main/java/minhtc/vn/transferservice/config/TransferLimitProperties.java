package minhtc.vn.transferservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ConfigurationProperties(prefix = "app.transfer.limits")
public record TransferLimitProperties(
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal dailyOutgoingLimit
) {

    public TransferLimitProperties {
        minAmount = normalize(
                minAmount,
                new BigDecimal("1000.0000")
        );

        maxAmount = normalize(
                maxAmount,
                new BigDecimal("20000000.0000")
        );

        dailyOutgoingLimit = normalize(
                dailyOutgoingLimit,
                new BigDecimal("50000000.0000")
        );

        if (minAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "minAmount must be greater than zero"
            );
        }

        if (maxAmount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException(
                    "maxAmount must be greater than minAmount"
            );
        }

        if (dailyOutgoingLimit.compareTo(maxAmount) < 0) {
            throw new IllegalArgumentException(
                    "dailyOutgoingLimit must not be lower than maxAmount"
            );
        }
    }

    private static BigDecimal normalize(
            BigDecimal value,
            BigDecimal defaultValue
    ) {
        BigDecimal actual = value != null
                ? value
                : defaultValue;

        return actual.setScale(
                4,
                RoundingMode.UNNECESSARY
        );
    }
}