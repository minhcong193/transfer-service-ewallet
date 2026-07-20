package minhtc.vn.transferservice.notification.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.config.BrevoSmtpProperties;
import minhtc.vn.transferservice.exception.OtpDeliveryException;

import minhtc.vn.transferservice.notification.OtpDeliveryPort;
import minhtc.vn.transferservice.notification.TransferOtpEmailTemplateFactory;
import minhtc.vn.transferservice.otp.TransferOtpDeliveryCommand;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
//@ConditionalOnProperty(
//        prefix = "app.otp",
//        name = "delivery-provider",
//        havingValue = "brevo"
//)
@Primary
public class BrevoEmailOtpDeliveryAdapter
        implements OtpDeliveryPort {

    private final JavaMailSender mailSender;

    private final BrevoSmtpProperties properties;

    private final TransferOtpEmailTemplateFactory
            emailTemplateFactory;

    public BrevoEmailOtpDeliveryAdapter(
            @Qualifier("brevoMailSender")
            JavaMailSender mailSender,
            BrevoSmtpProperties properties,
            TransferOtpEmailTemplateFactory
                    emailTemplateFactory
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.emailTemplateFactory =
                emailTemplateFactory;
    }

    @Override
    public void sendTransferOtp(
            TransferOtpDeliveryCommand command
    ) {
        validateCommand(command);

        MimeMessage mimeMessage =
                mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mimeMessage,
                            true, // <-- Sửa ở đây: Bật chế độ multipart
                            StandardCharsets.UTF_8.name()
                    );

            helper.setFrom(
                    properties.senderEmail(),
                    properties.senderName()
            );

            helper.setTo(
                    command.recipientEmail()
            );

            helper.setSubject(
                    properties.transferOtpSubject()
            );

            String plainText =
                    emailTemplateFactory.createPlainText(
                            command.transferId(),
                            command.rawOtp(),
                            command.expiresAt()
                    );

            String htmlContent =
                    emailTemplateFactory.createHtml(
                            command.recipientName(),
                            command.transferCode(),
                            command.rawOtp(),
                            command.expiresAt()
                    );

            helper.setText(
                    plainText,
                    htmlContent
            );

            mailSender.send(mimeMessage);

            log.info(
                    "Transfer OTP email sent successfully. "
                            + "transferId={}, ownerUserId={}, recipient={}",
                    command.transferId(),
                    command.ownerKeycloakUserId(),
                    maskEmail(command.recipientEmail())
            );
        } catch (MessagingException
                 | UnsupportedEncodingException
                 | MailException exception) {

            log.error(
                    "Failed to send transfer OTP email. "
                            + "transferId={}, ownerUserId={}",
                    command.transferId(),
                    command.ownerKeycloakUserId(),
                    exception
            );

            throw new OtpDeliveryException(
                    "Unable to send transfer OTP email",
                    exception
            );
        }
    }

    private void validateCommand(
            TransferOtpDeliveryCommand command
    ) {
        if (command == null) {
            throw new OtpDeliveryException(
                    "OTP delivery command is required"
            );
        }

        if (command.ownerKeycloakUserId() == null) {
            throw new OtpDeliveryException(
                    "OTP owner user ID is required"
            );
        }

        if (command.transferId() == null) {
            throw new OtpDeliveryException(
                    "Transfer ID is required"
            );
        }

        if (!StringUtils.hasText(
                command.recipientEmail()
        )) {
            throw new OtpDeliveryException(
                    "Recipient email is required"
            );
        }

        if (!StringUtils.hasText(
                command.rawOtp()
        )) {
            throw new OtpDeliveryException(
                    "OTP value is required"
            );
        }

        if (command.expiresAt() == null) {
            throw new OtpDeliveryException(
                    "OTP expiration time is required"
            );
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "***";
        }

        int atIndex = email.indexOf('@');

        if (atIndex <= 1) {
            return "***";
        }

        return email.charAt(0)
                + "***"
                + email.substring(atIndex);
    }
}
