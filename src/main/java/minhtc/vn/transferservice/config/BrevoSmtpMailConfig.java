package minhtc.vn.transferservice.config;

import minhtc.vn.transferservice.config.BrevoSmtpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties(BrevoSmtpProperties.class)
public class BrevoSmtpMailConfig {

    @Bean
    @Qualifier("brevoMailSender")
//    @ConditionalOnProperty(
//            prefix = "app.otp",
//            name = "delivery-provider",
//            havingValue = "brevo"
//    )
    public JavaMailSender brevoMailSender(
            BrevoSmtpProperties properties
    ) {
        validateConfiguration(properties);

        JavaMailSenderImpl mailSender =
                new JavaMailSenderImpl();

        mailSender.setHost(properties.host());
        mailSender.setPort(properties.port());

        // Login hiển thị trong phần SMTP Settings của Brevo
        mailSender.setUsername(properties.username());

        // Password chính là SMTP key, không phải password tài khoản Brevo
        mailSender.setPassword(properties.smtpKey());

        mailSender.setDefaultEncoding(
                StandardCharsets.UTF_8.name()
        );

        Properties mailProperties =
                mailSender.getJavaMailProperties();

        mailProperties.put(
                "mail.transport.protocol",
                "smtp"
        );

        mailProperties.put(
                "mail.smtp.auth",
                "true"
        );

        mailProperties.put(
                "mail.smtp.starttls.enable",
                "true"
        );

        mailProperties.put(
                "mail.smtp.starttls.required",
                "true"
        );

        mailProperties.put(
                "mail.smtp.connectiontimeout",
                properties.connectionTimeoutMillis()
        );

        mailProperties.put(
                "mail.smtp.timeout",
                properties.readTimeoutMillis()
        );

        mailProperties.put(
                "mail.smtp.writetimeout",
                properties.writeTimeoutMillis()
        );

        mailProperties.put(
                "mail.debug",
                "false"
        );

        return mailSender;
    }

    private void validateConfiguration(
            BrevoSmtpProperties properties
    ) {
        requireText(
                properties.host(),
                "Brevo SMTP host is required"
        );

        if (properties.port() <= 0) {
            throw new IllegalStateException(
                    "Brevo SMTP port must be greater than zero"
            );
        }

        requireText(
                properties.username(),
                "Brevo SMTP username is required"
        );

        requireText(
                properties.smtpKey(),
                "Brevo SMTP key is required"
        );

        requireText(
                properties.senderEmail(),
                "Brevo sender email is required"
        );

        requireText(
                properties.senderName(),
                "Brevo sender name is required"
        );

        requireText(
                properties.transferOtpSubject(),
                "Transfer OTP email subject is required"
        );
    }

    private void requireText(
            String value,
            String message
    ) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}