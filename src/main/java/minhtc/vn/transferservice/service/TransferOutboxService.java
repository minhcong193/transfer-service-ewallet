package minhtc.vn.transferservice.service;

import java.util.UUID;

public interface TransferOutboxService {

    void appendEvent(
            UUID eventId,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            UUID correlationId,
            Object payload
    );
}