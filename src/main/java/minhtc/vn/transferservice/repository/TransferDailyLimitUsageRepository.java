package minhtc.vn.transferservice.repository;

import jakarta.persistence.LockModeType;
import minhtc.vn.transferservice.domain.TransferDailyLimitUsage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TransferDailyLimitUsageRepository
        extends JpaRepository<TransferDailyLimitUsage, UUID> {

    @Modifying
    @Query(
            value = """
                    insert into transfer_daily_limit_usage (
                        id,
                        owner_keycloak_user_id,
                        usage_date,
                        reserved_amount,
                        completed_amount,
                        created_at,
                        updated_at,
                        version
                    )
                    values (
                        :id,
                        :ownerId,
                        :usageDate,
                        0.0000,
                        0.0000,
                        :now,
                        :now,
                        0
                    )
                    on conflict (
                        owner_keycloak_user_id,
                        usage_date
                    )
                    do nothing
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId,
            @Param("usageDate") LocalDate usageDate,
            @Param("now") LocalDateTime now
    );

    Optional<TransferDailyLimitUsage>
    findByOwnerKeycloakUserIdAndUsageDate(
            UUID ownerKeycloakUserId,
            LocalDate usageDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d
            from TransferDailyLimitUsage d
            where d.ownerKeycloakUserId = :ownerId
              and d.usageDate = :usageDate
            """)
    Optional<TransferDailyLimitUsage> findForUpdate(
            @Param("ownerId") UUID ownerId,
            @Param("usageDate") LocalDate usageDate
    );
}
