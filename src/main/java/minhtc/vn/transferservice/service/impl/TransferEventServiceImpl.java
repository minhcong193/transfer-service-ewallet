package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.integration.wallet.WalletCreditResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletFinalizeResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReleaseResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReservationResult;
import minhtc.vn.transferservice.enums.TransferEventType;
import minhtc.vn.transferservice.event.TransferEventEnvelope;
import minhtc.vn.transferservice.event.payload.TransferEventPayload;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.service.TransferEventService;
import minhtc.vn.transferservice.service.TransferOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferEventServiceImpl
        implements TransferEventService {

    private static final String AGGREGATE_TYPE =
            "TRANSFER";

    private final TransferRepository transferRepository;

    private final TransferOutboxService transferOutboxService;

    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTransferCreated(UUID transferId) {
        append(
                transferId,
                TransferEventType.TRANSFER_CREATED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendOtpPending(UUID transferId) {
        append(
                transferId,
                TransferEventType.TRANSFER_OTP_PENDING
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendOtpVerified(UUID transferId) {
        append(
                transferId,
                TransferEventType.TRANSFER_OTP_VERIFIED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendSourceReserved(
            UUID transferId,
            WalletReservationResult result
    ) {
        Transfer transfer =
                getTransfer(transferId);

        validateCommandId(
                transfer.getSourceReserveCommandId(),
                result.commandId(),
                "reserve"
        );

        append(
                transfer,
                TransferEventType.TRANSFER_SOURCE_RESERVED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTargetCredited(
            UUID transferId,
            WalletCreditResult result
    ) {
        Transfer transfer =
                getTransfer(transferId);

        validateCommandId(
                transfer.getTargetCreditCommandId(),
                result.commandId(),
                "credit"
        );

        append(
                transfer,
                TransferEventType.TRANSFER_TARGET_CREDITED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTransferCompleted(
            UUID transferId,
            WalletFinalizeResult result
    ) {
        Transfer transfer =
                getTransfer(transferId);

        validateCommandId(
                transfer.getSourceFinalizeCommandId(),
                result.commandId(),
                "finalize"
        );

        append(
                transfer,
                TransferEventType.TRANSFER_COMPLETED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTransferCompensated(
            UUID transferId,
            WalletReleaseResult result
    ) {
        Transfer transfer =
                getTransfer(transferId);

        validateCommandId(
                transfer.getSourceReleaseCommandId(),
                result.commandId(),
                "release"
        );

        append(
                transfer,
                TransferEventType.TRANSFER_COMPENSATED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTransferFailed(
            UUID transferId,
            String failureCode,
            String failureMessage
    ) {
        Transfer transfer =
                getTransfer(transferId);

        if (
                transfer.getFailureCode() == null
                        && failureCode != null
        ) {
            throw new IllegalStateException(
                    "Transfer failure data must be persisted "
                            + "before appending TRANSFER_FAILED event"
            );
        }

        append(
                transfer,
                TransferEventType.TRANSFER_FAILED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendManualReviewRequired(
            UUID transferId,
            String errorCode,
            String errorMessage
    ) {
        append(
                transferId,
                TransferEventType
                        .TRANSFER_MANUAL_REVIEW_REQUIRED
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTransferCancelled(
            UUID transferId
    ) {
        append(
                transferId,
                TransferEventType.TRANSFER_CANCELLED
        );
    }

    private void append(
            UUID transferId,
            TransferEventType eventType
    ) {
        Transfer transfer =
                getTransfer(transferId);

        append(
                transfer,
                eventType
        );
    }

    private void append(
            Transfer transfer,
            TransferEventType eventType
    ) {
        LocalDateTime occurredAt =
                LocalDateTime.now(clock);

        TransferEventPayload payload =
                mapPayload(
                        transfer,
                        occurredAt
                );

        TransferEventEnvelope<TransferEventPayload> event =
                new TransferEventEnvelope<>(
                        UUID.randomUUID(),
                        eventType,
                        transfer.getId(),
                        AGGREGATE_TYPE,
                        occurredAt,
                        transfer.getCorrelationId(),
                        payload
                );

        transferOutboxService.appendEvent(
                event.eventId(),
                event.aggregateId(),
                event.aggregateType(),
                event.eventType().name(),
                event.correlationId(),
                event
        );
    }

    private TransferEventPayload mapPayload(
            Transfer transfer,
            LocalDateTime occurredAt
    ) {
        return new TransferEventPayload(
                transfer.getId(),
                transfer.getTransferCode(),
                transfer.getSourceWalletId(),
                transfer.getTargetWalletId(),
                transfer.getSourceOwnerKeycloakUserId(),
                transfer.getTargetOwnerKeycloakUserId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getStatus(),
                transfer.getSourceReservationId(),
                transfer.getSourceReserveTransactionId(),
                transfer.getTargetCreditTransactionId(),
                transfer.getSourceFinalizeTransactionId(),
                transfer.getSourceReleaseTransactionId(),
                transfer.getFailureCode(),
                transfer.getFailureMessage(),
                occurredAt
        );
    }

    private Transfer getTransfer(UUID transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(
                        () -> new TransferNotFoundException(
                                transferId
                        )
                );
    }

    private void validateCommandId(
            UUID expected,
            UUID actual,
            String operation
    ) {
        if (
                actual == null
                        || !expected.equals(actual)
        ) {
            throw new IllegalStateException(
                    "Wallet "
                            + operation
                            + " commandId does not match Transfer commandId"
            );
        }
    }
}