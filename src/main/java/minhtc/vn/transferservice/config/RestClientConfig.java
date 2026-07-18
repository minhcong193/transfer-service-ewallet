package minhtc.vn.transferservice.config;

import minhtc.vn.transferservice.client.WalletClientProperties;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Lớp cấu hình cho RestClient.
 * Định nghĩa các bean để tạo và cấu hình RestClient cho việc giao tiếp với các dịch vụ RESTful khác.
 */
@Configuration
public class RestClientConfig {

    /**
     * Định nghĩa một bean RestClient.Builder.
     * Bean này cung cấp một builder cơ bản để tạo các thể hiện RestClient.
     * Điều này giúp giải quyết lỗi "Could not autowire. No beans of 'Builder' type found"
     * khi Spring không tự động cấu hình RestClient.Builder.
     *
     * @return Một thể hiện của RestClient.Builder.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Cấu hình và tạo một RestClient cụ thể để giao tiếp với dịch vụ Wallet.
     * RestClient này được tùy chỉnh với các cài đặt timeout, header mặc định
     * và có thể bao gồm token xác thực nếu được cung cấp.
     *
     * @param builder Một RestClient.Builder được inject bởi Spring.
     * @param properties Các thuộc tính cấu hình cho Wallet client (ví dụ: baseUrl, connectTimeout, readTimeout, serviceToken).
     * @return Một thể hiện của RestClient đã được cấu hình sẵn để gọi dịch vụ Wallet.
     */
    @Bean
    public RestClient walletRestClient(
            RestClient.Builder builder,
            WalletClientProperties properties
    ) {
        // Cấu hình RequestConfig cho HttpClient, bao gồm thời gian chờ kết nối và thời gian chờ phản hồi.
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(
                        Timeout.ofMilliseconds(
                                properties.connectTimeout().toMillis()
                        )
                )
                .setResponseTimeout(
                        Timeout.ofMilliseconds(
                                properties.readTimeout().toMillis()
                        )
                )
                .build();

        // Tạo CloseableHttpClient với RequestConfig đã cấu hình và tắt tự động thử lại.
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();

        // Tạo HttpComponentsClientHttpRequestFactory sử dụng HttpClient đã tạo.
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        // Sử dụng builder được inject để cấu hình RestClient.
        RestClient.Builder restClientBuilder = builder
                .baseUrl(properties.baseUrl()) // Đặt URL cơ sở cho RestClient.
                .requestFactory(requestFactory) // Đặt request factory tùy chỉnh.
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                ) // Đặt header Content-Type mặc định là application/json.
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                ); // Đặt header Accept mặc định là application/json.

        // Nếu có service token, thêm header Authorization.
        if (properties.serviceToken() != null
                && !properties.serviceToken().isBlank()) {
            restClientBuilder.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + properties.serviceToken()
            );
        }

        // Xây dựng và trả về RestClient đã cấu hình.
        return restClientBuilder.build();
    }
}
