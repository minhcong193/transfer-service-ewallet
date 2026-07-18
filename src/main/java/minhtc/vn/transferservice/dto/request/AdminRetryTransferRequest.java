package minhtc.vn.transferservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminRetryTransferRequest(
        @NotBlank
        String reason
) {
}
