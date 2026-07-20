package minhtc.vn.transferservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.otp")
public record TransferOtpProperties(
        Duration ttl,
        int maxAttempts,
        Duration resendCooldown,
        int maxResend,
        int maxOtpPerUserPerDay,
        String hashSecret
) {
}