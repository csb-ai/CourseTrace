package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.VideoContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VideoMaterialNormalizerTest {
    @Test
    void removesDiagnosticOnlyAndClipsLastWindowWithoutChangingTranscriptOrFrames() {
        var context = new VideoContext("source", "goal", List.of(new VideoContext.VideoSegment(
                600000, 660000, "刚才的十个工具", List.of("Estimating resolution as 283", "Estimating resolution as 250\n一周学习"), List.of("frame.jpg"))));
        var normalized = VideoMaterialNormalizer.normalize(context, 640058);
        var last = normalized.segments().getFirst();
        assertEquals(640058, last.endMs());
        assertEquals("刚才的十个工具", last.transcript());
        assertEquals(List.of("一周学习"), last.ocrTexts());
        assertEquals(List.of("frame.jpg"), last.evidenceFrames());
        assertEquals(660000, context.segments().getFirst().endMs());
    }
}
