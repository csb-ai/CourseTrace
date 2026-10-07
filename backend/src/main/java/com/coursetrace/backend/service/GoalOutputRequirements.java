package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisMode;
import com.coursetrace.backend.dto.AnalysisResult;
import com.coursetrace.backend.service.mode.ModeProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 用户明确要求的交付物优先于模式模板；程序检查结构，Critic 检查实际内容。 */
final class GoalOutputRequirements {
    private static final Pattern WEEK = Pattern.compile("一周|七天|7\\s*天|7[- ]day|one[- ]week", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAN = Pattern.compile("计划|清单|安排|行动|plan|schedule|checklist", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY = Pattern.compile("^(?:建议[：:]?\\s*)?第([1-7一二三四五六七])天[：:、，.\\s].+", Pattern.DOTALL);

    private GoalOutputRequirements() { }

    static ModeProfile forGoal(String goal, ModeProfile base) {
        // 追问携带历史目标，但历史目标不是本次需要重新完成的任务。
        String currentGoal = currentGoal(goal);
        if (!WEEK.matcher(currentGoal).find() || !PLAN.matcher(currentGoal).find()) return base;
        ModeProfile profile = base == null
                ? new ModeProfile(AnalysisMode.GENERAL, "通用分析", "", "", "", List.of()) : base;
        List<String> keys = new ArrayList<>(profile.requiredSectionKeys());
        if (!keys.contains("weekly_plan")) keys.add("weekly_plan");
        String instruction = " 用户要求一周行动计划：必须额外提供key=weekly_plan，title=一周行动计划（建议），"
                + "items恰好7条，依次以第1天：至第7天：开头。每条说明一个具体动作和验收方式；"
                + "每天的安排是基于视频方法的应用建议，不是视频原话，必须明确标注建议。";
        return new ModeProfile(profile.mode(), profile.displayName(),
                profile.planInstruction() + instruction, profile.executeInstruction() + instruction,
                profile.criticInstruction() + " 必须检查weekly_plan七天都存在且有动作、验收方式；不能用通用suggestions代替。",
                keys);
    }

    static String currentGoal(String goal) {
        if (goal == null) return "";
        int question = goal.lastIndexOf("当前追问：");
        return question < 0 ? goal : goal.substring(question + "当前追问：".length());
    }

    static List<String> problems(AnalysisResult result, ModeProfile profile) {
        if (profile == null || !profile.requiredSectionKeys().contains("weekly_plan")) return List.of();
        List<AnalysisResult.Section> weeks = result == null ? List.of() : result.sections().stream()
                .filter(section -> "weekly_plan".equals(section.key())).toList();
        if (weeks.size() != 1) return List.of("一周计划必须有且只有一个weekly_plan段落");
        List<String> items = weeks.getFirst().items();
        if (items.size() != 7) return List.of("weekly_plan必须恰好包含第1天至第7天的7条行动，不能省略天数");
        for (int day = 1; day <= 7; day++) {
            var matcher = DAY.matcher(items.get(day - 1).trim());
            if (!matcher.matches() || !matcher.group(1).equals(String.valueOf(day))) {
                return List.of("weekly_plan按第1天：至第7天：排列，每天必须包含非空行动内容");
            }
        }
        if (!weeks.getFirst().title().contains("建议")) return List.of("一周安排是应用建议，weekly_plan标题必须明确标注建议");
        return List.of();
    }
}
