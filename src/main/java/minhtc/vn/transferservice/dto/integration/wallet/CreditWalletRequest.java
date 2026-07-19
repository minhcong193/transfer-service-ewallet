package minhtc.vn.transferservice.dto.integration.wallet;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditWalletRequest(

        @NotNull
        UUID commandId,

        @NotNull
        UUID transferId,

        @NotNull
        UUID sourceWalletId,

        @NotNull
        @DecimalMin(value = "0.0001")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency,

        @Size(max = 500)
        String description,

        UUID correlationId
) {
}
