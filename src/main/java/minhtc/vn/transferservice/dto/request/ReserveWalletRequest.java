package minhtc.vn.transferservice.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record ReserveWalletRequest(
        UUID commandId,
        String businessReference,
        String ownerKeycloakUserId,
        BigDecimal amount,
        String currency,
        String reason
) {
}
