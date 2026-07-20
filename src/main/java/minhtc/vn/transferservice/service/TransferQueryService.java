package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.transfer.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

public interface TransferQueryService {

    Page<TransferSummaryResponse> getMyTransfers(
            Jwt jwt,
            TransferSearchCriteria criteria,
            Pageable pageable
    );

    TransferResponse getMyTransferById(
            Jwt jwt,
            UUID transferId
    );

    List<TransferStatusHistoryResponse> getStatusHistory(
            Jwt jwt,
            UUID transferId
    );

    TransferResponse getInternalTransfer(UUID transferId);


}