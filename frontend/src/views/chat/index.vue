<script setup lang="ts">
import { ACTIVE_KNOWLEDGE_SPACE_KEY } from '@/views/knowledge-base/utils/knowledge-space';
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';

const activeSpaceTitle = ref('全部可访问知识');

function refreshActiveSpaceTitle() {
  try {
    const context = JSON.parse(localStorage.getItem(ACTIVE_KNOWLEDGE_SPACE_KEY) || '{}');
    activeSpaceTitle.value = context.title || '全部可访问知识';
  } catch {
    activeSpaceTitle.value = '全部可访问知识';
  }
}

onMounted(refreshActiveSpaceTitle);
</script>

<template>
  <div class="paper-page min-h-500px h-full flex flex-col gap-4 overflow-hidden">
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
      <div class="header-tags flex flex-wrap justify-end gap-2 lt-sm:hidden">
        <NTag :bordered="false" size="small" type="warning">范围：{{ activeSpaceTitle }}</NTag>
        <NTag :bordered="false" size="small">权限隔离</NTag>
        <NTag :bordered="false" size="small" type="info">混合检索</NTag>
        <NTag :bordered="false" size="small" type="success">来源追溯</NTag>
      </div>
    </section>
    <div class="paper-stage min-h-0 flex flex-1 flex-col overflow-hidden card-wrapper">
      <ChatList class="min-h-0 flex-1" />
    </div>
    <InputBox class="shrink-0" />
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
