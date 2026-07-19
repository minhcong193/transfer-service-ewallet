package minhtc.vn.transferservice.notification;

import java.util.UUID;

public interface OtpDeliveryPort {

    void sendTransferOtp(
            UUID ownerKeycloakUserId,
            UUID transferId,
            String otp
    );
}
