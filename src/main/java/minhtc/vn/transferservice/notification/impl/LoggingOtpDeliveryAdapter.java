package minhtc.vn.transferservice.notification.impl;

import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.notification.OtpDeliveryPort;
import minhtc.vn.transferservice.otp.TransferOtpDeliveryCommand;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("none")
public class LoggingOtpDeliveryAdapter
        implements OtpDeliveryPort {

    @Override
    public void sendTransferOtp(
            TransferOtpDeliveryCommand command
    ) {
        log.warn(
                "[LOCAL-ONLY] Transfer OTP: "
                        + "ownerUserId={}, transferId={}, "
                        + "email={}, otp={}, expiresAt={}",
                command.ownerKeycloakUserId(),
                command.transferId(),
                command.recipientEmail(),
                command.rawOtp(),
                command.expiresAt()
        );
    }
}