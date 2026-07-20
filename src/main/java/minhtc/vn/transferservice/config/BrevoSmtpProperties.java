package minhtc.vn.transferservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.brevo")
public record BrevoSmtpProperties(
        String host,
        int port,
        String username,
        String smtpKey,

        String senderEmail,
        String senderName,
        String transferOtpSubject,

        int connectionTimeoutMillis,
        int readTimeoutMillis,
        int writeTimeoutMillis
) {
}