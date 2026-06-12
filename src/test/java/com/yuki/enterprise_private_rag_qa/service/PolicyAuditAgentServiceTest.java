package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAuditAgentServiceTest {

    private final PolicyAuditAgentService service = new PolicyAuditAgentService();

    @Test
    void completePolicyPasses() {
        String text = """
                适用范围：全体员工
                责任部门：人事行政部
                生效时间：2026-01-01
                审批人：总经理
                版本号：V1.0
                流程：员工提交申请，部门负责人审批，通过后归档；驳回后重新提交，异常处理由人事行政部协调。
                """;

        PolicyAuditAgentService.AuditResult result = service.audit(text);

        assertEquals(FileUpload.PolicyAuditStatus.PASS, result.status());
        assertTrue(result.score() >= 90);
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void missingLifecycleRequiresManualReview() {
        String text = "员工请假由部门负责人审批，审批后执行。";

        PolicyAuditAgentService.AuditResult result = service.audit(text);

        assertEquals(FileUpload.PolicyAuditStatus.NEED_MANUAL_REVIEW, result.status());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("生效时间")));
    }

    @Test
    void severelyIncompletePolicyIsRejected() {
        String text = "请假找领导。";

        PolicyAuditAgentService.AuditResult result = service.audit(text);

        assertEquals(FileUpload.PolicyAuditStatus.REJECT, result.status());
        assertTrue(result.score() < 50);
    }

    @Test
    void partialPolicyPassesWithWarnings() {
        String text = """
                适用范围：财务部
                责任部门：财务部
                生效时间：2026-03-01
                审批人：财务负责人
                员工提交报销申请后由财务审批。
                """;

        PolicyAuditAgentService.AuditResult result = service.audit(text);

        assertEquals(FileUpload.PolicyAuditStatus.PASS_WITH_WARNINGS, result.status());
        assertTrue(result.score() >= 70);
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("版本")));
    }
}
