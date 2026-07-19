package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.domain.TransferStatusHistory;
import minhtc.vn.transferservice.enums.TransferStatus;
import minhtc.vn.transferservice.exception.InvalidTransferStateException;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.repository.TransferStatusHistoryRepository;
import minhtc.vn.transferservice.service.TransferStateMachineService;
import minhtc.vn.transferservice.dto.TransferTransitionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service quản lý vòng đời và sự thay đổi trạng thái (State Machine) của một giao dịch.
 * Đảm bảo các luồng (Happy Path, Rollback Path) chạy đúng thứ tự và an toàn trong môi trường đồng thời (Concurrency).
 */
@Service
@RequiredArgsConstructor
public class TransferStateMachineServiceImpl
        implements TransferStateMachineService {

    private static final Map<TransferStatus, Set<TransferStatus>>
            ALLOWED_TRANSITIONS = buildAllowedTransitions();

    private final TransferRepository transferRepository;

    private final TransferStatusHistoryRepository
            transferStatusHistoryRepository;

    private final Clock clock;

    @Override
    @Transactional
    public Transfer transition(
            UUID transferId,
            TransferStatus targetStatus,
            TransferTransitionContext context
    ) {
        Transfer transfer = transferRepository
                .findByIdForUpdate(transferId)
                .orElseThrow(
                        () -> new TransferNotFoundException(
                                transferId
                        )
                );

        return transitionManaged(
                transfer,
                targetStatus,
                context
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Transfer transitionManaged(
            Transfer transfer,
            TransferStatus targetStatus,
            TransferTransitionContext context
    ) {
        if (transfer == null) {
            throw new IllegalArgumentException(
                    "transfer is required"
            );
        }

        if (targetStatus == null) {
            throw new IllegalArgumentException(
                    "targetStatus is required"
            );
        }

        TransferStatus fromStatus =
                transfer.getStatus();

        /*
         * Idempotent transition.
         *
         * Nếu hiện tại đã ở targetStatus thì không ghi thêm history.
         * Điều này tránh trùng history khi retry/recovery gọi lại cùng bước.
         */
        if (fromStatus == targetStatus) {
            return transfer;
        }

        validateTransition(
                transfer.getId(),
                fromStatus,
                targetStatus
        );

        LocalDateTime changedAt =
                LocalDateTime.now(clock);

        transfer.changeStatus(
                targetStatus,
                changedAt
        );

        TransferTransitionContext safeContext =
                context != null
                        ? context
                        : TransferTransitionContext.saga(
                        "Transfer status changed",
                        transfer.getCorrelationId()
                );

        UUID correlationId =
                safeContext.correlationId() != null
                        ? safeContext.correlationId()
                        : transfer.getCorrelationId();

        TransferStatusHistory history =
                TransferStatusHistory.create(
                        transfer.getId(),
                        fromStatus,
                        targetStatus,
                        truncate(
                                safeContext.reasonCode(),
                                100
                        ),
                        truncate(
                                safeContext.reasonMessage(),
                                500
                        ),
                        correlationId
                );

        transferStatusHistoryRepository.save(history);

        return transfer;
    }

    @Override
    public boolean canTransition(
            TransferStatus fromStatus,
            TransferStatus toStatus
    ) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }

        if (fromStatus == toStatus) {
            return true;
        }

        return ALLOWED_TRANSITIONS
                .getOrDefault(
                        fromStatus,
                        Set.of()
                )
                .contains(toStatus);
    }

    @Override
    public void validateTransition(
            UUID transferId,
            TransferStatus fromStatus,
            TransferStatus toStatus
    ) {
        if (!canTransition(fromStatus, toStatus)) {
            throw new InvalidTransferStateException(
                    transferId,
                    fromStatus,
                    "Transition from "
                            + fromStatus
                            + " to "
                            + toStatus
                            + " is not allowed"
            );
        }
    }

    private static Map<TransferStatus, Set<TransferStatus>>
    buildAllowedTransitions() {
        Map<TransferStatus, Set<TransferStatus>> transitions =
                new EnumMap<>(TransferStatus.class);

        transitions.put(
                TransferStatus.CREATED,
                EnumSet.of(
                        TransferStatus.OTP_PENDING,
                        TransferStatus.CANCELLED,
                        TransferStatus.FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.OTP_PENDING,
                EnumSet.of(
                        TransferStatus.OTP_VERIFIED,
                        TransferStatus.OTP_FAILED,
                        TransferStatus.OTP_EXPIRED,
                        TransferStatus.CANCELLED,
                        TransferStatus.FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.OTP_FAILED,
                EnumSet.of(
                        TransferStatus.OTP_PENDING,
                        TransferStatus.OTP_VERIFIED,
                        TransferStatus.OTP_EXPIRED,
                        TransferStatus.CANCELLED,
                        TransferStatus.FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.OTP_EXPIRED,
                EnumSet.of(
                        TransferStatus.OTP_PENDING,
                        TransferStatus.CANCELLED,
                        TransferStatus.FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.OTP_VERIFIED,
                EnumSet.of(
                        TransferStatus.SOURCE_RESERVE_PENDING,
                        TransferStatus.FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.SOURCE_RESERVE_PENDING,
                EnumSet.of(
                        TransferStatus.SOURCE_RESERVED,
                        TransferStatus.SOURCE_RESERVE_FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.SOURCE_RESERVE_FAILED,
                EnumSet.of(
                        TransferStatus.FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.SOURCE_RESERVED,
                EnumSet.of(
                        TransferStatus.TARGET_CREDIT_PENDING,
                        TransferStatus.COMPENSATION_PENDING,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.TARGET_CREDIT_PENDING,
                EnumSet.of(
                        TransferStatus.TARGET_CREDITED,
                        TransferStatus.TARGET_CREDIT_FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.TARGET_CREDIT_FAILED,
                EnumSet.of(
                        TransferStatus.COMPENSATION_PENDING,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.TARGET_CREDITED,
                EnumSet.of(
                        TransferStatus.SOURCE_FINALIZE_PENDING,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.SOURCE_FINALIZE_PENDING,
                EnumSet.of(
                        TransferStatus.SOURCE_FINALIZED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.SOURCE_FINALIZED,
                EnumSet.of(
                        TransferStatus.COMPLETED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.COMPENSATION_PENDING,
                EnumSet.of(
                        TransferStatus.COMPENSATING,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.COMPENSATING,
                EnumSet.of(
                        TransferStatus.COMPENSATED,
                        TransferStatus.COMPENSATION_FAILED,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        transitions.put(
                TransferStatus.COMPENSATION_FAILED,
                EnumSet.of(
                        TransferStatus.COMPENSATING,
                        TransferStatus.MANUAL_REVIEW
                )
        );

        /*
         * Terminal statuses không có transition tiếp.
         */
        transitions.put(
                TransferStatus.COMPLETED,
                Set.of()
        );

        transitions.put(
                TransferStatus.FAILED,
                Set.of()
        );

        transitions.put(
                TransferStatus.COMPENSATED,
                Set.of()
        );

        transitions.put(
                TransferStatus.CANCELLED,
                Set.of()
        );

        transitions.put(
                TransferStatus.MANUAL_REVIEW,
                Set.of()
        );

        return Map.copyOf(transitions);
    }

    private static String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}