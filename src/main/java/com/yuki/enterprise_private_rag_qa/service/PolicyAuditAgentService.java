package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * First-pass policy audit agent.
 *
 * This version is intentionally rule-based so policy admission can run offline
 * and remain deterministic. Later phases can add LLM review as a second opinion.
 */
@Service
public class PolicyAuditAgentService {

    private static final int MISSING_REQUIRED_PENALTY = 15;

    public AuditResult audit(String text) {
        String content = text == null ? "" : text.trim();
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        check(content, issues, suggestions, "适用范围", "补充制度适用的人群、部门或岗位。", "适用范围", "适用对象", "适用于");
        check(content, issues, suggestions, "责任部门", "补充制度归口部门或责任部门。", "责任部门", "归口部门", "负责部门");
        check(content, issues, suggestions, "生效时间", "补充制度生效时间，避免新旧制度边界不清。", "生效时间", "生效日期", "自", "起施行");
        check(content, issues, suggestions, "审批人", "补充审批人、批准人或审批机构。", "审批人", "批准人", "审批机构", "经");
        check(content, issues, suggestions, "版本", "补充版本号或修订记录，便于制度沿革管理。", "版本", "版本号", "V1", "v1", "修订");
        check(content, issues, suggestions, "流程闭环", "补充提交、审批、归档、驳回或异常处理说明。", "提交", "审批", "归档", "驳回", "异常处理", "重新提交");
        checkAmbiguity(content, issues, suggestions);
        checkDuplicateParagraphs(content, issues, suggestions);
        checkConflictingSignals(content, issues, suggestions);

        int score = Math.max(0, 100 - issues.size() * MISSING_REQUIRED_PENALTY);
        FileUpload.PolicyAuditStatus status = statusFor(score, issues.size());
        String summary = buildSummary(status, score, issues);

        return new AuditResult(status, score, summary, List.copyOf(issues), List.copyOf(suggestions));
    }

    private void checkAmbiguity(String content, List<String> issues, List<String> suggestions) {
        List<String> ambiguous = Arrays.stream(new String[] {
                        "原则上", "视情况", "相关人员", "有关部门", "适当", "及时", "尽快", "必要时", "一般情况下"
                })
                .filter(content::contains)
                .toList();
        if (!ambiguous.isEmpty()) {
            issues.add("存在模糊表述：" + String.join("、", ambiguous));
            suggestions.add("将模糊表述改为明确责任人、时限、条件或审批口径。");
        }
    }

    private void checkDuplicateParagraphs(String content, List<String> issues, List<String> suggestions) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicated = new HashSet<>();
        for (String paragraph : content.split("\\R+")) {
            String normalized = paragraph.replaceAll("\\s+", "");
            if (normalized.length() < 40) {
                continue;
            }
            String key = normalized.substring(0, Math.min(80, normalized.length()));
            if (!seen.add(key)) {
                duplicated.add(paragraph.trim());
            }
        }
        if (!duplicated.isEmpty()) {
            issues.add("存在疑似重复段落");
            suggestions.add("合并重复或高度相似条款，避免同一事项出现多个口径。");
        }
    }

    private void checkConflictingSignals(String content, List<String> issues, List<String> suggestions) {
        boolean mandatory = content.contains("必须") || content.contains("不得") || content.contains("严禁");
        boolean discretionary = content.contains("可自行") || content.contains("自行决定") || content.contains("无需审批");
        if (mandatory && discretionary) {
            issues.add("存在疑似强制要求与自由裁量冲突");
            suggestions.add("明确哪些事项必须执行，哪些事项可以自行决定，并给出边界条件。");
        }
        boolean active = content.contains("生效") || content.contains("执行");
        boolean abolished = content.contains("废止") || content.contains("失效");
        if (active && abolished && !content.contains("替代") && !content.contains("原制度")) {
            issues.add("存在生效与废止边界不清");
            suggestions.add("补充新旧制度替代关系、废止对象和生效日期。");
        }
    }

    private void check(String content, List<String> issues, List<String> suggestions,
                       String dimension, String suggestion, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return;
            }
        }
        issues.add("缺少" + dimension);
        suggestions.add(suggestion);
    }

    private FileUpload.PolicyAuditStatus statusFor(int score, int issueCount) {
        if (issueCount == 0 && score >= 90) {
            return FileUpload.PolicyAuditStatus.PASS;
        }
        if (score >= 70) {
            return FileUpload.PolicyAuditStatus.PASS_WITH_WARNINGS;
        }
        if (score >= 25) {
            return FileUpload.PolicyAuditStatus.NEED_MANUAL_REVIEW;
        }
        return FileUpload.PolicyAuditStatus.REJECT;
    }

    private String buildSummary(FileUpload.PolicyAuditStatus status, int score, List<String> issues) {
        if (issues.isEmpty()) {
            return "制度结构完整，基础审计通过，评分 " + score + "。";
        }
        return switch (status) {
            case PASS_WITH_WARNINGS -> "制度基本可用，但存在需补充项：" + String.join("、", issues) + "。评分 " + score + "。";
            case NEED_MANUAL_REVIEW -> "制度存在关键缺失，建议人工复核：" + String.join("、", issues) + "。评分 " + score + "。";
            case REJECT -> "制度缺失较多，暂不建议纳入知识库：" + String.join("、", issues) + "。评分 " + score + "。";
            default -> "制度审计完成，评分 " + score + "。";
        };
    }

    public record AuditResult(
            FileUpload.PolicyAuditStatus status,
            int score,
            String summary,
            List<String> issues,
            List<String> suggestions
    ) {
    }
}
