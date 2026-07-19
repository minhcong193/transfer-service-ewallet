package minhtc.vn.transferservice.service;

public interface TransferRecoveryService {

    void recoverDueTransfers();

    void recoverOneTransfer(java.util.UUID transferId);
}