package minhtc.vn.transferservice.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceWalletId,
        @NotNull UUID targetWalletId,

        @NotNull
        @DecimalMin("1000")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "VND")
        String currency,

        @Size(max = 255)
        String description
) {
}
