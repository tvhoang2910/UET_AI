package vn.edu.uet.chatbot.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CourseCodeUtils {

    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile(
            "\\b(INT|MAT|PHY|CHE|BIO|ENG|EMA|EPN|ELT|MES|GEO|HIS|PHI|POL|LAW|MGT|ECO|PES)\\s*(\\d{4}[A-Z]?)\\b",
            Pattern.CASE_INSENSITIVE);

    private CourseCodeUtils() {
    }

    public static Set<String> extractCourseCodes(String text) {
        Set<String> codes = new HashSet<>();
        if (text == null || text.isBlank()) {
            return codes;
        }

        Matcher matcher = COURSE_CODE_PATTERN.matcher(text);
        while (matcher.find()) {
            codes.add(matcher.group(1).toUpperCase() + matcher.group(2).toUpperCase());
        }
        return codes;
    }
}
