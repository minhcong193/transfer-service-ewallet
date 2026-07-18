package minhtc.vn.transferservice.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.dto.SecurityEvent;
import minhtc.vn.transferservice.enums.SecurityEventType;
import minhtc.vn.transferservice.util.RequestContextProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityEventPublisher {

    private static final String SOURCE = "user-service";

    private final RequestContextProvider contextProvider;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.security-events:security-events}")
    private String securityEventsTopic;

    public void publishOwnershipViolation(
            String username
    ) {
        SecurityEvent event = SecurityEvent.builder()
                .eventType(SecurityEventType.OWNERSHIP_VIOLATION.name())
                .username(username)
                .resource(contextProvider.getRequestPath())
                .source(SOURCE)
                .timestamp(LocalDateTime.now())
                .ip(contextProvider.getClientIp())
                .correlationId(contextProvider.getCorrelationId())
                .build();

        kafkaTemplate.send(
                        securityEventsTopic,
                        username,
                        event
                )
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error(
                                "Failed to publish ownership violation event",
                                error
                        );
                    }
                });
    }
}
