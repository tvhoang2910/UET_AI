package vn.edu.uet.chatbot.router;

import org.junit.jupiter.api.Test;
import vn.edu.uet.chatbot.ingest.model.DocumentCategory;

import static org.assertj.core.api.Assertions.assertThat;

class QueryCategoryRouterTest {

    private final QueryCategoryRouter router = new QueryCategoryRouter();

    @Test
    void should_detect_course_category_for_course_questions() {
        assertThat(router.detectCategory("Môn INT2204 tiên quyết môn nào?")).contains(DocumentCategory.MON_HOC);
    }

    @Test
    void should_detect_regulation_category_for_policy_questions() {
        assertThat(router.detectCategory("Quy chế học vụ có điều khoản nghỉ học không?"))
                .contains(DocumentCategory.QUY_CHE);
    }
}
