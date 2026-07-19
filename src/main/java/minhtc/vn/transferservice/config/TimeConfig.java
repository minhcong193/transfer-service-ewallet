package minhtc.vn.transferservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        // Trả về thời gian thực tế theo múi giờ mặc định của máy chủ (ví dụ: Asia/Ho_Chi_Minh)
        return Clock.systemDefaultZone(); 
        
        // HOẶC NẾU HỆ THỐNG LÀM VIỆC ĐA QUỐC GIA:
        // Luôn luôn ép hệ thống chạy theo giờ UTC (Chuẩn quốc tế - Khuyên dùng)
        // return Clock.systemUTC(); 
    }
}