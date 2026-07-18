package minhtc.vn.transferservice.repository;

import minhtc.vn.transferservice.domain.TransferStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransferStatusHistoryRepository
        extends JpaRepository<TransferStatusHistory, UUID> {

    List<TransferStatusHistory>
    findAllByTransferIdOrderByCreatedAtAsc(UUID transferId);
}
