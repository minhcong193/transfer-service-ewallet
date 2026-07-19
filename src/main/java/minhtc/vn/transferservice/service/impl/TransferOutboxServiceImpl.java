package minhtc.vn.transferservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.domain.OutboxEvent;
import minhtc.vn.transferservice.enums.OutboxStatus;
import minhtc.vn.transferservice.repository.OutboxEventRepository;

import minhtc.vn.transferservice.service.TransferOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferOutboxServiceImpl
        implements TransferOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendEvent(
            UUID eventId,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            UUID correlationId,
            Object payload
    ) {
        if (
                outboxEventRepository.existsByEventId(
                        eventId
                )
        ) {
            return;
        }

        OutboxEvent event =
                OutboxEvent.create(
                        eventId,
                        aggregateType,
                        aggregateId,
                        eventType,
                        serialize(payload),
                        correlationId
                );

        outboxEventRepository.save(event);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(
                    payload
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to serialize outbox payload",
                    exception
            );
        }
    }
}