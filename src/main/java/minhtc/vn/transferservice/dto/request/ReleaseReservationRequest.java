package minhtc.vn.transferservice.dto.request;

import java.util.UUID;

public record ReleaseReservationRequest(
        UUID commandId,
        String businessReference,
        String reason
) {
}
