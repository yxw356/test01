<script setup lang="ts">
import {
  ACTIVE_KNOWLEDGE_SPACE_KEY,
  buildKnowledgeSpaces,
  type KnowledgeSpace
} from '@/views/knowledge-base/utils/knowledge-space';
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';

const activeSpace = ref<KnowledgeSpace | null>(null);
const spaceOptions = ref<Array<{ label: string; value: string }>>([]);

function readActiveSpaceContext() {
  try {
    return JSON.parse(localStorage.getItem(ACTIVE_KNOWLEDGE_SPACE_KEY) || '{}') as {
      id?: string;
      title?: string;
      knowledgeScope?: KnowledgeSpace['type'];
      departmentId?: string | null;
    };
  } catch {
    return {};
  }
}

function persistActiveSpace(space: KnowledgeSpace | null) {
  activeSpace.value = space;
  if (!space) {
    localStorage.removeItem(ACTIVE_KNOWLEDGE_SPACE_KEY);
    return;
  }
  localStorage.setItem(
    ACTIVE_KNOWLEDGE_SPACE_KEY,
    JSON.stringify({
      id: space.id,
      title: space.title,
      knowledgeScope: space.type,
      departmentId: space.departmentId
    })
  );
}

async function refreshSpaceOptions() {
  const { error, data } = await request<Api.KnowledgeBase.KnowledgeSpaceSummary[]>({
    url: '/documents/knowledge-spaces'
  });
  const spaces = !error && data?.length ? data : buildKnowledgeSpaces([]);
  spaceOptions.value = spaces.map(space => ({ label: space.title, value: space.id }));

  const saved = readActiveSpaceContext();
  const matched = spaces.find(space => space.id === saved.id) || spaces[0] || null;
  persistActiveSpace(matched);
}

function handleSpaceChange(spaceId: string | null) {
  const space = spaceOptions.value.find(item => item.value === spaceId);
  if (!space) {
    persistActiveSpace(null);
    return;
  }
  persistActiveSpace({
    id: space.value,
    title: space.label,
    type: space.value === 'PUBLIC' ? 'PUBLIC' : space.value.startsWith('PRIVATE') ? 'PRIVATE' : 'DEPARTMENT',
    departmentId: space.value.startsWith('DEPARTMENT:') ? space.value.replace('DEPARTMENT:', '') : null,
    fileCount: 0,
    indexedCount: 0,
    processingCount: 0,
    interruptedCount: 0,
    cleaningIssueCount: 0,
    lastUpdatedAt: null
  });
}

onMounted(refreshSpaceOptions);
</script>

<template>
  <div class="paper-page h-full flex flex-col gap-4">
    <section class="rag-workbench-header flex items-center justify-between gap-4">
      <div class="min-w-0">
        <div class="flex items-center gap-2">
          <span class="header-mark flex-center">
            <icon-solar:database-bold-duotone class="text-20px text-primary" />
          </span>
          <h1 class="m-0 truncate text-18px font-600">龙汇QA 问答工作台</h1>
        </div>
        <p class="m-0 mt-1 text-13px color-[rgb(var(--base-text-color)/0.58)]">
          面向龙汇内部制度、流程、项目资料的知识库问答
        </p>
      </div>
      <div class="header-tags flex flex-wrap items-center justify-end gap-2 lt-sm:hidden">
        <NSelect
          class="w-220px"
          size="small"
          :value="activeSpace?.id ?? null"
          :options="spaceOptions"
          placeholder="选择知识空间"
          @update:value="handleSpaceChange"
        />
        <NTag :bordered="false" size="small">权限隔离</NTag>
        <NTag :bordered="false" size="small" type="info">混合检索</NTag>
        <NTag :bordered="false" size="small" type="success">来源追溯</NTag>
      </div>
    </section>
    <div class="paper-stage min-h-0 flex flex-col flex-1 card-wrapper">
      <ChatList />
    </div>
    <InputBox />
  </div>
</template>

<style scoped>
.rag-workbench-header {
  min-height: 74px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: rgb(var(--container-bg-color));
  padding: 14px 18px;
  box-shadow: 0 10px 28px -24px rgb(15 23 42 / 0.28);
}

.header-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: rgb(var(--primary-color) / 0.08);
}

.header-tags :deep(.n-tag) {
  border-radius: 6px;
}

html.dark .rag-workbench-header {
  border-color: rgb(255 255 255 / 0.08);
  box-shadow: 0 18px 40px -30px rgb(0 0 0 / 0.5);
}
</style>
