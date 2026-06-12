# Knowledge Governance Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first trusted-knowledge gate for 龙汇QA: file lifecycle, policy audit status, retrieval eligibility filtering, clickable source foundation, FAQ, and term dictionary.

**Architecture:** Stage 1 adds governance metadata to `FileUpload`, then centralizes eligibility decisions in a small service used by document lists, knowledge spaces, indexing, and RAG retrieval. FAQ and term dictionary are introduced as separate bounded modules so later PPT, question bank, graph, and learning feedback features can reuse them without coupling to chat UI internals.

**Tech Stack:** Spring Boot 3.4, Spring Data JPA, H2/MySQL-compatible schema, Elasticsearch Java Client, Vue 3, TypeScript, Naive UI, Vite.

---

## Phase Roadmap

| Phase | Name | Goal | Main Deliverables | Done When |
| --- | --- | --- | --- | --- |
| 1 | Trusted Q&A Foundation | Ensure only valid knowledge can be searched and cited | lifecycle fields, audit status, search filter, clickable source data, FAQ, term dictionary | expired/rejected files are hidden from assistant; standard FAQ can answer directly; terms expand search |
| 2 | Training Material Generation | Generate department training assets | PPT generation task, question bank generation, artifact center | department lead can generate and review PPT/questions |
| 3 | Governance Graphs | Explain file and policy relationships | document topology, logic network graph, graph review | users can see file replacement/reference/conflict graph |
| 4 | Learning Loop | Improve with controlled feedback | feedback pool, FAQ candidates, term candidates, answer corrections | feedback must be reviewed before affecting answers |
| 5 | Evaluation Center | Measure quality before changes go live | generated test sets, retrieval/source accuracy metrics | each knowledge space has repeatable RAG evaluation |

## Scope Review

The design has no blocking issue, but it spans independent subsystems. Implementation must start with Phase 1 because later generation and graph features depend on correct file validity, audit status, and source traceability.

Existing code anchors:

- `src/main/java/com/yuki/enterprise_private_rag_qa/model/FileUpload.java` stores file metadata.
- `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentService.java` returns accessible files.
- `src/main/java/com/yuki/enterprise_private_rag_qa/controller/DocumentController.java` exposes document list and knowledge spaces.
- `src/main/java/com/yuki/enterprise_private_rag_qa/service/HybridSearchService.java` builds ES permission filters.
- `src/main/java/com/yuki/enterprise_private_rag_qa/service/rag/RagPipeline.java` orchestrates RAG.
- `frontend/src/views/knowledge-base/index.vue` renders the knowledge base file table and space cards.
- `frontend/src/views/chat/modules/chat-message.vue` renders citations.

## File Structure

### Backend

- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/model/FileUpload.java`
  - Add lifecycle and policy audit fields.
  - Add enums `LifecycleStatus` and `PolicyAuditStatus`.
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentLifecycleService.java`
  - Single place to decide whether a file is currently searchable.
  - Handles lifecycle update and timeline DTO construction.
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/service/PolicyAuditAgentService.java`
  - Rule-based first version of audit agent.
  - Produces pass/warning/reject/manual-review result without requiring LLM.
- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentService.java`
  - Filter assistant-searchable files where needed.
  - Keep management views able to show inactive/rejected files with status.
- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentIndexService.java`
  - Block reindexing when a file is not searchable.
- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/service/HybridSearchService.java`
  - Apply DB-side eligibility after ES recall so older indexed docs are filtered even before ES mappings are expanded.
- Modify `src/main/java/com/yuki/enterprise_private_rag_qa/controller/DocumentController.java`
  - Return lifecycle and audit fields in document DTO.
  - Add lifecycle and timeline endpoints.
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/model/FaqEntry.java`
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/repository/FaqEntryRepository.java`
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/service/FaqService.java`
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/controller/FaqController.java`
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/model/TermEntry.java`
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/repository/TermEntryRepository.java`
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/service/TermDictionaryService.java`
- Create `src/main/java/com/yuki/enterprise_private_rag_qa/controller/TermController.java`

### Frontend

- Modify `frontend/src/typings/api.d.ts`
  - Add lifecycle, audit, FAQ, term types.
- Modify `frontend/src/views/knowledge-base/index.vue`
  - Add file lifecycle columns.
  - Add timeline action.
  - Show audit status.
- Modify `frontend/src/views/chat/modules/chat-message.vue`
  - Make citations behave as direct source links.
- Add `frontend/src/views/faq/index.vue`
  - Basic FAQ list and create/edit form.
- Add `frontend/src/views/terms/index.vue`
  - Basic term dictionary list and create/edit form.
- Modify route/menu files after confirming existing route pattern.

### Tests

- Create `src/test/java/com/yuki/enterprise_private_rag_qa/service/DocumentLifecycleServiceTest.java`
- Create `src/test/java/com/yuki/enterprise_private_rag_qa/service/PolicyAuditAgentServiceTest.java`
- Create `src/test/java/com/yuki/enterprise_private_rag_qa/service/FaqServiceTest.java`
- Create `src/test/java/com/yuki/enterprise_private_rag_qa/service/TermDictionaryServiceTest.java`
- Extend `src/test/java/com/yuki/enterprise_private_rag_qa/controller/DocumentControllerTest.java`
- Extend `src/test/java/com/yuki/enterprise_private_rag_qa/service/DocumentIndexServiceTest.java`
- Extend `src/test/java/com/yuki/enterprise_private_rag_qa/service/HybridSearchServiceTest.java` if existing ES mocking is practical; otherwise unit-test the post-recall file filter in a separate helper.

---

### Task 1: File Lifecycle Model and Eligibility Service

**Files:**
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/model/FileUpload.java`
- Create: `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentLifecycleService.java`
- Test: `src/test/java/com/yuki/enterprise_private_rag_qa/service/DocumentLifecycleServiceTest.java`

- [ ] **Step 1: Write lifecycle eligibility tests**

Create `DocumentLifecycleServiceTest` with these cases:

```java
@Test
void activeApprovedFileIsSearchable() {
    FileUpload file = file("ACTIVE", "PASS");
    file.setEffectiveAt(LocalDateTime.now().minusDays(1));
    file.setAbolishedAt(LocalDateTime.now().plusDays(1));
    assertTrue(service.isSearchable(file, LocalDateTime.now()));
}

@Test
void expiredFileIsNotSearchable() {
    FileUpload file = file("ACTIVE", "PASS");
    file.setEffectiveAt(LocalDateTime.now().minusDays(10));
    file.setAbolishedAt(LocalDateTime.now().minusDays(1));
    assertFalse(service.isSearchable(file, LocalDateTime.now()));
}

@Test
void auditRejectedFileIsNotSearchable() {
    FileUpload file = file("ACTIVE", "REJECT");
    file.setEffectiveAt(LocalDateTime.now().minusDays(1));
    assertFalse(service.isSearchable(file, LocalDateTime.now()));
}

@Test
void historicalQueryCanIncludeExpiredFileWhenExplicitlyRequested() {
    FileUpload file = file("EXPIRED", "PASS");
    file.setEffectiveAt(LocalDateTime.now().minusYears(2));
    file.setAbolishedAt(LocalDateTime.now().minusYears(1));
    assertTrue(service.isVisibleInHistoryMode(file));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=DocumentLifecycleServiceTest test
```

Expected: FAIL because `DocumentLifecycleService` and new fields do not exist.

- [ ] **Step 3: Add fields and enums to `FileUpload`**

Add:

```java
@Column(name = "effective_at")
private LocalDateTime effectiveAt;

@Column(name = "abolished_at")
private LocalDateTime abolishedAt;

@Column(name = "published_at")
private LocalDateTime publishedAt;

@Column(name = "version_no", length = 64)
private String versionNo;

@Column(name = "supersedes_file_md5", length = 32)
private String supersedesFileMd5;

@Column(name = "superseded_by_file_md5", length = 32)
private String supersededByFileMd5;

@Enumerated(EnumType.STRING)
@Column(name = "lifecycle_status", nullable = false)
private LifecycleStatus lifecycleStatus = LifecycleStatus.ACTIVE;

@Enumerated(EnumType.STRING)
@Column(name = "policy_audit_status", nullable = false)
private PolicyAuditStatus policyAuditStatus = PolicyAuditStatus.PASS;

@Column(name = "policy_audit_score", nullable = false)
private double policyAuditScore = 100.0d;

@Column(name = "policy_audit_summary", length = 1024)
private String policyAuditSummary;

@Column(name = "policy_audit_issues", columnDefinition = "TEXT")
private String policyAuditIssues;
```

Enums:

```java
public enum LifecycleStatus {
    DRAFT,
    PENDING_AUDIT,
    AUDIT_REJECTED,
    APPROVED,
    ACTIVE,
    EXPIRED,
    REVOKED,
    SUPERSEDED
}

public enum PolicyAuditStatus {
    NOT_REQUIRED,
    PENDING,
    PASS,
    PASS_WITH_WARNINGS,
    REJECT,
    NEED_MANUAL_REVIEW
}
```

- [ ] **Step 4: Implement `DocumentLifecycleService`**

Rules:

```java
public boolean isSearchable(FileUpload file, LocalDateTime now) {
    if (file == null) return false;
    if (!isAuditAccepted(file)) return false;
    if (file.getLifecycleStatus() != FileUpload.LifecycleStatus.ACTIVE
            && file.getLifecycleStatus() != FileUpload.LifecycleStatus.APPROVED) {
        return false;
    }
    if (file.getEffectiveAt() != null && file.getEffectiveAt().isAfter(now)) return false;
    if (file.getAbolishedAt() != null && !file.getAbolishedAt().isAfter(now)) return false;
    return true;
}

public boolean isAuditAccepted(FileUpload file) {
    return file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.NOT_REQUIRED
            || file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.PASS
            || file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.PASS_WITH_WARNINGS;
}

public boolean isVisibleInHistoryMode(FileUpload file) {
    return file != null && isAuditAccepted(file);
}
```

- [ ] **Step 5: Run lifecycle tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=DocumentLifecycleServiceTest test
```

Expected: PASS.

### Task 2: Policy Audit Agent Foundation

**Files:**
- Create: `src/main/java/com/yuki/enterprise_private_rag_qa/service/PolicyAuditAgentService.java`
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/controller/DocumentController.java`
- Test: `src/test/java/com/yuki/enterprise_private_rag_qa/service/PolicyAuditAgentServiceTest.java`

- [ ] **Step 1: Write audit agent tests**

Cases:

```java
@Test
void completePolicyPasses() {
    String text = "适用范围：全体员工\n责任部门：人事行政部\n生效时间：2026-01-01\n审批人：总经理\n版本号：V1.0\n流程：提交、审批、归档、异常处理";
    AuditResult result = service.audit(text);
    assertEquals(FileUpload.PolicyAuditStatus.PASS, result.status());
    assertTrue(result.score() >= 90);
}

@Test
void missingLifecycleRequiresManualReview() {
    String text = "员工请假由部门负责人审批，审批后执行。";
    AuditResult result = service.audit(text);
    assertEquals(FileUpload.PolicyAuditStatus.NEED_MANUAL_REVIEW, result.status());
    assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("生效时间")));
}
```

- [ ] **Step 2: Implement rule-based audit**

Checks:

- `适用范围`
- `责任部门`
- `生效时间`
- `审批人`
- `版本`
- At least one of `异常处理`, `驳回`, `重新提交`, `归档`

Scoring:

- Start at 100.
- Subtract 15 for each missing required dimension.
- `score >= 90`: `PASS`
- `70 <= score < 90`: `PASS_WITH_WARNINGS`
- `50 <= score < 70`: `NEED_MANUAL_REVIEW`
- `< 50`: `REJECT`

- [ ] **Step 3: Add document audit endpoints**

In `DocumentController`:

- `GET /api/v1/documents/{fileMd5}/audit`
- `POST /api/v1/documents/{fileMd5}/audit/run`
- `POST /api/v1/documents/{fileMd5}/audit/review`

The first implementation can persist audit fields directly on `FileUpload`.

- [ ] **Step 4: Run audit tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=PolicyAuditAgentServiceTest test
```

Expected: PASS.

### Task 3: Apply Lifecycle Filter to Indexing and RAG Retrieval

**Files:**
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentIndexService.java`
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/service/HybridSearchService.java`
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/service/DocumentService.java`
- Test: `src/test/java/com/yuki/enterprise_private_rag_qa/service/DocumentIndexServiceTest.java`
- Test: `src/test/java/com/yuki/enterprise_private_rag_qa/service/DocumentLifecycleServiceTest.java`

- [ ] **Step 1: Add test that non-searchable files cannot be reindexed**

Expected behavior:

- `AUDIT_REJECTED` returns a `CustomException` with forbidden or bad request.
- `EXPIRED` returns a `CustomException`.
- `ACTIVE + PASS` submits task.

- [ ] **Step 2: Guard indexing**

Before submitting a processing task in `retryIndexing` and `retryCleaningAndIndexing`, call:

```java
if (!documentLifecycleService.isSearchable(file, LocalDateTime.now())) {
    throw new CustomException("文件未生效、已废止或审计未通过，不能纳入知识库检索", HttpStatus.BAD_REQUEST);
}
```

- [ ] **Step 3: Filter ES recall results**

After `attachFileNames(results)` in `HybridSearchService`, filter results by fetching `FileUpload` records and calling `documentLifecycleService.isSearchable(file, LocalDateTime.now())`.

Rule: if metadata is missing, exclude the result.

- [ ] **Step 4: Run targeted tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=DocumentLifecycleServiceTest,DocumentIndexServiceTest test
```

Expected: PASS.

### Task 4: Expose Lifecycle and Audit Data in Knowledge Base UI

**Files:**
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/controller/DocumentController.java`
- Modify: `frontend/src/typings/api.d.ts`
- Modify: `frontend/src/views/knowledge-base/index.vue`
- Test: existing frontend typecheck.

- [ ] **Step 1: Add fields to document DTO**

Return:

- `effectiveAt`
- `abolishedAt`
- `publishedAt`
- `versionNo`
- `lifecycleStatus`
- `policyAuditStatus`
- `policyAuditScore`
- `policyAuditSummary`

- [ ] **Step 2: Add lifecycle columns**

In knowledge base table:

- File name
- Lifecycle status
- Audit status
- Effective time
- Abolished time
- Version

Render expired/rejected statuses as warning/error tags.

- [ ] **Step 3: Add timeline button**

Add row action `时间线`, calling `GET /api/v1/documents/{fileMd5}/timeline`.

- [ ] **Step 4: Run frontend check**

Run:

```bash
cd frontend
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
```

Expected: no new lifecycle-related type errors.

### Task 5: Clickable Citation Foundation

**Files:**
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/model/RetrievalCitation.java`
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/service/ConversationService.java`
- Modify: `frontend/src/views/chat/modules/chat-message.vue`

- [ ] **Step 1: Extend citation model**

Add fields:

- `fileMd5`
- `chunkId`
- `pageNumber`
- `sectionTitle`
- `previewUrl`

- [ ] **Step 2: Populate citation URLs**

Build preview URL as:

```java
"/api/v1/documents/" + fileMd5 + "/preview?chunkId=" + chunkId
```

- [ ] **Step 3: Make frontend source item open preview**

If `citation.previewUrl` exists, click opens file preview with chunk context.

- [ ] **Step 4: Verify manually**

Ask a question that returns citations and click the first source.

Expected: preview opens and shows filename/snippet.

### Task 6: FAQ and Term Dictionary Backend

**Files:**
- Create: `FaqEntry`, `FaqEntryRepository`, `FaqService`, `FaqController`
- Create: `TermEntry`, `TermEntryRepository`, `TermDictionaryService`, `TermController`
- Modify: `ChatHandler` or `RagPipeline` in a later task to use these services.

- [ ] **Step 1: Implement FAQ entity**

Required fields:

- `standardQuestion`
- `similarQuestionsJson`
- `answer`
- `spaceId`
- `departmentId`
- `sourceFileMd5`
- `sourceChunkId`
- `enabled`
- `hitCount`

- [ ] **Step 2: Implement FAQ matching**

First version uses normalized exact match and substring match:

- normalize lower case
- remove whitespace
- apply term aliases before match

- [ ] **Step 3: Implement term entity**

Required fields:

- `term`
- `aliasesJson`
- `definition`
- `spaceId`
- `departmentId`
- `reviewStatus`
- `enabled`

- [ ] **Step 4: Implement term expansion**

Input: `绩效评估怎么做`  
If alias `绩效评估 -> 绩效考核`, output:

```java
List.of("绩效评估怎么做", "绩效考核怎么做")
```

- [ ] **Step 5: Run backend tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=FaqServiceTest,TermDictionaryServiceTest test
```

Expected: PASS.

### Task 7: Wire FAQ and Terms Into Chat

**Files:**
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/service/ChatHandler.java`
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/service/rag/QueryPipeline.java` or `QueryNormalizer.java`
- Test: `src/test/java/com/yuki/enterprise_private_rag_qa/service/ChatHandlerKnowledgeSpaceTest.java`

- [ ] **Step 1: FAQ before RAG**

Before invoking RAG retrieval, call FAQ matching.

If confidence is exact/high:

- stream FAQ answer
- attach FAQ source citation
- save conversation
- skip LLM call

- [ ] **Step 2: Terms before query rewrite**

Use `TermDictionaryService.expandQuery()` and pass expanded queries into existing multi-route retrieval.

- [ ] **Step 3: Run chat tests**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=ChatHandlerKnowledgeSpaceTest,QueryPipelineTest test
```

Expected: PASS.

## Verification Commands

Run after Phase 1 tasks:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn test
```

```bash
cd frontend
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
```

```bash
./scripts/local-dev-status.sh
```

## Plan Self-Review

Spec coverage:

- Lifecycle and abolished-file search exclusion: Task 1, Task 3, Task 4.
- Policy audit gate: Task 2, Task 3, Task 4.
- Clickable sources: Task 5.
- File name display: Task 4 keeps filename prominent and adds timeline metadata.
- FAQ: Task 6, Task 7.
- Term dictionary: Task 6, Task 7.
- PPT/question bank/graphs/learning loop/evaluation: intentionally deferred to Phases 2-5 after Phase 1 establishes trusted knowledge eligibility.

Known implementation note:

- `frontend/package.json` and `frontend/pnpm-lock.yaml` are already dirty from local dependency repair needed to run the LHRAG branch. Do not include those files in Phase 1 commits unless deliberately deciding to persist the dependency fixes.

## Execution Log

Completed on 2026-06-11:

- Task 1: Added document lifecycle fields to `FileUpload` and centralized searchable eligibility in `DocumentLifecycleService`.
- Task 2: Added rule-based `PolicyAuditAgentService` and document audit endpoints.
- Task 3: Blocked reindex/reclean for non-searchable documents and filtered RAG search results by lifecycle/audit eligibility.
- Task 4: Added frontend lifecycle, audit, and effective/abolished boundary columns; added manual policy audit action.
- Task 5: Confirmed chat citations are structured and clickable through file preview; kept sources collapsed by default.
- Task 6: Added FAQ and term dictionary entities, repositories, services, and management API endpoints.
- Task 7: Wired exact FAQ answers before RAG and term expansion/context into chat retrieval.

Verification run:

- `mvn -Dtest=DocumentLifecycleServiceTest,PolicyAuditAgentServiceTest,DocumentIndexServiceTest,ChatHandlerKnowledgeSpaceTest test` passed, 19 tests.
- `mvn -DskipTests package` passed.
- `frontend/node_modules/.bin/vite build --mode test` passed.

Residual note:

- `vue-tsc --noEmit --skipLibCheck` still reports existing project-wide type debt in request/table/chat store typings. The Vite build passes and the new lifecycle/audit additions did not introduce a blocking frontend build error.
