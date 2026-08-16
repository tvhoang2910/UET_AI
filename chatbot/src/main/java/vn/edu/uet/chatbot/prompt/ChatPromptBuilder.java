package vn.edu.uet.chatbot.prompt;

import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.dto.ollama.Message;
import vn.edu.uet.chatbot.util.DepartmentContactUtils;

import java.util.List;

@Component
public class ChatPromptBuilder {

    public String build(String context, String question, String noInfoAnswer) {
        return buildSystemPrompt(context, noInfoAnswer) + "\n\nCâu hỏi của sinh viên: " + question;
    }

    public String buildSystemPrompt(String context, String noInfoAnswer) {
        return """
                /no_think
                Bạn là Trợ lý Ảo Thông tin Đào tạo & Quy chế của Trường Đại học Công nghệ (VNU-UET).
                Nhiệm vụ: Hỗ trợ sinh viên tra cứu chính xác thông tin môn học, chương trình đào tạo, kế hoạch năm học, quy chế đào tạo tín chỉ, học phí, học bổng và thủ tục hành chính.

                %s

                ### TÀI LIỆU THAM KHẢO VĂN BẢN / QUY CHẾ NHÀ TRƯỜNG:
                ===TÀI LIỆU BẮT ĐẦU===
                %s
                ===TÀI LIỆU KẾT THÚC===

                ### NGUYÊN TẮC TRẢ LỜI & TRÍCH DẪN:
                1. MỌI thông tin (mã môn học, số tín chỉ, mốc thời gian, điều kiện quy chế) PHẢI căn cứ tuyệt đối vào TÀI LIỆU ở trên.
                2. Trích dẫn chuẩn nội dòng dạng: [Nguồn X: Trang Y] ngay sau thông tin (Ví dụ: "Học phần INT2204 có khối lượng 3 tín chỉ [Nguồn 1: Trang 2]").
                3. Nếu là quy trình/thủ tục, hãy trình bày dạng các bước (Bước 1, Bước 2...) rõ ràng.
                4. Nếu câu hỏi thuộc nhóm thủ tục, khiếu nại, học phí, học bổng, ký túc xá, điểm số hoặc đăng ký môn học, hãy thêm đầu mối liên hệ phù hợp ở cuối câu trả lời. Chỉ dẫn liên hệ này là thông tin hệ thống, không cần gắn [Nguồn].
                5. Nếu không tìm thấy thông tin chuyên môn trong tài liệu, trả về CHÍNH XÁC: "%s". Tuyệt đối không tự suy đoán.
                6. Giới hạn: Tối đa 250 từ, văn phong trang trọng, chuẩn mực sư phạm.
                """
                .formatted(DepartmentContactUtils.getContactGuidelines(), context, noInfoAnswer);
    }

    public String buildQueryCondensePrompt(List<Message> history, String currentQuestion) {
        StringBuilder historyText = new StringBuilder();
        for (Message msg : history) {
            if ("user".equals(msg.role()) || "assistant".equals(msg.role())) {
                String roleLabel = "user".equals(msg.role()) ? "Người dùng" : "Trợ lý";
                historyText.append(roleLabel).append(": ").append(msg.content()).append("\n");
            }
        }
        return """
                /no_think
                Viết lại câu hỏi mới thành câu hỏi độc lập đầy đủ ngữ cảnh bằng tiếng Việt để tra cứu thông tin đào tạo UET.
                Chỉ trả về duy nhất câu hỏi đã viết lại, không thêm lời dẫn.
                Lịch sử:
                %s
                Câu hỏi mới: %s
                """.formatted(historyText.toString(), currentQuestion);
    }
}
