package minhtc.vn.transferservice.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;
import java.util.Locale;

/**
 * Configuration class for Internationalization (i18n) support.
 * This class configures how messages are resolved and how the user's locale is determined.
 */
@Configuration
public class MessageConfig implements WebMvcConfigurer {

    /**
     * Configures the MessageSource bean to load messages from properties files.
     *
     * @return The configured MessageSource.
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        // Sets the base name for message files. Spring will look for files like messages.properties,
        // messages_en.properties, messages_vi.properties in the 'src/main/resources/i18n' directory.
        messageSource.setBasename("classpath:i18n/messages");
        // Sets the default encoding to UTF-8 to support special characters (e.g., Vietnamese).
        messageSource.setDefaultEncoding("UTF-8");
        // If a message code is not found, return the code itself instead of throwing an exception.
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    /**
     * Configures the LocaleResolver to determine the current locale based on the session.
     *
     * @return The configured LocaleResolver.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();

        // Sử dụng Locale.of() thay vì new Locale() (Bị Deprecated từ Java 19+)
        resolver.setDefaultLocale(Locale.of("vi"));

        // (Tuỳ chọn) Giới hạn danh sách các ngôn ngữ hệ thống hỗ trợ
        // Tránh trường hợp user gửi ngôn ngữ lạ, hệ thống tự fallback về mặc định
        resolver.setSupportedLocales(List.of(
                Locale.of("vi"),
                Locale.of("en")
        ));

        return resolver;
    }

    // BẮT BUỘC: Đi kèm với Interceptor để bắt tham số ?lang= trên URL
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
