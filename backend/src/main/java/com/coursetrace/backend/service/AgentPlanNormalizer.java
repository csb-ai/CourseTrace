package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AgentState;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型修复后仍输出过多任务时，将相邻任务分组，完整保留每一项原文与顺序。
 * 这里只处理数量溢出；不补造目标、不丢弃任务，也不放行空任务或异常大的列表。
 */
final class AgentPlanNormalizer {
    static final int MAX_GROUPS = 5;
    static final int MAX_RAW_TASKS = 20;

    private AgentPlanNormalizer() { }

    static AgentState.AgentPlan compactOverflow(AgentState.AgentPlan plan) {
        if (plan == null || plan.understoodGoal().isBlank()
                || plan.tasks().size() <= MAX_GROUPS || plan.tasks().size() > MAX_RAW_TASKS
                || plan.tasks().stream().anyMatch(task -> task == null || task.isBlank())) {
            return plan;
        }
        int groupSize = (plan.tasks().size() + MAX_GROUPS - 1) / MAX_GROUPS;
        List<String> groups = new ArrayList<>();
        for (int offset = 0; offset < plan.tasks().size(); offset += groupSize) {
            int end = Math.min(offset + groupSize, plan.tasks().size());
            groups.add(String.join("\n", plan.tasks().subList(offset, end)));
        }
        return new AgentState.AgentPlan(plan.understoodGoal(), groups);
    }
}
