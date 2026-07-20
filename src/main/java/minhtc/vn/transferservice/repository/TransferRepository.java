package minhtc.vn.transferservice.repository;

import jakarta.persistence.LockModeType;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
            from transfers
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

    /**
     * Pessimistic write lock bảo đảm hai luồng không đồng thời
     * chuyển trạng thái của cùng một Transfer.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from Transfer t
            where t.id = :transferId
            """)
    Optional<Transfer> findByIdForUpdate(
            @Param("transferId") UUID transferId
    );


    Optional<Transfer> findBySourceOwnerKeycloakUserIdAndRequestIdempotencyKey(
            UUID sourceOwnerKeycloakUserId,
            UUID requestIdempotencyKey
    );

    @Query("""
        select coalesce(sum(t.amount), 0)
        from Transfer t
        where t.sourceOwnerKeycloakUserId = :ownerKeycloakUserId
          and t.currency = :currency
          and t.status in :statuses
          and t.createdAt >= :startOfDay
          and t.createdAt < :endOfDay
        """)
    BigDecimal sumAmountForDailyLimit(
            @Param("ownerKeycloakUserId")
            UUID ownerKeycloakUserId,

            @Param("currency")
            String currency,

            @Param("statuses")
            List<TransferStatus> statuses,

            @Param("startOfDay")
            LocalDateTime startOfDay,

            @Param("endOfDay")
            LocalDateTime endOfDay
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from Transfer t
            where t.status in :statuses
              and (
                    t.nextRetryAt is null
                    or t.nextRetryAt <= :now
                  )
            order by t.updatedAt asc
            """)
    List<Transfer> findDueForRecovery(
            @Param("statuses") List<TransferStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
