package vn.edu.uet.chatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.uet.chatbot.store.dto.ScoredDocumentChunk;
import vn.edu.uet.chatbot.util.CourseCodeUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PrerequisiteTracker {

    private static final Pattern PREREQ_PATTERN = Pattern.compile(
            "(?i)(?:tiên quyết|học trước|song hành)[^:\\n]*:\\s*([^\\.\\n]+)");

    public String extractPrerequisiteChainSummary(String query, List<ScoredDocumentChunk> chunks) {
        Set<String> queryCodes = CourseCodeUtils.extractCourseCodes(query);
        if (queryCodes.isEmpty()) {
            return "";
        }

        StringBuilder chainInfo = new StringBuilder();
        for (ScoredDocumentChunk scored : chunks) {
            String content = scored.chunk().content();
            if (content == null) {
                continue;
            }

            Matcher matcher = PREREQ_PATTERN.matcher(content);
            while (matcher.find()) {
                String rawPrereqText = matcher.group(1);
                Set<String> prereqCodes = CourseCodeUtils.extractCourseCodes(rawPrereqText);
                if (!prereqCodes.isEmpty()) {
                    for (String targetCode : queryCodes) {
                        if (content.toUpperCase().contains(targetCode)) {
                            chainInfo.append("- Học phần **")
                                    .append(targetCode)
                                    .append("** yêu cầu học trước/tiên quyết: ")
                                    .append(String.join(", ", prereqCodes))
                                    .append("\n");
                        }
                    }
                }
            }
        }

        if (!chainInfo.isEmpty()) {
            return "\n### THÔNG TIN MÔN HỌC TIÊN QUYẾT PHÁT HIỆN TỪ ĐỀ CƯƠNG:\n" + chainInfo + "\n";
        }

        return "";
    }
}
