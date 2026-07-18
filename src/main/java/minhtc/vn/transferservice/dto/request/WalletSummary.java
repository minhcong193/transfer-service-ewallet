package minhtc.vn.transferservice.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletSummary(
        UUID id,
        UUID ownerKeycloakUserId,
        String ownerDisplayName,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        String currency,
        String status
) {
}
