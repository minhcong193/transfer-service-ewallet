package minhtc.vn.transferservice.dto.request;

import jakarta.validation.constraints.Size;

public record CancelTransferRequest(
        @Size(max = 255)
        String reason
) {
}
