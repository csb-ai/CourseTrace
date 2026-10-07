package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisMode;
import com.coursetrace.backend.dto.AnalysisResult;
import com.coursetrace.backend.service.mode.ModeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GoalOutputRequirementsTest {
    private final ModeRegistry registry = new ModeRegistry();

    @Test void goalAddsWeekToExistingLearningTemplate() {
        var profile = GoalOutputRequirements.forGoal("总结并给初学者一周行动清单", registry.of(AnalysisMode.LEARNING));
        assertTrue(profile.requiredSectionKeys().containsAll(List.of("outline", "keypoints", "quiz", "pitfalls", "weekly_plan")));
        assertTrue(GoalOutputRequirements.problems(result("建议", 6), profile).size() > 0);
        assertTrue(GoalOutputRequirements.problems(result("一周行动计划（建议）", 7), profile).isEmpty());
    }

    @Test void rejectsUnlabeledRecommendationsAndRepeatedDays() {
        var profile = GoalOutputRequirements.forGoal("7天学习计划", registry.of(AnalysisMode.GENERAL));
        assertFalse(GoalOutputRequirements.problems(result("计划", 7), profile).isEmpty());
        var repeated = new AnalysisResult("", List.of(), List.of(), List.of(), List.of(
                new AnalysisResult.Section("weekly_plan", "建议", java.util.Collections.nCopies(7, "第1天：复习并自测"))));
        assertFalse(GoalOutputRequirements.problems(repeated, profile).isEmpty());
    }

    @Test void backgroundGoalDoesNotLeakIntoCurrentQuestion() {
        var base = registry.of(AnalysisMode.GENERAL);
        assertSame(base, GoalOutputRequirements.forGoal("原始目标：一周学习计划\n当前追问：为什么反复看书没用？", base));
        assertTrue(GoalOutputRequirements.forGoal("原始目标：总结\n当前追问：给我一周行动计划", base)
                .requiredSectionKeys().contains("weekly_plan"));
    }

    @Test void ordinaryWeeklyDescriptionDoesNotForceActionPlan() {
        var base = registry.of(AnalysisMode.LEARNING);
        assertSame(base, GoalOutputRequirements.forGoal("视频中一周会发生什么？", base));
    }

    private AnalysisResult result(String title, int days) {
        return new AnalysisResult("分析", List.of(), List.of(), List.of(), List.of(new AnalysisResult.Section(
                "weekly_plan", title, IntStream.rangeClosed(1, days)
                .mapToObj(day -> "第" + day + "天：闭卷回忆一个知识点，答对自测题后记录结果").toList())));
    }
}
