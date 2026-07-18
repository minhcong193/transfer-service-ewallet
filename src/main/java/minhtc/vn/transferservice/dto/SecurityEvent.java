package minhtc.vn.transferservice.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SecurityEvent(
        String eventType,
        String username,
        String resource,
        String ip,
        LocalDateTime timestamp,
        String source,
        String correlationId
) {
}