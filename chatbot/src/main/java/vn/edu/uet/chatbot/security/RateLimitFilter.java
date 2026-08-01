package vn.edu.uet.chatbot.security;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.uet.chatbot.config.RateLimitProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit đơn giản dựa trên bucket4j, lưu bucket trong bộ nhớ tiến trình
 * (ConcurrentHashMap). Đủ dùng cho triển khai 1 instance; nếu scale ra nhiều
 * node cần chuyển sang bucket4j-redis (hoặc tương đương) để chia sẻ trạng thái
 * giữa các instance.
 *
 * LƯU Ý: filter này được add thủ công vào SecurityConfig#filterChain, và việc
 * tự-đăng-ký-thêm vào servlet container đã bị tắt trong
 * FilterRegistrationConfig
 * để tránh chạy 2 lần/request (xem class đó để biết lý do).
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimitProperties properties;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = resolveRule(request.getRequestURI(), request.getMethod());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = resolveIdentity(request);
        Bucket bucket = buckets.computeIfAbsent(rule.name() + ":" + identity, key -> newBucket(rule));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"title\":\"Too Many Requests\"," +
                            "\"detail\":\"Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau ít phút.\"}");
        }
    }

    private RateLimitRule resolveRule(String path, String method) {
        if (PATH_MATCHER.match("/api/chat/**", path)) {
            return new RateLimitRule("chat", properties.getChatCapacity(), properties.getChatRefillPerMinute());
        }
        if ("POST".equalsIgnoreCase(method) && PATH_MATCHER.match("/api/documents/upload", path)) {
            return new RateLimitRule("upload", properties.getUploadCapacity(), properties.getUploadRefillPerMinute());
        }
        return null;
    }

    private String resolveIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return "user:" + auth.getName();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        return "ip:" + ip;
    }

    private Bucket newBucket(RateLimitRule rule) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(rule.capacity())
                        .refillGreedy(rule.refillPerMinute(), Duration.ofMinutes(1)))
                .build();
    }

    private record RateLimitRule(String name, int capacity, int refillPerMinute) {
    }
}
