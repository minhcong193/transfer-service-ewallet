package minhtc.vn.transferservice.dto.response;

import minhtc.vn.transferservice.enums.IdempotencyDecision;

import java.util.UUID;

public record IdempotencyBeginResult(
        UUID commandId,
        IdempotencyDecision decision,
        UUID transferId,
        String responseJson,
        Integer responseStatus,
        String errorCode,
        String errorMessage
) {

    public static IdempotencyBeginResult proceed(
            UUID commandId
    ) {
        return new IdempotencyBeginResult(
                commandId,
                IdempotencyDecision.PROCEED,
                null,
                null,
                null,
                null,
                null
        );
    }

}