package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.domain.TransferCommand;
import minhtc.vn.transferservice.enums.*;
import minhtc.vn.transferservice.exception.IdempotencyConflictException;
import minhtc.vn.transferservice.repository.TransferCommandRepository;
import minhtc.vn.transferservice.service.TransferIdempotencyService;
import minhtc.vn.transferservice.dto.response.IdempotencyBeginResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service cốt lõi xử lý tính Lũy đẳng (Idempotency) cho toàn bộ hệ thống Transfer.
 * Đảm bảo mỗi request từ Client (nhận diện qua Idempotency-Key) chỉ được xử lý ĐÚNG MỘT LẦN,
 * dù Client có bấm gửi liên tục hay mạng bị chập chờn gây retry.
 */
@Service
@RequiredArgsConstructor
public class TransferIdempotencyServiceImpl implements TransferIdempotencyService {

    private final TransferCommandRepository commandRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock; // Dùng để mock thời gian khi viết Unit Test

    /**
     * Hàm "Gác cổng". Mọi request tạo giao dịch hoặc confirm OTP đều phải đi qua đây trước.
     *
     * @return Kết quả quyết định: Cho phép đi tiếp (PROCEED), hay bắt quay xe lấy kết quả cũ (REPLAY).
     */
    @Override
    @Transactional
    public IdempotencyBeginResult begin(
            UUID ownerKeycloakUserId,
            UUID transferId,
            TransferCommandType commandType,
            UUID idempotencyKey,
            String requestHash
    ) {
        validateInput(ownerKeycloakUserId, commandType, idempotencyKey, requestHash);

        UUID newCommandId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);

        /*
         * 1. BƯỚC INSERT THĂM DÒ (Tối ưu hiệu năng - Không dùng SELECT trước)
         * Hàm insertIfAbsent dưới database sẽ gọi câu lệnh SQL: "INSERT ... ON CONFLICT DO NOTHING".
         * Nếu đây là request mới toanh -> Insert thành công trả về 1.
         * Nếu là request trùng -> Bị DB chặn lại, nuốt lỗi và trả về 0 (không làm sập giao dịch).
         */
        int inserted = commandRepository.insertIfAbsent(
                newCommandId,
                ownerKeycloakUserId,
                transferId,
                commandType.name(),
                idempotencyKey,
                requestHash,
                now
        );

        /*
         * Insert thành công (inserted == 1) nghĩa là request này chưa từng được xử lý.
         * Cho phép luồng nghiệp vụ chạy tiếp (PROCEED).
         */
        if (inserted == 1) {
            return IdempotencyBeginResult.proceed(newCommandId);
        }

        /*
         * 2. XỬ LÝ KHI PHÁT HIỆN TRÙNG LẶP (inserted == 0)
         * Dùng Khóa bi quan (SELECT ... FOR UPDATE) để khóa cứng bản ghi này lại.
         * Đảm bảo không có luồng nào khác đang thay đổi trạng thái của command này trong lúc ta đang đọc.
         */
        TransferCommand existing = commandRepository
                .findByBusinessKeyForUpdate(ownerKeycloakUserId, commandType, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency record disappeared"));

        /*
         * 3. CHỐNG GIAN LẬN (Payload Hijacking)
         * Kẻ gian có thể gửi lại cùng 1 Idempotency-Key nhưng lại lén sửa số tiền từ 100k thành 1 triệu.
         * Ta so sánh Hash của Body. Nếu Hash khác nhau -> Báo lỗi ngay lập tức.
         */
        if (!sameHash(existing.getRequestHash(), requestHash)) {
            throw new IdempotencyConflictException();
        }

        /*
         * Request hash nên chứa transferId.
         * Việc kiểm tra trực tiếp transferId giúp phát hiện lỗi lập trình hoặc sai sót data rõ hơn.
         */
        if (existing.getTransferId() != null && transferId != null && !existing.getTransferId().equals(transferId)) {
            throw new IdempotencyConflictException();
        }

        /*
         * 4. QUYẾT ĐỊNH HÀNH ĐỘNG DỰA TRÊN TRẠNG THÁI CŨ (State Machine Pattern)
         */
        return switch (existing.getStatus()) {

            // Request trước đó vẫn đang chạy, chưa xong. Báo cho Client biết để chờ thêm (hoặc trả mã 409 Conflict).
            case IN_PROGRESS -> new IdempotencyBeginResult(
                    existing.getId(), IdempotencyDecision.IN_PROGRESS, existing.getTransferId(), null, null, null, null
            );

            // Request trước đã THÀNH CÔNG. Nhặt cục JSON kết quả cũ trả về luôn, KHÔNG xử lý lại nghiệp vụ.
            case SUCCEEDED -> new IdempotencyBeginResult(
                    existing.getId(), IdempotencyDecision.REPLAY_SUCCESS, existing.getTransferId(), existing.getResponseJson(), existing.getResponseStatus(), null, null
            );

            // Request trước đã THẤT BẠI. Nhặt cục báo lỗi cũ trả về luôn.
            case FAILED -> new IdempotencyBeginResult(
                    existing.getId(), IdempotencyDecision.REPLAY_FAILURE, existing.getTransferId(), existing.getResponseJson(), existing.getResponseStatus(), existing.getErrorCode(), existing.getErrorMessage()
            );
        };
    }

    /**
     * Đánh dấu request đã xử lý thành công và lưu kết quả JSON lại để dành cho các lần Retry sau.
     */
    @Override
    @Transactional
    public void completeSuccess(UUID commandId, UUID transferId, Object response, int responseStatus) {
        TransferCommand command = commandRepository.findByIdForUpdate(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer command not found: " + commandId));

        /*
         * Hỗ trợ complete được gọi lại do retry nội bộ (VD: Hệ thống đang lưu thì bị timeout, nó gọi lưu lại lần 2).
         */
        if (command.getStatus() == TransferCommandStatus.SUCCEEDED) {
            return;
        }

        // Bắt buộc phải từ IN_PROGRESS mới được nhảy lên SUCCEEDED
        if (command.getStatus() != TransferCommandStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete command in status: " + command.getStatus());
        }

        command.markSucceeded(transferId, serialize(response), responseStatus);
        commandRepository.save(command);
    }

    /**
     * Đánh dấu request thất bại để nếu user có bấm lại (cùng Key) thì trả luôn lỗi, không tốn tài nguyên chạy lại.
     */
    @Override
    @Transactional
    public void completeFailure(UUID commandId, String errorCode, String errorMessage, int responseStatus) {
        TransferCommand command = commandRepository.findByIdForUpdate(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer command not found: " + commandId));

        if (command.getStatus() == TransferCommandStatus.FAILED) {
            return;
        }

        if (command.getStatus() != TransferCommandStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot fail command in status: " + command.getStatus());
        }

        command.markFailed(errorCode, errorMessage, responseStatus);
        commandRepository.save(command);
    }

    /**
     * Dịch ngược JSON đang lưu trong Database thành Object gốc để trả về cho Controller.
     */
    @Override
    public <T> T readResponse(String responseJson, Class<T> responseType) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalArgumentException("Stored response is empty");
        }
        try {
            return objectMapper.readValue(responseJson, responseType);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot deserialize idempotency response", exception);
        }
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize idempotency response", exception);
        }
    }

    /**
     * So sánh 2 chuỗi Hash.
     * Dùng MessageDigest.isEqual thay vì string.equals() để CHỐNG TẤN CÔNG THỜI GIAN (Timing Attack).
     * Hacker không thể đo thời gian hàm chạy để dò ra được chuỗi Hash hợp lệ.
     */
    private boolean sameHash(String storedHash, String requestHash) {
        if (storedHash == null || requestHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void validateInput(UUID ownerKeycloakUserId, TransferCommandType commandType, UUID idempotencyKey, String requestHash) {
        if (ownerKeycloakUserId == null) throw new IllegalArgumentException("ownerKeycloakUserId is required");
        if (commandType == null) throw new IllegalArgumentException("commandType is required");
        if (idempotencyKey == null) throw new IllegalArgumentException("Idempotency-Key is required");
        if (requestHash == null || requestHash.isBlank()) throw new IllegalArgumentException("requestHash is required");
    }
}
