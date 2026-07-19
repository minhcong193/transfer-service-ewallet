package minhtc.vn.transferservice.otp.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.otp.OtpHasher;
import minhtc.vn.transferservice.otp.TransferOtpProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HmacSha256OtpHasher implements OtpHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final TransferOtpProperties otpProperties;

    private SecretKeySpec secretKey;

    @PostConstruct
    void initialize() {
        String secret = otpProperties.hmacSecret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "OTP HMAC secret must be configured"
            );
        }

        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "OTP HMAC secret must contain at least 32 characters"
            );
        }

        this.secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    @Override
    public String hash(UUID transferId, String otp) {
        if (transferId == null) {
            throw new IllegalArgumentException(
                    "transferId must not be null"
            );
        }

        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "OTP must contain exactly 6 digits"
            );
        }

        String value = transferId + ":" + otp;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);

            byte[] result = mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(result);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(
                    "Unable to calculate OTP HMAC",
                    exception
            );
        }
    }

    @Override
    public boolean matches(
            UUID transferId,
            String rawOtp,
            String storedHash
    ) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }

        String calculatedHash = hash(transferId, rawOtp);

        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
