package minhtc.vn.transferservice.notification;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class TransferOtpEmailTemplateFactory {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "HH:mm:ss dd/MM/yyyy"
            );

    public String createHtml(
            String recipientName,
            String transferCode,
            String rawOtp,
            LocalDateTime expiresAt
    ) {
        String safeName =
                escape(
                        StringUtils.hasText(recipientName)
                                ? recipientName
                                : "Quý khách"
                );

        String safeOtp =
                escape(rawOtp);


        String safeExpiresAt =
                escape(
                        expiresAt.format(DATE_FORMATTER)
                );

        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0">
                    <title>Xác nhận chuyển tiền</title>
                </head>

                <body style="
                    margin: 0;
                    padding: 0;
                    background: #f3f4f6;
                    font-family: Arial, Helvetica, sans-serif;
                    color: #111827;">

                    <table role="presentation"
                           width="100%"
                           cellspacing="0"
                           cellpadding="0"
                           style="
                               width: 100%;
                               background: #f3f4f6;
                               padding: 32px 12px;">

                        <tr>
                            <td align="center">

                                <table role="presentation"
                                       width="100%"
                                       cellspacing="0"
                                       cellpadding="0"
                                       style="
                                           width: 100%;
                                           max-width: 600px;
                                           background: #ffffff;
                                           border-radius: 16px;
                                           overflow: hidden;
                                           box-shadow: 0 8px 24px
                                               rgba(0, 0, 0, 0.08);">

                                    <tr>
                                        <td style="
                                            background: #111827;
                                            padding: 28px 32px;
                                            text-align: center;">

                                            <div style="
                                                color: #ffffff;
                                                font-size: 26px;
                                                font-weight: 700;">
                                                E-Wallet
                                            </div>

                                            <div style="
                                                color: #d1d5db;
                                                font-size: 14px;
                                                margin-top: 8px;">
                                                Xác nhận giao dịch chuyển tiền
                                            </div>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td style="padding: 32px;">

                                            <p style="
                                                margin: 0 0 18px;
                                                font-size: 16px;">
                                                Xin chào
                                                <strong>{{recipientName}}</strong>,
                                            </p>

                                            <p style="
                                                margin: 0 0 22px;
                                                font-size: 15px;
                                                line-height: 1.6;
                                                color: #4b5563;">
                                                Bạn vừa yêu cầu thực hiện
                                                một giao dịch chuyển tiền.
                                                Sử dụng mã OTP dưới đây
                                                để xác nhận giao dịch.
                                            </p>

                                            <div style="
                                                margin: 24px 0;
                                                padding: 22px;
                                                background: #f9fafb;
                                                border: 1px solid #e5e7eb;
                                                border-radius: 12px;
                                                text-align: center;">

                                                <div style="
                                                    font-size: 12px;
                                                    color: #6b7280;
                                                    margin-bottom: 12px;
                                                    letter-spacing: 1px;">
                                                    MÃ OTP
                                                </div>

                                                <div style="
                                                    font-size: 38px;
                                                    letter-spacing: 10px;
                                                    font-weight: 700;
                                                    color: #111827;">
                                                    {{otp}}
                                                </div>
                                            </div>

                                            <p style="
                                                margin: 0 0 10px;
                                                font-size: 14px;
                                                color: #4b5563;">
                                                Mã OTP hết hạn lúc:
                                                <strong>{{expiresAt}}</strong>
                                            </p>

                                            <p style="
                                                margin: 0 0 24px;
                                                font-size: 13px;
                                                color: #6b7280;
                                                word-break: break-all;">
                                                Mã giao dịch:
                                                <strong>{{transferCode}}</strong>
                                            </p>

                                            <div style="
                                                padding: 16px;
                                                background: #fff7ed;
                                                border-left:
                                                    4px solid #f97316;
                                                border-radius: 6px;
                                                font-size: 13px;
                                                line-height: 1.6;
                                                color: #9a3412;">
                                                Không cung cấp OTP cho bất kỳ ai.
                                                Nhân viên E-Wallet sẽ không bao giờ
                                                yêu cầu bạn đọc mã OTP.
                                            </div>

                                            <p style="
                                                margin: 24px 0 0;
                                                font-size: 13px;
                                                line-height: 1.5;
                                                color: #6b7280;">
                                                Nếu bạn không thực hiện giao dịch
                                                này, hãy bỏ qua email và kiểm tra
                                                lại tài khoản.
                                            </p>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td style="
                                            background: #f9fafb;
                                            padding: 20px 32px;
                                            text-align: center;
                                            font-size: 12px;
                                            color: #9ca3af;">
                                            Email tự động từ
                                            E-Wallet Security Platform.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .replace(
                        "{{recipientName}}",
                        safeName
                )
                .replace(
                        "{{otp}}",
                        safeOtp
                )
                .replace(
                        "{{expiresAt}}",
                        safeExpiresAt
                )
                .replace(
                        "{{transferCode}}",
                        transferCode
                );
    }

    public String createPlainText(
            UUID transferId,
            String rawOtp,
            LocalDateTime expiresAt
    ) {
        return """
                Mã OTP xác nhận chuyển tiền của bạn là: %s

                Mã giao dịch: %s
                Thời gian hết hạn: %s

                Không cung cấp OTP cho bất kỳ ai.
                """
                .formatted(
                        rawOtp,
                        transferId,
                        expiresAt.format(DATE_FORMATTER)
                );
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}