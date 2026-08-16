package vn.edu.uet.chatbot.ingest.extractor;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class VietnameseTextCleaner {

    private static final Map<Pattern, String> REPAIR_PATTERNS = Map.ofEntries(
            Map.entry(Pattern.compile("(?i)\\bK[\\?]\\s*ho[\\?]ch\\b"), "Kế hoạch"),
            Map.entry(Pattern.compile("(?i)\\bH[\\?]c\\s*k[\\?]\\b"), "Học kỳ"),
            Map.entry(Pattern.compile("(?i)\\bH[\\?]c\\s*ph[\\?]n\\b"), "Học phần"),
            Map.entry(Pattern.compile("(?i)\\bH[\\?]c\\s*t[\\?]p\\b"), "Học tập"),
            Map.entry(Pattern.compile("(?i)\\bSinh\\s*vi[\\?]n\\b"), "Sinh viên"),
            Map.entry(Pattern.compile("(?i)\\bGi[\\?]ng\\s*vi[\\?]n\\b"), "Giảng viên"),
            Map.entry(Pattern.compile("(?i)\\b[\\?]i[\\?]u\\s*(\\d+)\\b"), "Điều $1"),
            Map.entry(Pattern.compile("(?i)\\bQuy\\s*[\\?][\\?]nh\\b"), "Quy định"),
            Map.entry(Pattern.compile("(?i)\\b[\\?][\\?]i\\s*h[\\?]c\\b"), "Đại học"),
            Map.entry(Pattern.compile("(?i)\\bTh[\\?]i\\s*gian\\b"), "Thời gian"),
            Map.entry(Pattern.compile("(?i)\\bC[\\?]ng\\s*ngh[\\?]\\b"), "Công nghệ"),
            Map.entry(Pattern.compile("(?i)\\bTh[\\?]ng\\s*b[\\?]o\\b"), "Thông báo"),
            Map.entry(Pattern.compile("(?i)\\bK[\\?]t\\s*qu[\\?]\\b"), "Kết quả"),
            Map.entry(Pattern.compile("(?i)\\b[\\?][\\?]ng\\s*k[\\?]\\b"), "Đăng ký"),
            Map.entry(Pattern.compile("(?i)\\bT[\\?]n\\s*ch[\\?]\\b"), "Tín chỉ"));

    public String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = Normalizer.normalize(text, Normalizer.Form.NFC);
        result = result.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        for (Map.Entry<Pattern, String> entry : REPAIR_PATTERNS.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }

        return result.trim();
    }
}
