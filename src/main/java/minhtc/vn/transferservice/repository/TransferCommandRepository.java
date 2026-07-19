package minhtc.vn.transferservice.repository;

import jakarta.persistence.LockModeType;
import minhtc.vn.transferservice.domain.TransferCommand;
import minhtc.vn.transferservice.enums.TransferCommandType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TransferCommandRepository
        extends JpaRepository<TransferCommand, UUID> {

    @Modifying
    @Query(
            value = """
                    insert into transfer_commands (
                        id,
                        owner_keycloak_user_id,
                        transfer_id,
                        command_type,
                        idempotency_key,
                        request_hash,
                        status,
                        created_at,
                        updated_at,
                        version
                    )
                    values (
                        :id,
                        :ownerId,
                        :transferId,
                        :commandType,
                        :idempotencyKey,
                        :requestHash,
                        'IN_PROGRESS',
                        :createdAt,
                        :createdAt,
                        0
                    )
                    on conflict (
                        owner_keycloak_user_id,
                        command_type,
                        idempotency_key
                    )
                    do nothing
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId,
            @Param("transferId") UUID transferId,
            @Param("commandType") String commandType,
            @Param("idempotencyKey") UUID idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c
        from TransferCommand c
        where c.ownerKeycloakUserId = :ownerId
          and c.commandType = :commandType
          and c.idempotencyKey = :idempotencyKey
        """)
    Optional<TransferCommand> findByBusinessKeyForUpdate(
            @Param("ownerId") UUID ownerId,
            @Param("commandType") TransferCommandType commandType,
            @Param("idempotencyKey") UUID idempotencyKey
    );

    /*
     * Dùng query method thay vì JPQL valueOf.
     * Method này không lock, phù hợp cho read/replay đơn giản.
     */
    Optional<TransferCommand>
    findByOwnerKeycloakUserIdAndCommandTypeAndIdempotencyKey(
            UUID ownerKeycloakUserId,
            minhtc.vn.transferservice.enums.TransferCommandType commandType,
            UUID idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from TransferCommand c
            where c.id = :commandId
            """)
    Optional<TransferCommand> findByIdForUpdate(
            @Param("commandId") UUID commandId
    );
}
