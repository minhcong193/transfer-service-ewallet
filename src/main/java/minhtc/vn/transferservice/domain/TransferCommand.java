package minhtc.vn.transferservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import minhtc.vn.transferservice.enums.TransferCommandStatus;
import minhtc.vn.transferservice.enums.TransferCommandType;

import java.util.UUID;

/**
 * Entity lưu trữ chi tiết các Lệnh (Command) mà Saga Orchestrator đã/sắp phát đi.
 * Đóng vai trò như hộp đen ghi lại mệnh lệnh để phục hồi (Retry) khi có sự cố.
 */
@Entity
@Table(name = "transfer_commands")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferCommand extends BaseEntity {

    /**
     * ID của giao dịch chuyển tiền gốc.
     */
    private UUID transferId;

    /**
     * Loại mệnh lệnh phát đi (VD: RESERVE, CREDIT, FINALIZE, RELEASE).
     * Tương ứng với 4 bước của luồng Saga Hold & Capture.
     */
    @Enumerated(EnumType.STRING)
    private TransferCommandType commandType;

    /**
     * Khóa chống trùng lặp sinh ra cho mệnh lệnh này.
     * Khi WalletService nhận được lệnh, nó sẽ check khóa này. Nếu trùng, nó trả về kết quả cũ
     * thay vì trừ tiền lần 2. (Idempotent Receiver).
     */
    private String idempotencyKey;

    /**
     * Mã băm của nội dung lệnh. Dùng để đảm bảo dữ liệu không bị thay đổi
     * trong quá trình truyền tải qua Message Broker.
     */
    private String requestHash;

    /**
     * Trạng thái của lệnh (VD: PENDING - Chờ gửi, SENT - Đã gửi lên Kafka,
     * COMPLETED - Bên kia đã làm xong, FAILED - Bên kia báo lỗi).
     */
    @Enumerated(EnumType.STRING)
    private TransferCommandStatus status;

    /**
     * Nội dung phản hồi (JSON) từ service đích (WalletService) trả về.
     * Dùng type jsonb (PostgreSQL) để tối ưu việc query nếu cần.
     * VD: Lưu lại mã phiếu giam tiền do WalletService sinh ra.
     */
    @Column(columnDefinition = "jsonb")
    private String responsePayload;

    /**
     * Mã lỗi nếu lệnh thất bại (VD: WALLET_NOT_FOUND, BALANCE_NOT_ENOUGH).
     */
    private String failureCode;

    /**
     * Chi tiết lỗi (Message text) trả về từ service đích.
     */
    private String failureMessage;
}
