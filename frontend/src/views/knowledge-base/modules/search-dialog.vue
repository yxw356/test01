<script setup lang="ts">
defineOptions({
  name: 'SearchDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const { formRef, restoreValidation } = useNaiveForm();

const store = useAuthStore();
const model = ref<Api.KnowledgeBase.SearchParams>(createDefaultModel());

function createDefaultModel(): Api.KnowledgeBase.SearchParams {
  return {
    userId: `${store.userInfo.id}`,
    query: '',
    topK: 10
  };
}

const list = ref<Api.KnowledgeBase.SearchResult[]>([]);

const patterns = ref<string[]>([]);
const hasSearched = ref(false);

const sortedList = computed(() => {
  return [...list.value].sort((a, b) => diagnosticScore(b) - diagnosticScore(a));
});

const topHit = computed(() => sortedList.value[0]);
const hitFileCount = computed(() => new Set(list.value.map(item => item.fileMd5).filter(Boolean)).size);

function highlight(text?: string | null) {
  if (!text || !model.value.query) return false;
  return text.includes(model.value.query);
}

function displaySnippet(item: Api.KnowledgeBase.SearchResult) {
  return item.parentTextContent || item.textContent || '';
}

function diagnosticScore(item: Api.KnowledgeBase.SearchResult) {
  return item.finalRank ? 100000 - item.finalRank : item.crossScore || item.rrfScore || item.score || 0;
}

function formatScore(value?: number | null) {
  if (value === undefined || value === null) return '-';
  if (Math.abs(value) >= 100) return value.toFixed(1);
  return value.toFixed(4).replace(/0+$/, '').replace(/\.$/, '');
}

function scopeLabel(item: Api.KnowledgeBase.SearchResult) {
  const scope = item.knowledgeScope || (item.isPublic ? 'PUBLIC' : 'DEPARTMENT');
  if (scope === 'PUBLIC') return '公共知识';
  if (scope === 'PRIVATE') return '私人知识';
  return '部门知识';
}

function scopeType(item: Api.KnowledgeBase.SearchResult) {
  const scope = item.knowledgeScope || (item.isPublic ? 'PUBLIC' : 'DEPARTMENT');
  if (scope === 'PUBLIC') return 'success';
  if (scope === 'PRIVATE') return 'warning';
  return 'info';
}

function routeTags(item: Api.KnowledgeBase.SearchResult) {
  const sources = item.retrievalSources?.length ? item.retrievalSources : item.retrievalSource ? [item.retrievalSource] : [];
  return sources.slice(0, 4);
}

async function search() {
  if (!model.value.query.trim()) {
    window.$message?.warning('请输入检索问题或关键词');
    return;
  }
  loading.value = true;
  const { error, data } = await request<Api.KnowledgeBase.SearchResult[]>({
    url: '/search/hybrid',
    params: model.value,
    baseURL: '/proxy-api'
  });
  if (!error) {
    list.value = data;
    patterns.value = [model.value.query];
    hasSearched.value = true;
  }
  loading.value = false;
}

function reset() {
  model.value = createDefaultModel();
  patterns.value = [];
  list.value = [];
  hasSearched.value = false;
  restoreValidation();
}
watch(visible, () => {
  if (visible.value) {
    reset();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="知识库混合检索"
    :show-icon="false"
    :mask-closable="false"
    class="paper-modal w-1000px!"
  >
    <NForm
      ref="formRef"
      :model="model"
      label-placement="left"
      :label-width="60"
      inline
      class="pb-2"
      :show-feedback="false"
    >
      <NGrid>
        <NFormItemGi label="召回数" path="topK" class="pr-24px" span="6">
          <NInputNumber
            v-model:value="model.topK"
            placeholder="请输入召回数量"
            clearable
            :min="1"
            :precision="0"
            :step="10"
          />
        </NFormItemGi>
        <NFormItemGi label="检索词" path="query" class="pr-24px" span="12">
          <NInput v-model:value="model.query" placeholder="请输入检索问题或关键词" clearable />
        </NFormItemGi>
        <NFormItemGi span="6">
          <NSpace class="w-full" justify="end">
            <NButton @click="reset">
              <template #icon>
                <icon-ic-round-refresh class="text-icon" />
              </template>
              重置
            </NButton>
            <NButton type="primary" ghost @click="search">
              <template #icon>
                <icon-ic-round-search class="text-icon" />
              </template>
              搜索
            </NButton>
          </NSpace>
        </NFormItemGi>
      </NGrid>
    </NForm>
    <NSpin :show="loading">
      <NEmpty v-if="list.length === 0" :description="hasSearched ? '没有命中可用片段' : '输入问题后查看检索链路'" class="py-100px" />
      <div v-else class="diagnostic-summary">
        <div>
          <span>命中片段</span>
          <strong>{{ list.length }}</strong>
        </div>
        <div>
          <span>命中文件</span>
          <strong>{{ hitFileCount }}</strong>
        </div>
        <div>
          <span>最高分</span>
          <strong>{{ formatScore(topHit?.score) }}</strong>
        </div>
        <div>
          <span>最高命中</span>
          <strong>{{ topHit?.fileName || '-' }}</strong>
        </div>
      </div>
      <NScrollbar v-if="list.length" class="max-h-540px">
        <NCard
          v-for="(item, index) in sortedList"
          :key="index"
          :bordered="false"
          class="search-result-card my-8"
          :segmented="{
            content: true,
            footer: 'soft'
          }"
        >
          <div class="result-head">
            <div class="result-title">
              <NTag size="small" type="primary" :bordered="false">#{{ index + 1 }}</NTag>
              <strong>{{ item.fileName || '未命名文件' }}</strong>
            </div>
            <div class="result-tags">
              <NTag size="small" :type="scopeType(item)" :bordered="false">{{ scopeLabel(item) }}</NTag>
              <NTag v-if="item.departmentId || item.orgTag" size="small" :bordered="false">
                {{ item.departmentId || item.orgTag }}
              </NTag>
              <NTag v-for="source in routeTags(item)" :key="source" size="small" :bordered="false">
                {{ source }}
              </NTag>
            </div>
          </div>
          <div class="score-grid">
            <div>
              <span>原始分</span>
              <strong>{{ formatScore(item.score) }}</strong>
            </div>
            <div>
              <span>RRF</span>
              <strong>{{ formatScore(item.rrfScore) }}</strong>
            </div>
            <div>
              <span>精排</span>
              <strong>{{ formatScore(item.crossScore) }}</strong>
            </div>
            <div>
              <span>最终排名</span>
              <strong>{{ item.finalRank || item.crossRank || item.rrfRank || '-' }}</strong>
            </div>
          </div>
          <div class="snippet-box">
            <NHighlight
              v-if="highlight(displaySnippet(item))"
              highlight-class="bg-[rgb(var(--primary-400-color))] color-white px-2 mx-1 rd-sm"
              :text="displaySnippet(item)"
              :patterns="patterns"
            />
            <span v-else>{{ displaySnippet(item) }}</span>
          </div>
          <template #footer>
            <div class="result-footer">
              <span>文件MD5：{{ item.fileMd5 }}</span>
              <span>chunk：{{ item.chunkId ?? '-' }}</span>
              <span v-if="item.parentId">parent：{{ item.parentId }}</span>
              <span v-if="item.queryUsed">query：{{ item.queryUsed }}</span>
            </div>
          </template>
        </NCard>
      </NScrollbar>
    </NSpin>
  </NModal>
</template>

<style scoped>
.search-result-card {
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: rgb(var(--container-bg-color));
}

.diagnostic-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}

.diagnostic-summary > div {
  min-width: 0;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 10px 12px;
  background: rgb(var(--base-color));
}

.diagnostic-summary span,
.score-grid span {
  display: block;
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 12px;
}

.diagnostic-summary strong,
.score-grid strong {
  display: block;
  overflow: hidden;
  margin-top: 4px;
  font-size: 16px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.result-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.result-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.score-grid > div {
  border-radius: 8px;
  background: rgb(var(--base-color));
  padding: 8px 10px;
}

.snippet-box {
  max-height: 180px;
  overflow: auto;
  border-radius: 8px;
  background: rgb(var(--base-color));
  padding: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.result-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: rgb(var(--base-text-color) / 0.62);
  font-size: 12px;
}

html.dark .search-result-card {
  border-color: rgb(255 255 255 / 0.08);
}

@media (max-width: 768px) {
  .diagnostic-summary,
  .score-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .result-head {
    display: block;
  }

  .result-tags {
    justify-content: flex-start;
    margin-top: 8px;
  }
}
</style>
