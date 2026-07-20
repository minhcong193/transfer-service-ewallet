package minhtc.vn.transferservice.scheduler;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.service.TransferRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferRecoveryScheduler {

    private final TransferRecoveryService transferRecoveryService;

    @Scheduled(
            fixedDelayString =
                    "${app.transfer.recovery.scheduler-delay:3000000}"
    )
    public void recoverDueTransfers() {
        transferRecoveryService.recoverDueTransfers();
    }
}