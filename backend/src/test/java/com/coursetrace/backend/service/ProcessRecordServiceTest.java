package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisMode;
import com.coursetrace.backend.dto.TaskEvent;
import com.coursetrace.backend.dto.TaskStage;
import com.coursetrace.backend.dto.TaskStatus;
import com.coursetrace.backend.mapper.AnalysisProcessRecordMapper;
import com.coursetrace.backend.utils.AnalysisTaskKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessRecordServiceTest {
    @Test
    void queryScopesByMediaGoalAndMode() {
        AnalysisProcessRecordMapper mapper = mock(AnalysisProcessRecordMapper.class);
        ProcessRecordService records = new ProcessRecordService(mapper);
        when(mapper.list(anyLong(), anyString(), anyString())).thenReturn(List.of());

        records.list(7L, "goal", AnalysisMode.LEARNING);
        records.list(7L, "another goal", AnalysisMode.LEARNING);
        records.list(7L, "goal", AnalysisMode.GENERAL);

        verify(mapper).list(7L, AnalysisTaskKeys.goalDigest("goal", AnalysisMode.LEARNING), "LEARNING");
        verify(mapper).list(7L, AnalysisTaskKeys.goalDigest("another goal", AnalysisMode.LEARNING), "LEARNING");
        verify(mapper).list(7L, AnalysisTaskKeys.goalDigest("goal", AnalysisMode.GENERAL), "GENERAL");
        assertNotEquals(AnalysisTaskKeys.goalDigest("goal", AnalysisMode.LEARNING),
                AnalysisTaskKeys.goalDigest("goal", AnalysisMode.GENERAL));
    }

    @Test
    void publishedStageAndReuseEachPersistExactlyOneRealEventWithoutTheFullResult() {
        AnalysisProcessRecordMapper mapper = mock(AnalysisProcessRecordMapper.class);
        ProcessRecordService records = new ProcessRecordService(mapper);
        TaskEventService events = new TaskEventService(mock(StringRedisTemplate.class), new ObjectMapper(), records);

        events.publishAnalysis(7L, "goal", AnalysisMode.LEARNING,
                new TaskStatus(TaskStatus.State.PROCESSING, "full report must not be stored", "准备执行"),
                TaskStage.EXECUTOR_STARTED);
        assertTrue(records.recordReuse(7L, "goal", AnalysisMode.LEARNING));

        verify(mapper).insert(7L, AnalysisTaskKeys.goalDigest("goal", AnalysisMode.LEARNING),
                "LEARNING", "PROCESSING", "EXECUTOR_STARTED", "准备执行");
        verify(mapper).insert(eq(7L), anyString(), eq("LEARNING"), eq("COMPLETED"),
                eq("COMPLETED_REUSED"), contains("未重新调用模型"));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void failedPersistenceDoesNotStopLivePublicationAndIsNotReportedAsSaved() {
        AnalysisProcessRecordMapper mapper = mock(AnalysisProcessRecordMapper.class);
        doThrow(new IllegalStateException("storage unavailable")).when(mapper)
                .insert(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
        ProcessRecordService records = new ProcessRecordService(mapper);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TaskEventService events = new TaskEventService(redis, new ObjectMapper(), records);
        assertDoesNotThrow(() -> events.publishAnalysis(7L, "goal", AnalysisMode.LEARNING,
                TaskStatus.completed("report"), TaskStage.COMPLETED));
        verify(redis).convertAndSend(eq(TaskEventService.REDIS_CHANNEL), anyString());
        assertFalse(records.recordReuse(7L, "goal", AnalysisMode.LEARNING));
    }

    @Test
    void onlyBoundedRedactedStatusMessageIsStored() {
        AnalysisProcessRecordMapper mapper = mock(AnalysisProcessRecordMapper.class);
        ProcessRecordService records = new ProcessRecordService(mapper);
        String fakeKey = "sk-" + "synthetic".repeat(4);
        records.record(7L, "goal", null, new TaskEvent(TaskStatus.State.FAILED, "do not store",
                fakeKey + " Bearer private-token " + "x".repeat(1200), TaskStage.FAILED));
        verify(mapper).insert(eq(7L), anyString(), eq("GENERAL"), eq("FAILED"), eq("FAILED"),
                argThat(message -> message.length() == 1000 && !message.contains("private-token")
                        && !message.contains(fakeKey)));
    }
}
