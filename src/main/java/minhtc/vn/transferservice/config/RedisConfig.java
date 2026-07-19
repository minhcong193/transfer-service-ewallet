package minhtc.vn.transferservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cấu hình Redis cho Transfer Service.
 *
 * <p>Redis được sử dụng cho:</p>
 *
 * <ul>
 *     <li>Lưu OTP hash theo transfer ID.</li>
 *     <li>Quản lý số lần nhập OTP sai.</li>
 *     <li>Quản lý thời gian cooldown khi resend OTP.</li>
 *     <li>Giới hạn số OTP được tạo theo ngày.</li>
 * </ul>
 *
 * <p>Transfer Service chỉ lưu chuỗi và Redis Hash, vì vậy sử dụng
 * {@link StringRedisTemplate}. Không cần dùng Java serialization.</p>
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    /**
     * Tạo StringRedisTemplate dùng cho các thao tác OTP.
     *
     * <p>Tất cả key, hash key và hash value được serialize bằng UTF-8
     * StringRedisSerializer. Điều này giúp kiểm tra dữ liệu trực tiếp bằng
     * redis-cli dễ dàng hơn.</p>
     *
     * @param connectionFactory Redis connection factory do Spring Boot tạo
     * @return StringRedisTemplate đã cấu hình serializer
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        StringRedisSerializer stringSerializer =
                new StringRedisSerializer();

        StringRedisTemplate template =
                new StringRedisTemplate();

        template.setConnectionFactory(
                connectionFactory
        );

        template.setKeySerializer(
                stringSerializer
        );

        template.setValueSerializer(
                stringSerializer
        );

        template.setHashKeySerializer(
                stringSerializer
        );

        template.setHashValueSerializer(
                stringSerializer
        );

        template.afterPropertiesSet();

        return template;
    }

    /**
     * Cung cấp helper tạo Redis key thống nhất cho toàn bộ Transfer Service.
     */
    @Bean
    public TransferRedisKeyFactory transferRedisKeyFactory(
            TransferRedisProperties properties
    ) {
        return new TransferRedisKeyFactory(
                properties.keyPrefix()
        );
    }
}