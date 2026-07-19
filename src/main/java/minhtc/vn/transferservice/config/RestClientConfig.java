package minhtc.vn.transferservice.config;

import minhtc.vn.transferservice.client.WalletClientProperties;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier; // <-- Thêm import này
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Cấu hình HTTP client dùng để Transfer Service giao tiếp với Wallet Service.
 *
 * <p>Cấu hình này chịu trách nhiệm:</p>
 * <ul>
 *     <li>Tạo {@link RestClient.Builder} để các RestClient khác có thể tái sử dụng.</li>
 *     <li>Cấu hình connect timeout và response timeout.</li>
 *     <li>Tắt automatic retry ở tầng Apache HttpClient.</li>
 *     <li>Cấu hình base URL của Wallet Service.</li>
 *     <li>Gắn service token cho các internal API nếu được khai báo.</li>
 * </ul>
 *
 * <p>Lưu ý: Retry nghiệp vụ không nên thực hiện tự động ở HTTP client vì các
 * command tài chính phải được retry có kiểm soát bằng cùng {@code commandId}.</p>
 */
@Configuration
public class RestClientConfig {

    /**
     * Tạo RestClient.Builder dưới dạng prototype.
     *
     * <p>{@link RestClient.Builder} là một mutable builder. Nếu khai báo dưới
     * dạng singleton mặc định, cấu hình của một RestClient có thể vô tình ảnh
     * hưởng tới RestClient khác.</p>
     *
     * @return builder mới cho mỗi lần Spring yêu cầu bean
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Cấu hình timeout cho các request gửi tới Wallet Service.
     *
     * <p>Các timeout được lấy từ {@link WalletClientProperties}:</p>
     * <ul>
     *     <li>Connection request timeout: thời gian chờ lấy connection.</li>
     *     <li>Connect timeout: thời gian chờ thiết lập TCP connection.</li>
     *     <li>Response timeout: thời gian chờ Wallet Service phản hồi.</li>
     * </ul>
     *
     * @param properties cấu hình Wallet Service client
     * @return RequestConfig dành cho Wallet Service
     */
    @Bean
    public RequestConfig walletRequestConfig(
            WalletClientProperties properties
    ) {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(
                        Timeout.ofMilliseconds(
                                properties.connectTimeout().toMillis()
                        )
                )
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
    }

    /**
     * Tạo Apache HttpClient dùng riêng cho Wallet Service.
     *
     * <p>Automatic retry bị tắt vì request reserve, credit, finalize và release
     * là các command tài chính. Việc retry phải do Transfer Saga hoặc Recovery
     * Service kiểm soát bằng cùng một {@code commandId}.</p>
     *
     * <p>Bean được cấu hình {@code destroyMethod = "close"} để Spring đóng
     * HttpClient khi application shutdown.</p>
     *
     * @param requestConfig cấu hình timeout
     * @return Apache HttpClient đã cấu hình
     */
    @Bean(
            name = "walletHttpClient",
            destroyMethod = "close"
    )
    public CloseableHttpClient walletHttpClient(
            RequestConfig requestConfig
    ) {
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
    }

    /**
     * Tạo request factory dùng Apache HttpClient 5 cho Wallet RestClient.
     *
     * @param httpClient Apache HttpClient dành riêng cho Wallet Service
     * @return request factory cho Wallet RestClient
     */
    @Bean(name = "walletRequestFactory")
    public HttpComponentsClientHttpRequestFactory walletRequestFactory(
            @Qualifier("walletHttpClient")
            CloseableHttpClient httpClient
    ) {
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /**
     * Tạo RestClient dùng để gọi các internal API của Wallet Service.
     *
     * <p>Mỗi request sẽ lấy access token từ Keycloak bằng Client Credentials
     * thông qua OAuth2AuthorizedClientManager, sau đó gắn token vào header:</p>
     *
     * <pre>
     * Authorization: Bearer {access_token}
     * </pre>
     *
     * <p>Token không được lưu cố định trong application.yaml. Spring Security
     * sẽ quản lý việc lấy token mới khi token hiện tại hết hạn.</p>
     */
    @Bean(name = "walletRestClient")
    public RestClient walletRestClient(
            RestClient.Builder builder,
            WalletClientProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager
    ) {
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

        /*
         * Tắt automatic retry ở tầng HTTP client.
         *
         * Các lệnh tài chính như reserve, credit, finalize và release
         * chỉ được retry bởi Saga Recovery với cùng commandId.
         */
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                /*
                 * Interceptor được gọi trước mỗi HTTP request.
                 *
                 * Nó yêu cầu access token cho OAuth2 client có registrationId
                 * là "wallet-service-client".
                 */
                .requestInterceptor((request, body, execution) -> {
                    OAuth2AuthorizeRequest authorizeRequest =
                            OAuth2AuthorizeRequest
                                    .withClientRegistrationId(
                                            "wallet-service"
                                    )
                                    /*
                                     * Client Credentials không có user đăng nhập.
                                     * Principal này chỉ dùng làm định danh để
                                     * OAuth2AuthorizedClientManager cache token.
                                     */
                                    .principal(
                                            new AnonymousAuthenticationToken(
                                                    "transfer-service",
                                                    "transfer-service",
                                                    AuthorityUtils.createAuthorityList(
                                                            "ROLE_SYSTEM"
                                                    )
                                            )
                                    )
                                    .build();

                    OAuth2AuthorizedClient authorizedClient =
                            authorizedClientManager.authorize(
                                    authorizeRequest
                            );

                    if (authorizedClient == null
                            || authorizedClient.getAccessToken() == null) {
                        throw new IllegalStateException(
                                "Cannot obtain Wallet Service access token from Keycloak"
                        );
                    }

                    /*
                     * Gắn access token do Keycloak cấp vào request gọi
                     * Wallet Service.
                     */
                    request.getHeaders().setBearerAuth(
                            authorizedClient
                                    .getAccessToken()
                                    .getTokenValue()
                    );

                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * Tạo OAuth2AuthorizedClientManager hỗ trợ Client Credentials.
     *
     * <p>Manager chịu trách nhiệm:</p>
     * <ul>
     *     <li>Gọi token endpoint của Keycloak.</li>
     *     <li>Gửi client-id và client-secret.</li>
     *     <li>Nhận access token.</li>
     *     <li>Tái sử dụng token khi còn hạn.</li>
     *     <li>Lấy token mới khi token hết hạn.</li>
     * </ul>
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService
    ) {
        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientService
                );

        manager.setAuthorizedClientProvider(
                authorizedClientProvider
        );

        return manager;
    }

    /**
     * Kiểm tra một chuỗi có nội dung thực tế hay không.
     *
     * @param value chuỗi cần kiểm tra
     * @return true khi chuỗi không null và không chỉ chứa khoảng trắng
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
