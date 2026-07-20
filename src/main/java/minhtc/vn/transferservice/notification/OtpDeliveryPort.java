package minhtc.vn.transferservice.notification;

import minhtc.vn.transferservice.otp.TransferOtpDeliveryCommand;

import java.util.UUID;

public interface OtpDeliveryPort {

    void sendTransferOtp(
            TransferOtpDeliveryCommand command
    );
}
