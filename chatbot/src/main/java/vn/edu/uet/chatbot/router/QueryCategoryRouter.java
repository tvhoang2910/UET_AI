package vn.edu.uet.chatbot.router;

import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.ingest.model.DocumentCategory;
import vn.edu.uet.chatbot.util.CourseCodeUtils;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class QueryCategoryRouter {

    private static final Pattern QUY_CHE_PATTERN = Pattern.compile(
            "(?i)(quy\\s*chế|học\\s*vụ|chuẩn\\s*đầu\\s*ra|cảnh\\s*báo\\s*học\\s*vụ|buộc\\s*thôi\\s*học|điểm\\s*rèn\\s*luyện|xếp\\s*loại|thang\\s*điểm|gpa|cpa|tốt\\s*nghiệp|bảo\\s*lưu|chuyển\\s*ngành)");

    private static final Pattern KE_HOACH_PATTERN = Pattern.compile(
            "(?i)(kế\\s*hoạch|lịch\\s*trình|thời\\s*khóa\\s*biểu|lịch\\s*thi|thi\\s*hết\\s*môn|tuần\\s*\\d+|học\\s*kỳ\\s*(1|2|phụ|hè)|nghỉ\\s*tết|nghỉ\\s*hè)");

    private static final Pattern THU_TUC_PATTERN = Pattern.compile(
            "(?i)(học\\s*phí|nộp\\s*tiền|lệ\\s*phí|học\\s*bổng|ký\\s*túc\\s*xá|ktx|miễn\\s*giảm|bảo\\s*hiểm|bảng\\s*điểm|giấy\\s*xác\\s*nhận|hoãn\\s*thi|tạm\\s*hoãn|nghĩa\\s*vụ|đơn\\s*từ|thủ\\s*tục)");

    public Optional<DocumentCategory> detectCategory(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String lower = query.toLowerCase();
        if (!CourseCodeUtils.extractCourseCodes(query).isEmpty()
                || lower.matches(".*\\b(môn học|học phần|tiên quyết|học trước|đề cương|tín chỉ môn)\\b.*")) {
            return Optional.of(DocumentCategory.MON_HOC);
        }

        if (QUY_CHE_PATTERN.matcher(query).find()) {
            return Optional.of(DocumentCategory.QUY_CHE);
        }

        if (KE_HOACH_PATTERN.matcher(query).find()) {
            return Optional.of(DocumentCategory.KE_HOACH);
        }

        if (THU_TUC_PATTERN.matcher(query).find()) {
            return Optional.of(DocumentCategory.THU_TUC_SV);
        }

        return Optional.empty();
    }
}
