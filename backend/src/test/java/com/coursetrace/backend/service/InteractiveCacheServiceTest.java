package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisMode;
import com.coursetrace.backend.mapper.InteractiveCacheMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InteractiveCacheServiceTest {
    @Test
    void reusesExactStandaloneQuestionButSeparatesGoalModeAndMedia() {
        var mapper = mock(InteractiveCacheMapper.class);
        Map<String,String> rows = new HashMap<>();
        doAnswer(c -> { rows.put(c.getArgument(0)+":"+c.getArgument(1)+":"+c.getArgument(2), c.getArgument(3)); return null; })
                .when(mapper).save(anyLong(), anyString(), anyString(), anyString());
        when(mapper.find(anyLong(), anyString(), anyString())).thenAnswer(c -> rows.get(c.getArgument(0)+":"+c.getArgument(1)+":"+c.getArgument(2)));
        var cache = new InteractiveCacheService(mapper, new ObjectMapper());
        String question = "视频里为什么反复看笔记会让人误以为自己学会了？";
        cache.saveAnswer(1L,"goal",AnalysisMode.LEARNING,question,"verified answer");
        assertEquals("verified answer", cache.answer(1L,"goal",AnalysisMode.LEARNING,question));
        assertNull(cache.answer(2L,"goal",AnalysisMode.LEARNING,question));
        assertNull(cache.answer(1L,"other",AnalysisMode.LEARNING,question));
        assertNull(cache.answer(1L,"goal",AnalysisMode.GENERAL,question));
        cache.saveAnswer(1L,"goal",AnalysisMode.LEARNING,"刚才那个方法为什么比另外一种方法好？","wrong context");
        assertNull(cache.answer(1L,"goal",AnalysisMode.LEARNING,"刚才那个方法为什么比另外一种方法好？"));
    }
}
