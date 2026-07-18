package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.response.TransferResponse;
import minhtc.vn.transferservice.dto.response.TransferSummaryResponse;
import minhtc.vn.transferservice.enums.TransferDirection;
import minhtc.vn.transferservice.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransferQueryService {

    TransferResponse getTransfer(UUID transferId);

    Page<TransferSummaryResponse> getMyTransfers(
            TransferDirection direction,
            TransferStatus status,
            Pageable pageable
    );

    Page<TransferResponse> searchTransfers(
            TransferSearchCriteria criteria,
            Pageable pageable
    );
}
