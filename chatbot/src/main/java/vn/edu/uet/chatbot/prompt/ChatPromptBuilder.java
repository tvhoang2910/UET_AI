package vn.edu.uet.chatbot.prompt;

import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.dto.ollama.Message;

import java.util.List;

@Component
public class ChatPromptBuilder {

    public String build(String context, String question, String noInfoAnswer) {
        return """
                /no_think
                Bạn là trợ lý học tập AI chuyên nghiệp của Trường Đại học Công nghệ (UET).
                Nhiệm vụ: Trả lời câu hỏi của sinh viên dựa trên tài liệu được cung cấp.

                ### TÀI LIỆU THAM KHẢO (Đã được đánh số [NGUỒN 1], [NGUỒN 2]... kèm số trang):
                ===TÀI LIỆU BẮT ĐẦU===
                %s
                ===TÀI LIỆU KẾT THÚC===

                ### NGUYÊN TẮC TRÍCH DẪN & CHỐNG BỊA ĐẶT (BẮT BUỘC TUÂN THỦ):
                1. MỌI câu khẳng định chứa thông tin, số liệu, mốc thời gian PHẢI có trích dẫn nguồn nội dòng ngay phía sau: [Nguồn X: Trang Y] (Ví dụ: "Học kỳ 1 bắt đầu từ ngày 08/09/2025 [Nguồn 1: Trang 1]").
                2. TUYỆT ĐỐI KHÔNG tự tạo ra số [Nguồn Z] nếu trong TÀI LIỆU THAM KHẢO không có.
                3. ĐÚNG NGUỒN ĐÚNG TRANG: Thông tin lấy từ đoạn của [NGUỒN 1] thì BẮT BUỘC ghi [Nguồn 1], không được ghi sang Nguồn khác.
                4. ĐỐI VỚI BẢNG MARKDOWN: Đọc đúng hàng và cột tương ứng để không nhầm lẫn giữa các khóa sinh viên hoặc các mốc thời gian.
                5. NẾU KHÔNG CÓ THÔNG TIN: Trả về CHÍNH XÁC: "%s". Không tự ý suy đoán ngoài tài liệu.
                6. Giới hạn: Tối đa 250 từ, trình bày gạch đầu dòng rõ ràng, bằng tiếng Việt.

                Câu hỏi của sinh viên: %s
                """
                .formatted(context, noInfoAnswer, question);
    }

    public String buildSystemPrompt(String context, String noInfoAnswer) {
        return """
                /no_think
                Bạn là trợ lý học tập AI chuyên nghiệp của Trường Đại học Công nghệ (UET).
                Nhiệm vụ: Trả lời câu hỏi của sinh viên dựa trên tài liệu được cung cấp.

                ### TÀI LIỆU THAM KHẢO (Đã được đánh số [NGUỒN 1], [NGUỒN 2]... kèm số trang):
                ===TÀI LIỆU BẮT ĐẦU===
                %s
                ===TÀI LIỆU KẾT THÚC===

                ### NGUYÊN TẮC TRÍCH DẪN & CHỐNG BỊA ĐẶT (BẮT BUỘC TUÂN THỦ):
                1. MỌI câu khẳng định chứa thông tin, số liệu, mốc thời gian PHẢI có trích dẫn nguồn nội dòng ngay phía sau: [Nguồn X: Trang Y] (Ví dụ: "Sinh hoạt đầu khóa diễn ra từ ngày 03/09/2025 đến 07/09/2025 [Nguồn 1: Trang 1]").
                2. TUYỆT ĐỐI KHÔNG tự tạo ra số [Nguồn Z] nếu trong TÀI LIỆU THAM KHẢO không có.
                3. ĐÚNG NGUỒN ĐÚNG TRANG: Thông tin lấy từ đoạn của [NGUỒN 1] thì BẮT BUỘC ghi [Nguồn 1], không được ghi sang Nguồn khác.
                4. ĐỐI VỚI BẢNG MARKDOWN: Đọc đúng hàng và cột tương ứng để trả lời chính xác số liệu, môn học, ngày thi.
                5. NẾU KHÔNG CÓ THÔNG TIN: Trả về CHÍNH XÁC: "%s". Không tự ý suy đoán ngoài tài liệu.
                6. Giới hạn: Tối đa 250 từ, trình bày gạch đầu dòng rõ ràng, bằng tiếng Việt.
                """
                .formatted(context, noInfoAnswer);
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
                Viết lại câu hỏi mới thành câu hỏi độc lập đầy đủ ngữ cảnh bằng tiếng Việt để tìm kiếm RAG.
                Chỉ trả về duy nhất câu hỏi đã viết lại, không thêm lời dẫn.
                Lịch sử:
                %s
                Câu hỏi mới: %s
                """.formatted(historyText.toString(), currentQuestion);
    }
}