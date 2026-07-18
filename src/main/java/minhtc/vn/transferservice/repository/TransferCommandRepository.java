package minhtc.vn.transferservice.repository;

import minhtc.vn.transferservice.domain.TransferCommand;
import minhtc.vn.transferservice.enums.TransferCommandType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferCommandRepository
        extends JpaRepository<TransferCommand, UUID> {

    Optional<TransferCommand>
    findByTransferIdAndCommandTypeAndIdempotencyKey(
            UUID transferId,
            TransferCommandType commandType,
            String idempotencyKey
    );
}
