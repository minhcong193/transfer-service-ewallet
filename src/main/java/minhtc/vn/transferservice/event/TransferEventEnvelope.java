package minhtc.vn.transferservice.event;

import minhtc.vn.transferservice.enums.TransferEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record TransferEventEnvelope<T>(

        UUID eventId,

        TransferEventType eventType,

        UUID aggregateId,

        String aggregateType,

        LocalDateTime occurredAt,

        UUID correlationId,

        T payload
) {
}