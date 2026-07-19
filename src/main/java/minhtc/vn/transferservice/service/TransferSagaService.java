package minhtc.vn.transferservice.service;

import java.util.UUID;

public interface TransferSagaService {

    void execute(UUID transferId);

    void reserveSourceWallet(UUID transferId);

    void creditTargetWallet(UUID transferId);

    void finalizeSourceReservation(UUID transferId);

    void compensateSourceReservation(
            UUID transferId,
            String reason
    );
}