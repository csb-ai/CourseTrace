package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class AgentPlanNormalizerTest {
    @Test
    void capturedRealPlannerFailureFitsBoundsWithoutLosingAnyTask() throws Exception {
        try (var input = getClass().getResourceAsStream("/cases/planner-overflow.json")) {
            assertNotNull(input);
            AgentState.AgentPlan raw = new ObjectMapper().readValue(input, AgentState.AgentPlan.class);
            assertEquals(9, raw.tasks().size());
            AgentState.AgentPlan normalized = AgentPlanNormalizer.compactOverflow(raw);
            assertEquals(5, normalized.tasks().size());
            assertTrue(normalized.tasks().stream().allMatch(task -> !task.isBlank() && task.length() <= 500));
            assertEquals(String.join("\n", raw.tasks()), String.join("\n", normalized.tasks()));
        }
    }

    @Test
    void nineTasksKeepAllContentAndOrderInAtMostFiveGroups() {
        List<String> tasks = IntStream.rangeClosed(1, 9).mapToObj(i -> "独立原任务" + i).toList();
        AgentState.AgentPlan raw = new AgentState.AgentPlan("覆盖完整课程", tasks);
        AgentState.AgentPlan normalized = AgentPlanNormalizer.compactOverflow(raw);
        assertTrue(normalized.tasks().size() <= 5);
        assertEquals(String.join("\n", tasks), String.join("\n", normalized.tasks()));
        assertEquals(raw.understoodGoal(), normalized.understoodGoal());
    }

    @Test
    void aValidSmallPlanIsNotRewritten() {
        AgentState.AgentPlan plan = new AgentState.AgentPlan("目标", List.of("先提取", "再总结"));
        assertSame(plan, AgentPlanNormalizer.compactOverflow(plan));
    }

    @Test
    void structuralErrorsAreNotHiddenByCompaction() {
        AgentState.AgentPlan blankGoal = new AgentState.AgentPlan("", List.of("1", "2", "3", "4", "5", "6"));
        AgentState.AgentPlan blankTask = new AgentState.AgentPlan("目标", List.of("1", "2", " ", "4", "5", "6"));
        assertSame(blankGoal, AgentPlanNormalizer.compactOverflow(blankGoal));
        assertSame(blankTask, AgentPlanNormalizer.compactOverflow(blankTask));
    }

    @Test
    void excessiveUnboundedOutputRemainsInvalid() {
        AgentState.AgentPlan plan = new AgentState.AgentPlan("目标", IntStream.range(0, 21).mapToObj(String::valueOf).toList());
        assertSame(plan, AgentPlanNormalizer.compactOverflow(plan));
    }
}
