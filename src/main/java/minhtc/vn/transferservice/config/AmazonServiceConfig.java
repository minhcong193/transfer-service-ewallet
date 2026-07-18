package minhtc.vn.transferservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@Slf4j
public class AmazonServiceConfig {
    @Value("${app.r2.access-key}")
    private String accessKey;

    @Value("${app.r2.secret-key}")
    private String secretKey;

    @Value("${app.r2.region}")
    private String region;

    @Value("${app.r2.endpoint}")
    private String s3Endpoint;

    @Bean
    public S3Client s3Client() {
        log.info("--- Loading R2 S3 Configuration ---");
        log.info("Endpoint: {}", s3Endpoint);
        log.info("Region: {}", region);
        log.info("Access Key ID: {}", accessKey);
        // Kiểm tra sự tồn tại của Secret Key một cách an toàn
        if (secretKey != null && !secretKey.isEmpty()) {
            log.info("Secret Access Key: Loaded (length: {})", secretKey.length());
        } else {
            log.error("Secret Access Key: NOT LOADED OR EMPTY!");
        }
        log.info("------------------------------------");
        // 1. Tạo credentials provider từ access key và secret key đã đọc.
        // StaticCredentialsProvider phù hợp khi credentials không thay đổi trong lúc ứng dụng chạy
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );
        // 2. Xây dựng (build) S3Client với các cấu hình cần thiết cho R2.
        return S3Client.builder()
                // Cung cấp credentials để xác thực.
                .credentialsProvider(credentialsProvider)
                // lấy AWS_ACCESS_KEY_ID và AWS_SECRET_ACCESS_KEY. trong biến môi trường để khởi tạo
                //.credentialsProvider(DefaultCredentialsProvider.create())

                // Mặc dù R2 không có region, SDK vẫn yêu cầu một giá trị.
                .region(Region.of(region))

                // ĐÂY LÀ BƯỚC QUAN TRỌNG NHẤT:
                // Trỏ client đến endpoint của Cloudflare R2 thay vì endpoint mặc định của AWS.
                .endpointOverride(URI.create(s3Endpoint))

                // BẮT BUỘC cho R2 và các S3-compatible storage khác.
                // Bật chế độ truy cập kiểu path (endpoint/bucket/key).
                .forcePathStyle(true)
                .build();
    }
}
