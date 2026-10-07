package com.coursetrace.backend.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FollowUpPromptTest {
    @Test void standaloneQuestionHasNoPreviousReportAndRetainsExactCurrentQuestion() {
        String question = "视频如何解释主动回忆？请给一个今天能做的练习。";
        String prompt = AiService.contextualQuestion(null, null, question, List.of(), null);
        assertThat(prompt).doesNotContain("原始目标：", "已有分析：", "历史对话：");
        assertThat(GoalOutputRequirements.currentGoal(prompt)).isEqualTo(question);
        assertThat(prompt).contains("不补写外部机制", "suggestions");
    }
}
