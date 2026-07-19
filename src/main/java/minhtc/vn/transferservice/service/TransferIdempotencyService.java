package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.response.IdempotencyBeginResult;
import minhtc.vn.transferservice.enums.TransferCommandType;

import java.util.UUID;

public interface TransferIdempotencyService {

    IdempotencyBeginResult begin(
            UUID ownerKeycloakUserId,
            UUID transferId,
            TransferCommandType commandType,
            UUID idempotencyKey,
            String requestHash
    );

    void completeSuccess(
            UUID commandId,
            UUID transferId,
            Object response,
            int responseStatus
    );

    void completeFailure(
            UUID commandId,
            String errorCode,
            String errorMessage,
            int responseStatus
    );

    <T> T readResponse(
            String responseJson,
            Class<T> responseType
    );
}
