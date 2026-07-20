package minhtc.vn.transferservice.enums;

/**
 * Tập hợp các loại Lệnh (Command) thao tác với tiền trong Ví.
 * Phục vụ cho kiến trúc phân tán áp dụng Saga Pattern (mô hình Hold & Capture).
 */
public enum WalletCommandType {

    /**
     * LỆNH ĐÓNG BĂNG TIỀN (Hold/Freeze)
     * - Mục tiêu: Ví người gửi (Source Wallet).
     * - Hành động: Trừ "số dư khả dụng" (available_balance), nhưng giữ nguyên "số dư thực tế" (actual_balance).
     * - Ý nghĩa: Khóa chặt khoản tiền này lại để đảm bảo an toàn, không cho khách hàng rút ra hay mua sắm món khác trong lúc luồng chuyển tiền đang xử lý.
     * - Kết quả: Tạo ra một phiếu tạm giữ ở trạng thái HELD.
     */
    RESERVE,

    /**
     * LỆNH CỘNG TIỀN (Add funds)
     * - Mục tiêu: Ví người nhận (Target Wallet).
     * - Hành động: Cộng thẳng vào cả "số dư khả dụng" lẫn "số dư thực tế".
     * - Ý nghĩa: Đầu bên kia đã sẵn sàng, nổ tiền vào tài khoản cho người nhận.
     */
    CREDIT,

    /**
     * LỆNH CHỐT TRỪ TIỀN (Capture/Commit)
     * - Mục tiêu: Ví người gửi.
     * - Hành động: Trừ "số dư thực tế" tương ứng với khoản tiền đã giam giữ.
     * - Ý nghĩa: Nhạc trưởng báo tin "Giao dịch đã thành công trọn vẹn, đích đã nhận được tiền".
     *            Ví nguồn tiến hành đốt bỏ hoàn toàn khoản tiền đang bị giam.
     * - Kết quả: Phiếu tạm giữ chuyển từ HELD sang FINALIZED.
     */
    FINALIZE_RESERVATION,

    /**
     * LỆNH HOÀN TIỀN / HỦY ĐÓNG BĂNG (Rollback/Compensation)
     * - Mục tiêu: Ví người gửi.
     * - Hành động: Trả lại số tiền đang giam giữ cộng ngược vào "số dư khả dụng".
     * - Ý nghĩa: Luồng chuyển tiền gặp sự cố (ví dụ tài khoản nhận bị khóa, hoặc lỗi mạng).
     *            Nhạc trưởng ra lệnh hủy bỏ giao dịch, nhả tiền lại cho khách tiêu xài bình thường.
     * - Kết quả: Phiếu tạm giữ chuyển từ HELD sang RELEASED.
     */
    RELEASE_RESERVATION
}