package minhtc.vn.transferservice.dto.response;

import minhtc.vn.transferservice.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletCommandResult(

        UUID commandId,

        WalletCommandType commandType,

        WalletCommandStatus commandStatus,

        UUID walletId,

        UUID transferId,

        UUID counterpartyWalletId,

        UUID reservationId,

        UUID walletTransactionId,

        BigDecimal amount,

        String currency,

        WalletReservationStatus reservationStatus,

        WalletTransactionStatus transactionStatus,

        WalletFailureCode failureCode,

        String failureMessage,

        LocalDateTime createdAt,

        LocalDateTime completedAt
) {
}
