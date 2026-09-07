package dk.school.workoverviewagent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReviewToolsTest {

    @Autowired
    private ReviewTools reviewTools;

    @Test
    void shouldReturnMockedReviewDataThroughMcpTool() {
        var response = reviewTools.getReview(
                "2026-09-04T00:00:00Z",
                "2026-09-04T23:59:59Z");

        assertThat(response.items())
                .extracting("title")
                .containsExactly(
                        "Test environment clarification",
                        "Decision needed: integration approach",
                        "Project Alpha status review",
                        "Project Alpha status review notes",
                        "API contract follow-up");
    }

    @Test
    void shouldReviewAllSourcesByDefault() {
        var response = reviewTools.getReview(
                "2026-09-07T00:00:00Z",
                "2026-09-07T23:59:59Z");

        assertThat(response.request().sources()).isEmpty();
        assertThat(response.items())
                .extracting("title")
                .containsExactly("Prototype demo preparation");
    }
}
