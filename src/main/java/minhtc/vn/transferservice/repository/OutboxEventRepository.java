package minhtc.vn.transferservice.repository;

import minhtc.vn.transferservice.domain.OutboxEvent;
import minhtc.vn.transferservice.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusAndNextRetryAtBeforeOrderByCreatedAt(
            OutboxStatus status,
            LocalDateTime now
    );

    boolean existsByEventId(UUID eventId);
}
