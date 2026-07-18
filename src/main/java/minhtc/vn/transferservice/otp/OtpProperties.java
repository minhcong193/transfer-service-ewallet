package minhtc.vn.transferservice.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

import java.time.Duration;


@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        @DefaultValue("5m") Duration ttl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("60s") Duration resendCooldown,
        @DefaultValue("3") int maxResends,
        @DefaultValue("20") int dailyLimit,
        String hmacSecret // Không gán @DefaultValue thì ngầm định là null
) {
    // Không cần viết Compact Constructor nữa!
}
