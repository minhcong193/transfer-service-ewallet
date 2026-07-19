package minhtc.vn.transferservice.enums;

public enum WalletReservationStatus {
    // Đã đóng băng / Tạm giam 1 khoản tiển chờ kết quả giao dịch, tiền:
    // sau khi nhận ReserveCommand
    // app show available balance (sau khi trừ khoản tiền held). số tiền thực tế giữ nguyên
    HELD,
    // Đã chốt hạ / Trừ vĩnh viễn: giao dịch chuyển thành công
    // sau khi nhận FinalizeCommand
    FINALIZED,
    // Đã nhả tiền / Hủy đóng băng / Đã hoàn tiền
    // giao dịch thất bại, ví bị khóa
    // nhả tiền về available_balance
    RELEASED
}