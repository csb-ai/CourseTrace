package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.VideoContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LongVideoContextServiceTest {
    @Test void completeSummaryKeepsAllSeventeenMinutesWhenWithinBudget() {
        var telemetry=mock(AgentTelemetry.class);
        var checkpoint=mock(AgentCheckpointService.class);
        var chunks=mock(VideoChunkingService.class);
        var retrieval=mock(VideoEvidenceRetrievalService.class);
        when(chunks.build(anyList())).thenReturn(List.of());
        var service=new LongVideoContextService(telemetry,checkpoint,chunks,retrieval);
        var segments=IntStream.range(0,17).mapToObj(i -> new VideoContext.VideoSegment(
                i*60000L,(i+1)*60000L,"第"+i+"段原文",List.of(),List.of())).toList();
        var result=service.selectRelevant(new VideoContext("source","完整总结这节课程的核心方法",segments));
        assertEquals(segments,result.segments());
        verify(retrieval,never()).retrieve(any(),anyString(),anyList());
        verify(telemetry).incrementCurrent("fullCoverageSegments",17);
    }
}
