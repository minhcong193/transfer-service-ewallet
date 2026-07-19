package minhtc.vn.transferservice.notification.impl;

import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.notification.OtpDeliveryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("local")
public class LoggingOtpDeliveryAdapter
        implements OtpDeliveryPort {

    @Override
    public void sendTransferOtp(
            UUID ownerKeycloakUserId,
            UUID transferId,
            String otp
    ) {
        /*
         * Chỉ dùng local.
         * Tuyệt đối không log OTP trong production.
         */
        log.info(
                "[LOCAL_OTP] transferId={} otp={}",
                transferId,
                otp
        );
    }
}