package vn.edu.uet.chatbot.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Trước đây có field `role` nhưng AuthService.register() luôn gán cứng
// ROLE_STUDENT và không đọc field này — dead code gây hiểu nhầm rằng người
// dùng có thể tự chọn role khi đăng ký (thực ra không thể, và không nên thể,
// tránh privilege escalation). Đã bỏ field để phản ánh đúng hành vi thực tế.
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 6, message = "must be at least 6 characters long") String password) {
}
