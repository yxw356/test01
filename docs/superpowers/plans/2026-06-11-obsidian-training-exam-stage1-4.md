# Obsidian Knowledge Graph And Department Exam Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build phases 1-4 for 龙汇QA: Obsidian-style knowledge graph, department-scoped choice-question generation, automatic grading, and department ranking.

**Architecture:** Reuse the existing document topology and training quiz services instead of adding a new graph database in this phase. The backend returns graph-ready nodes and edges with department grouping, while quiz generation is constrained to single-choice and multiple-choice questions and stored as exam attempts for ranking.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, H2/MySQL-compatible schema, Vue 3, TypeScript, Naive UI, ECharts graph, Vite.

---

## Phase Scope

| Phase | Name | This Iteration Delivers |
| --- | --- | --- |
| 1 | Obsidian-style knowledge graph | File nodes, department/category/lifecycle grouping, relation edges, graph stats, front-end force graph |
| 2 | Clickable source consistency | Quiz and assistant sources carry file identifiers so the UI can open source files |
| 3 | Department choice-question generation | Only single-choice and multiple-choice questions are generated from the selected public or department knowledge space |
| 4 | Auto grading and department ranking | Users submit answers, system grades objective questions, and department leaders can view ranking |

## File Structure

### Backend

- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentTopologyService.java`
  - Add node type, display label, department group, risk level, x/y seed values, and richer relation labels for graph rendering.
- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/service/TrainingQuizService.java`
  - Restrict supported question types to `single_choice` and `multiple_choice`.
  - Normalize model output so every question has options and answer arrays.
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/model/TrainingExamAttempt.java`
  - Stores one user's submitted paper, score, department, scope, answers, and source summary.
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/repository/TrainingExamAttemptRepository.java`
  - Provides ranking queries by department and scope.
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/service/TrainingExamService.java`
  - Grades submitted choice questions and returns ranking rows.
- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/controller/KnowledgeTrainingController.java`
  - Add `/quiz/submit` and `/quiz/ranking` endpoints.

### Frontend

- Modify `frontend/src/typings/api.d.ts`
  - Add graph fields and exam result/ranking types.
- Modify `frontend/src/views/knowledge-base/index.vue`
  - Render topology with an ECharts force graph.
  - Quiz modal supports answering questions and submitting for grading.
  - Ranking panel shows department ranking after submission.

## Task 1: Backend Graph DTO Enhancement

- [ ] Add graph metadata to document topology nodes: `nodeType`, `label`, `group`, `riskLevel`, `symbolSize`, `x`, `y`.
- [ ] Add graph metadata to edges: `lineStyle`, `curveness`, `description`.
- [ ] Build summary counts by department and relation type.
- [ ] Verify `/api/v1/documents/topology` still returns existing fields for backward compatibility.

## Task 2: Choice-Only Quiz Generation

- [ ] Restrict `TrainingQuizService` to `single_choice` and `multiple_choice`.
- [ ] Prompt the LLM to output only objective choice questions.
- [ ] Reject questions without at least two options.
- [ ] Normalize answers as a list, even for single choice.
- [ ] Keep fallback questions objective.

## Task 3: Exam Attempt And Auto Grading

- [ ] Add `TrainingExamAttempt` JPA entity.
- [ ] Add repository with latest ranking queries.
- [ ] Implement `TrainingExamService.submit(...)`.
- [ ] Grade single choice by exact normalized match.
- [ ] Grade multiple choice by set equality.
- [ ] Return score, correct count, total count, duration, and per-question review.

## Task 4: Department Ranking

- [ ] Implement ranking endpoint filtered by `knowledgeScope` and `departmentId`.
- [ ] Department members see their own department ranking.
- [ ] Department leaders see their department ranking.
- [ ] Super administrators can query any department ranking.
- [ ] Ranking sorts by score descending, then duration ascending, then submit time ascending.

## Task 5: Frontend Knowledge Graph

- [ ] Replace plain topology list with ECharts force graph.
- [ ] Color nodes by department group.
- [ ] Size nodes by relation count and risk.
- [ ] Clicking a node opens the existing file preview when possible.
- [ ] Keep relation list as a textual fallback below the graph.

## Task 6: Frontend Quiz Taking

- [ ] Limit quiz type selector to single-choice and multiple-choice.
- [ ] Render radio controls for single choice.
- [ ] Render checkbox controls for multiple choice.
- [ ] Submit answer payload to `/knowledge-training/quiz/submit`.
- [ ] Show score, correct count, and per-question review.

## Task 7: Frontend Ranking

- [ ] Add ranking tab or panel in the quiz modal.
- [ ] Load ranking after submission.
- [ ] Display rank, username, score, correct/total, duration, and submitted time.
- [ ] Highlight the current user's latest attempt.

## Verification

- [ ] Run backend build:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -DskipTests package
```

- [ ] Run frontend build:

```bash
cd frontend
node_modules/.bin/vite build --mode test
```

- [ ] Restart backend LaunchAgent.
- [ ] Open `http://127.0.0.1:9527/#/knowledge-base` and verify topology, quiz submission, and ranking UI.
