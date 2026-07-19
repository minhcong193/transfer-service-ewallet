package minhtc.vn.transferservice.service;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.exception.TransferNotFoundException;
import minhtc.vn.transferservice.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class TransferSagaPersistenceService {

    private final TransferRepository transferRepository;

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(
                        () -> new TransferNotFoundException(
                                transferId
                        )
                );
    }

    /**
     * Cập nhật các dữ liệu nghiệp vụ không phải state transition:
     *
     * - reservationId;
     * - walletTransactionId;
     * - recoveryAttempts;
     * - nextRetryAt;
     * - failureCode;
     * - failureMessage.
     *
     * Không thay đổi Transfer.status trong method này.
     */
    @Transactional
    public void update(
            UUID transferId,
            Consumer<Transfer> updater
    ) {
        Transfer transfer = transferRepository
                .findByIdForUpdate(transferId)
                .orElseThrow(
                        () -> new TransferNotFoundException(
                                transferId
                        )
                );

        updater.accept(transfer);

        /*
         * Không bắt buộc gọi save vì entity đang managed.
         * Có thể giữ save để code rõ ràng.
         */
        transferRepository.save(transfer);
    }
}