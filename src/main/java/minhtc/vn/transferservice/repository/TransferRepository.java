package minhtc.vn.transferservice.repository;

import minhtc.vn.transferservice.domain.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository
        extends JpaRepository<Transfer, UUID>,
        JpaSpecificationExecutor<Transfer> {

    Optional<Transfer> findByIdAndSourceOwnerKeycloakUserId(
            UUID transferId,
            String ownerId
    );

    Optional<Transfer>
    findBySourceOwnerKeycloakUserIdAndCreateIdempotencyKey(
            String ownerId,
            String idempotencyKey
    );

    boolean existsByTransferCode(String transferCode);

    @Query("""
        select t from Transfer t
        where t.sourceOwnerKeycloakUserId = :ownerId
           or t.targetOwnerKeycloakUserId = :ownerId
    """)
    Page<Transfer> findUserTransfers(
            String ownerId,
            Pageable pageable
    );

    @Query(
            value = """
            select *
            from transfer
            where status in (:statuses)
              and next_retry_at <= now()
            order by next_retry_at asc, created_at asc
            limit :limit
            for update skip locked
        """,
            nativeQuery = true
    )
    List<Transfer> findRecoverableTransfersForUpdate(
            @Param("statuses") Collection<String> statuses,
            @Param("limit") int limit
    );
}
