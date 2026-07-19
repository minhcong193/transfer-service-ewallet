package minhtc.vn.transferservice.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.transfer.otp")
public record TransferOtpProperties(
        Duration ttl,
        Duration recordTtl,
        Duration resendCooldown,
        int maxAttempts,
        int maxResends,
        int maxDailyOtp,
        String hmacSecret
) {

    public TransferOtpProperties {
        ttl = ttl != null
                ? ttl
                : Duration.ofMinutes(5);

        recordTtl = recordTtl != null
                ? recordTtl
                : Duration.ofHours(24);

        resendCooldown = resendCooldown != null
                ? resendCooldown
                : Duration.ofSeconds(60);

        maxAttempts = maxAttempts > 0
                ? maxAttempts
                : 5;

        maxResends = maxResends > 0
                ? maxResends
                : 3;

        maxDailyOtp = maxDailyOtp > 0
                ? maxDailyOtp
                : 10;

        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "app.transfer.otp.hmac-secret is required"
            );
        }
    }
}
