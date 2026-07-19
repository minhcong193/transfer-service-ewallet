package minhtc.vn.transferservice.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.clients.wallet")
public record WalletClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public WalletClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Wallet Service base URL must be configured"
            );
        }

        connectTimeout = connectTimeout == null
                ? Duration.ofSeconds(3)
                : connectTimeout;

        readTimeout = readTimeout == null
                ? Duration.ofSeconds(10)
                : readTimeout;
    }
}
