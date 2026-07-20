package minhtc.vn.transferservice.dto.integration.user;

import java.util.UUID;

public record UserNotificationProfileResponse(
        UUID keycloakUserId,
        String email,
        String fullName,
        boolean active
) {
}