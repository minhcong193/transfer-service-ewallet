package minhtc.vn.transferservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmTransferRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{6}$")
        String otp
) {
}
