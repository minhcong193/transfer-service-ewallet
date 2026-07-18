package minhtc.vn.transferservice.otp;

import java.util.UUID;

public interface OtpHasher {

    String hash(UUID transferId, String otp);

    boolean matches(
            UUID transferId,
            String rawOtp,
            String storedHash
    );

}
