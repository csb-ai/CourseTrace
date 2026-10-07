package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisResult;
import com.coursetrace.backend.dto.VideoContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceVerificationServiceTest {

    private final EvidenceVerificationService service = new EvidenceVerificationService();
    private final VideoContext context = new VideoContext(
            "lesson.mp4",
            "总结课程",
            List.of(new VideoContext.VideoSegment(
                    120_000,
                    180_000,
                    "接下来讲解二叉树的前序遍历",
                    List.of("前序遍历：根节点、左子树、右子树"),
                    List.of("frame_000125.jpg"))));

    @Test
    void acceptsVerbatimEvidenceAtTheDeclaredTimestamp() {
        AnalysisResult.Evidence evidence = new AnalysisResult.Evidence(
                125_000, "OCR", "根节点、左子树、右子树", "前序遍历顺序");

        assertTrue(service.supported(context, evidence));
        assertTrue(service.supportsClaim(context, "前序遍历顺序", evidence));
    }

    @Test
    void rejectsTextThatOnlyLooksSimilarToTheSource() {
        AnalysisResult.Evidence evidence = new AnalysisResult.Evidence(
                125_000, "OCR", "根节点左子树不存在，因此应跳过", "前序遍历顺序");

        assertFalse(service.supported(context, evidence));
    }

    @Test void rejectsEllipsisEvenWhenRemovingPunctuationWouldMatch() {
        assertFalse(service.supported(context, new AnalysisResult.Evidence(
                125_000, "OCR", "根节点...左子树...右子树", "顺序")));
    }

    @Test void rejectsUnrecognizedSourcesAndRewrittenClaimBinding() {
        assertFalse(service.supported(context, new AnalysisResult.Evidence(
                125_000, "NOT_ASR", "前序遍历", "顺序")));
        assertFalse(service.supportsClaim(context, "顺序：根左右", new AnalysisResult.Evidence(
                125_000, "OCR", "前序遍历", "顺序根左右")));
    }

    @Test void reproducesRealFollowUpCitationFailureWithoutCallingModel() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (var input = getClass().getResourceAsStream("/cases/evidence-quote-real.json")) {
            var fixture = mapper.readTree(input);
            VideoContext realContext = mapper.treeToValue(fixture.get("context"), VideoContext.class);
            assertFalse(service.supported(realContext,
                    mapper.treeToValue(fixture.get("rejected"), AnalysisResult.Evidence.class)));
            assertTrue(service.supported(realContext,
                    mapper.treeToValue(fixture.get("accepted"), AnalysisResult.Evidence.class)));
        }
    }
}
