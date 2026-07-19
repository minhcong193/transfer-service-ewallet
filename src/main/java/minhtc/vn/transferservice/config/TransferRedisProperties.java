package minhtc.vn.transferservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Các cấu hình Redis dùng riêng cho Transfer Service.
 *
 * @param keyPrefix prefix chung của toàn bộ Redis key
 * @param commandTimeout thời gian tối đa chờ một Redis command
 */
@ConfigurationProperties(prefix = "app.transfer.redis")
public record TransferRedisProperties(
        String keyPrefix,
        Duration commandTimeout
) {

    public TransferRedisProperties {
        keyPrefix =
                keyPrefix != null && !keyPrefix.isBlank()
                        ? removeTrailingColon(keyPrefix.trim())
                        : "transfer-service";

        commandTimeout =
                commandTimeout != null
                        ? commandTimeout
                        : Duration.ofSeconds(3);
    }

    private static String removeTrailingColon(
            String value
    ) {
        String result = value;

        while (result.endsWith(":")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }
}