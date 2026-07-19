package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.dto.transfer.*;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.mapper.TransferMapper;
import minhtc.vn.transferservice.repository.TransferRepository;
import minhtc.vn.transferservice.repository.TransferStatusHistoryRepository;
import minhtc.vn.transferservice.repository.specification.TransferSpecification;
import minhtc.vn.transferservice.service.TransferOwnershipService;
import minhtc.vn.transferservice.service.TransferQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferQueryServiceImpl
        implements TransferQueryService {

    private final TransferRepository transferRepository;
    private final TransferStatusHistoryRepository
            historyRepository;

    private final TransferOwnershipService ownershipService;
    private final TransferMapper transferMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<TransferSummaryResponse> getMyTransfers(
            Jwt jwt,
            TransferSearchCriteria criteria,
            Pageable pageable
    ) {
        UUID currentUserId =
                UUID.fromString(jwt.getSubject());

        return transferRepository.findAll(
                TransferSpecification.forUser(
                        currentUserId,
                        criteria
                ),
                pageable
        ).map(transfer ->
                transferMapper.toSummary(
                        transfer,
                        currentUserId
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse getMyTransferById(
            Jwt jwt,
            UUID transferId
    ) {
        UUID currentUserId =
                UUID.fromString(jwt.getSubject());

        Transfer transfer = getTransfer(transferId);

        ownershipService.assertCanViewTransfer(
                currentUserId,
                transfer,
                transfer.getCorrelationId()
        );

        return transferMapper.toResponse(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferStatusHistoryResponse>
    getStatusHistory(
            Jwt jwt,
            UUID transferId
    ) {
        UUID currentUserId =
                UUID.fromString(jwt.getSubject());

        Transfer transfer = getTransfer(transferId);

        ownershipService.assertCanViewTransfer(
                currentUserId,
                transfer,
                transfer.getCorrelationId()
        );

        return historyRepository
                .findAllByTransferIdOrderByCreatedAtAsc(
                        transferId
                )
                .stream()
                .map(transferMapper::toHistory)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse getInternalTransfer(
            UUID transferId
    ) {
        return transferMapper.toResponse(
                getTransfer(transferId)
        );
    }

    private Transfer getTransfer(UUID transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(() ->
                        new TransferNotFoundException(
                                transferId
                        )
                );
    }
}