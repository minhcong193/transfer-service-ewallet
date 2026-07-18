package minhtc.vn.transferservice.otp;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class OtpKeyFactory {

    public String otpKey(UUID transferId) {
        return "otp:transfer:" + transferId;
    }

    public String cooldownKey(String userId) {
        return "otp:cooldown:" + userId;
    }

    public String dailyCountKey(String userId, LocalDate date) {
        return "otp:daily-count:" + userId + ":" + date;
    }

    public String verifyLockKey(UUID transferId) {
        return "otp:verify-lock:" + transferId;
    }
}
