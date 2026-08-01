package vn.edu.uet.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;

    // /api/chat/** : giới hạn theo user (hoặc IP nếu chưa xác thực)
    private int chatCapacity = 20;
    private int chatRefillPerMinute = 20;

    // /api/documents/upload : giới hạn riêng, nghiêm ngặt hơn vì tốn nhiều tài
    // nguyên (extract PDF + embedding hàng loạt)
    private int uploadCapacity = 5;
    private int uploadRefillPerMinute = 5;
}
