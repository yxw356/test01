# Knowledge Space Board Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the knowledge-base page from one large document table into draggable knowledge-space blocks for public knowledge and each department, then connect upload, statistics, chat filtering, and persistent `knowledge_space` data.

**Architecture:** Start with a frontend aggregation layer that derives spaces from existing `knowledgeScope` and `departmentId`, so the UI improves without a risky data migration. Then add backend space statistics and a real `knowledge_space` model, keeping current `FileUpload.knowledgeScope` and `departmentId` as compatibility fields.

**Tech Stack:** Vue 3, Naive UI, vue-draggable-plus, TypeScript, Spring Boot, JPA, H2/MySQL-compatible schema, Maven tests, frontend `tsx` helper tests.

---

### Task 1: Frontend Space Grouping

**Files:**
- Create: `frontend/src/views/knowledge-base/utils/knowledge-space.ts`
- Create: `frontend/src/views/knowledge-base/utils/knowledge-space.test.ts`
- Modify: `frontend/src/views/knowledge-base/index.vue`

- [ ] Write tests for deriving a fixed public space plus one space per visible department.
- [ ] Implement pure helpers for `buildKnowledgeSpaces`, `applySpaceLayout`, and `filterTasksBySpace`.
- [ ] Replace the current always-on table with a knowledge-space board and a selected-space table.
- [ ] Verify with `npx tsx src/views/knowledge-base/utils/knowledge-space.test.ts` and `vue-tsc`.

### Task 2: Draggable Personal Layout

**Files:**
- Modify: `frontend/src/views/knowledge-base/index.vue`
- Modify: `frontend/src/views/knowledge-base/utils/knowledge-space.ts`
- Modify: `frontend/src/views/knowledge-base/utils/knowledge-space.test.ts`

- [ ] Use `vue-draggable-plus` for desktop block ordering.
- [ ] Save selected space and block order in local storage as the first personal-layout implementation.
- [ ] Keep public knowledge pinned by default but allow super admin to move it if needed later.
- [ ] Verify layout restore after page reload.

### Task 3: Backend Space Statistics API

**Files:**
- Create: `src/main/java/com/yuki/enterprise_private_rag_qa/service/KnowledgeSpaceService.java`
- Create: `src/test/java/com/yuki/enterprise_private_rag_qa/service/KnowledgeSpaceServiceTest.java`
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/controller/DocumentController.java`
- Create: `src/test/java/com/yuki/enterprise_private_rag_qa/controller/DocumentControllerKnowledgeSpaceTest.java`

- [ ] Test that accessible documents are summarized into public and department spaces.
- [ ] Return file count, indexed count, processing count, interrupted count, cleaning issue count, and last updated time.
- [ ] Add `GET /api/v1/documents/knowledge-spaces`.
- [ ] Use the API on the frontend when available; fall back to client aggregation if it fails.

### Task 4: Upload Starts From Space

**Files:**
- Modify: `frontend/src/views/knowledge-base/modules/upload-dialog.vue`
- Modify: `frontend/src/views/knowledge-base/index.vue`

- [ ] Pass selected space into upload dialog.
- [ ] Pre-fill `knowledgeScope` and `departmentId` from the selected block.
- [ ] Keep category and cleaning-rule filtering scoped to the selected block.
- [ ] Verify public upload, department upload, and department-lead restricted upload.

### Task 5: Chat Uses Current Knowledge Space

**Files:**
- Modify: `frontend/src/views/chat/index.vue`
- Modify: `frontend/src/views/chat/modules/*`
- Modify: backend chat request DTO/service files after locating current chat pipeline.

- [ ] Add optional selected-space context to chat requests.
- [ ] Ensure retrieval filters by public space or selected department where appropriate.
- [ ] Show the active knowledge space as a compact selector, with sources collapsed by default.
- [ ] Verify general questions still work and knowledge questions prefer selected-space documents.

### Task 6: Persistent Knowledge Space Model

**Files:**
- Create: `src/main/java/com/yuki/enterprise_private_rag_qa/model/KnowledgeSpace.java`
- Create: `src/main/java/com/yuki/enterprise_private_rag_qa/repository/KnowledgeSpaceRepository.java`
- Create: `src/main/java/com/yuki/enterprise_private_rag_qa/model/UserKnowledgeSpaceLayout.java`
- Create: `src/main/java/com/yuki/enterprise_private_rag_qa/repository/UserKnowledgeSpaceLayoutRepository.java`
- Modify: `src/main/java/com/yuki/enterprise_private_rag_qa/model/FileUpload.java`

- [ ] Add `KnowledgeSpace` records for public and department spaces.
- [ ] Backfill missing spaces from existing organization tags and file uploads at startup.
- [ ] Link uploaded files to `spaceId` while preserving existing fields.
- [ ] Store user block order and collapsed state server-side.

### Task 7: Final Integration

**Files:**
- Modify affected frontend and backend tests.
- Update: `docs/使用文档.md`
- Update: `docs/技术文档.md`

- [ ] Migrate frontend from local layout to server layout when authenticated.
- [ ] Add docs for public space, department spaces, drag sorting, upload routing, and chat space context.
- [ ] Run backend tests, frontend typecheck, package build, restart services.
- [ ] Verify in the browser on `/knowledge-base` and `/chat`.
