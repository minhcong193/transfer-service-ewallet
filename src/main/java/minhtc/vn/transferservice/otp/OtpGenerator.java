package minhtc.vn.transferservice.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return "%06d".formatted(
                secureRandom.nextInt(1_000_000)
        );
    }
}
