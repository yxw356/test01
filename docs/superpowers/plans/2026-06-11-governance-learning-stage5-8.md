# Governance Learning Stage 5-8 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first governed learning loop for 龙汇QA: department case maintenance, case-to-policy drafting, stronger policy audit, and graph-aware assistant context.

**Architecture:** Reuse the existing FAQ and term governance module. Add a bounded `KnowledgeCase` module that is permission-scoped like FAQ/terms, expose it through the existing governance configuration dialog, and feed approved cases into chat context and topology graph as auxiliary sources.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, Vue 3, TypeScript, Naive UI, ECharts graph, DeepSeek-compatible RAG prompt chain.

---

## Phase Scope

| Phase | Name | First Implementation |
| --- | --- | --- |
| 5 | Q&A and case maintenance | Department leads maintain FAQ and cases; cases have scenario, handling, conclusion, status |
| 6 | Case-to-policy | System groups enough approved cases and generates a policy draft for human review |
| 7 | Policy audit strengthening | Rule-based audit checks ambiguity, duplicate-looking content, missing boundaries, conflicting phrases |
| 8 | Graph-aware assistant | Chat context includes matching terms, FAQ, cases, and document evidence; graph includes FAQ/case relation nodes |

## Task 1: Knowledge Case Backend

- [ ] Add `KnowledgeCase` entity with title, scenario, handling, conclusion, tags, status, scope, department, createdBy.
- [ ] Add `KnowledgeCaseRepository`.
- [ ] Add `KnowledgeCaseService` with create, update, delete, list manageable, list visible, matched-case context, policy draft.
- [ ] Add case endpoints under `/api/v1/knowledge-assistant/cases`.

## Task 2: Case-To-Policy Draft

- [ ] Add `/api/v1/knowledge-assistant/cases/policy-draft` endpoint.
- [ ] Require at least 3 approved or enabled cases in the selected scope.
- [ ] Generate deterministic draft sections: purpose, scope, common scenarios, process, exceptions, risk controls, source cases.
- [ ] Do not auto-publish draft into the knowledge base.

## Task 3: Policy Audit Enhancement

- [ ] Extend `PolicyAuditAgentService` with ambiguity keywords.
- [ ] Detect repeated paragraphs and likely duplicate content.
- [ ] Detect conflicting policy signals such as "必须" and "可自行" in the same short context.
- [ ] Add suggestions that tell the uploader how to fix ambiguity and conflict.

## Task 4: Graph-Aware Assistant Context

- [ ] Add matched case context to `ChatHandler`.
- [ ] Keep cases as auxiliary evidence, clearly marked as "案例参考".
- [ ] Ensure document evidence still has priority for制度/流程/金额/时间 answers.
- [ ] Keep FAQ exact match as first priority.

## Task 5: Knowledge Graph Extension

- [ ] Add FAQ and case nodes to topology response when visible to the current user.
- [ ] Link FAQ/case nodes to department and related files by department/category/tag.
- [ ] Use distinct node type and color so Obsidian-style graph shows documents, FAQ, and cases.

## Task 6: Frontend Case Governance

- [ ] Add "案例库" tab to governance dialog.
- [ ] Department leads can create, edit, enable/disable, delete department cases.
- [ ] Super administrators can manage all visible cases.
- [ ] Add policy draft generation panel and copyable draft text.

## Verification

- [ ] Run backend build with `mvn -DskipTests package`.
- [ ] Run frontend build with `node_modules/.bin/vite build --mode test`.
- [ ] Restart backend LaunchAgent.
- [ ] Verify authenticated users can open governance config and manage cases according to role.
