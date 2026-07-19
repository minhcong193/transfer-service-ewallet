package minhtc.vn.transferservice.repository.specification;

import jakarta.persistence.criteria.Predicate;
import minhtc.vn.transferservice.dto.transfer.TransferSearchCriteria;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.enums.TransferDirection;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransferSpecification {

    private TransferSpecification() {
    }

    public static Specification<Transfer> forUser(
            UUID userId,
            TransferSearchCriteria criteria
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates =
                    new ArrayList<>();

            TransferDirection direction =
                    criteria.resolvedDirection();

            switch (direction) {
                case OUT -> predicates.add(
                        builder.equal(
                                root.get(
                                        "sourceOwnerKeycloakUserId"
                                ),
                                userId
                        )
                );

                case IN -> predicates.add(
                        builder.equal(
                                root.get(
                                        "targetOwnerKeycloakUserId"
                                ),
                                userId
                        )
                );

                case ALL -> predicates.add(
                        builder.or(
                                builder.equal(
                                        root.get(
                                                "sourceOwnerKeycloakUserId"
                                        ),
                                        userId
                                ),
                                builder.equal(
                                        root.get(
                                                "targetOwnerKeycloakUserId"
                                        ),
                                        userId
                                )
                        )
                );
            }

            if (criteria.status() != null) {
                predicates.add(
                        builder.equal(
                                root.get("status"),
                                criteria.status()
                        )
                );
            }

            if (criteria.fromDate() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                criteria.fromDate()
                        )
                );
            }

            if (criteria.toDate() != null) {
                predicates.add(
                        builder.lessThanOrEqualTo(
                                root.get("createdAt"),
                                criteria.toDate()
                        )
                );
            }

            if (criteria.minAmount() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(
                                root.get("amount"),
                                criteria.minAmount()
                        )
                );
            }

            if (criteria.maxAmount() != null) {
                predicates.add(
                        builder.lessThanOrEqualTo(
                                root.get("amount"),
                                criteria.maxAmount()
                        )
                );
            }

            if (
                    criteria.transferCode() != null
                            && !criteria.transferCode().isBlank()
            ) {
                predicates.add(
                        builder.like(
                                builder.lower(
                                        root.get("transferCode")
                                ),
                                "%"
                                        + criteria.transferCode()
                                        .trim()
                                        .toLowerCase()
                                        + "%"
                        )
                );
            }

            return builder.and(
                    predicates.toArray(Predicate[]::new)
            );
        };
    }
}