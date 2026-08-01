package vn.edu.uet.chatbot.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.uet.chatbot.security.JwtAuthFilter;
import vn.edu.uet.chatbot.security.RateLimitFilter;

/**
 * JwtAuthFilter và RateLimitFilter được gắn thủ công vào chuỗi filter của
 * Spring Security (xem SecurityConfig#filterChain). Vì cả hai đều
 * là @Component,
 * Spring Boot sẽ tự động đăng ký thêm chúng vào chuỗi filter mặc định của
 * servlet
 * container, khiến chúng chạy 2 lần trên mỗi request.
 *
 * Cấu hình dưới đây tắt việc tự đăng ký đó, chỉ giữ lại bản trong chuỗi Spring
 * Security.
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
