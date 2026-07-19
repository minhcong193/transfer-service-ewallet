package minhtc.vn.transferservice.repository;

import jakarta.persistence.LockModeType;
import minhtc.vn.transferservice.domain.TransferLimitReservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransferLimitReservationRepository
        extends JpaRepository<TransferLimitReservation, UUID> {

    Optional<TransferLimitReservation> findByTransferId(
            UUID transferId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from TransferLimitReservation r
            where r.transferId = :transferId
            """)
    Optional<TransferLimitReservation> findByTransferIdForUpdate(
            @Param("transferId") UUID transferId
    );
}
