package minhtc.vn.transferservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.transfer.recovery")
public record TransferRecoveryProperties(

        int batchSize,

        Duration retryDelay,

        Duration processingLease,

        int maxRecoveryAttempts,

        int maxCompensationAttempts
) {

    public TransferRecoveryProperties {
        batchSize = batchSize > 0 ? batchSize : 20;

        retryDelay =
                retryDelay != null
                        ? retryDelay
                        : Duration.ofSeconds(30);

        processingLease =
                processingLease != null
                        ? processingLease
                        : Duration.ofMinutes(2);

        maxRecoveryAttempts =
                maxRecoveryAttempts > 0
                        ? maxRecoveryAttempts
                        : 10;

        maxCompensationAttempts =
                maxCompensationAttempts > 0
                        ? maxCompensationAttempts
                        : 20;
    }
}