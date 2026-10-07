package com.coursetrace.backend.utils;

import com.coursetrace.backend.dto.AnalysisResult;
import com.coursetrace.backend.dto.VideoContext;
import com.coursetrace.backend.service.EvidenceVerificationService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceCatalogTest {
    @Test void resolvesOnlyExactIdsAndKeepsOriginalWindowAndWords() {
        var context = new VideoContext("video", "goal", List.of(
                new VideoContext.VideoSegment(60000, 120000, "合上书，先回忆。想不起来再查。", List.of("主动回忆"), List.of())));
        var catalog = new EvidenceCatalog(context);
        var result = new AnalysisResult("title", List.of("先回忆再查书"), List.of(
                new AnalysisResult.Evidence(73000, "OCR", "E1_1", "先回忆再查书"),
                new AnalysisResult.Evidence(60000, "ASR", "E999_1", "先回忆再查书")), List.of(), List.of());
        var resolved = catalog.resolve(result);
        assertThat(resolved.evidence().getFirst().content()).isEqualTo("合上书，先回忆。");
        assertThat(resolved.evidence().getFirst().timestampMs()).isEqualTo(60000);
        assertThat(resolved.evidence().getFirst().source()).isEqualTo("ASR");
        assertThat(new EvidenceVerificationService().supported(context, resolved.evidence().getFirst())).isTrue();
        assertThat(new EvidenceVerificationService().supported(context, resolved.evidence().get(1))).isFalse();
        var quoted = catalog.resolve(new AnalysisResult("局部追问", List.of("E1_1"),
                List.of(new AnalysisResult.Evidence(0, "OCR", "E1_1", "E1_1")), List.of("建议：合书复述。"), List.of()));
        assertThat(quoted.conclusions()).containsExactly("合上书，先回忆。");
        assertThat(new EvidenceVerificationService().supportsClaim(context,
                quoted.conclusions().getFirst(), quoted.evidence().getFirst())).isTrue();
    }
}
