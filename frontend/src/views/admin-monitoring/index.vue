<script setup lang="ts">
defineOptions({ name: 'AdminMonitoring' });

const loading = ref(false);
const status = ref<Api.Admin.MonitoringStatus | null>(null);

async function fetchStatus() {
  loading.value = true;
  const { error, data: response } = await request<Api.Admin.MonitoringStatus>({
    url: '/admin/monitoring/status'
  });
  if (!error && response) {
    status.value = response;
  }
  loading.value = false;
}

onMounted(fetchStatus);

const components = computed(() => status.value?.components ?? {});
const metrics = computed(() => status.value?.metrics ?? {});

function statusType(value?: string) {
  if (value === 'UP') return 'success';
  if (value === 'DOWN') return 'error';
  if (value === 'DEGRADED') return 'warning';
  return 'default';
}

const embeddingDown = computed(() => components.value.vllmEmbedding?.status === 'DOWN');
const chatDown = computed(() => components.value.vllmChat?.status === 'DOWN');

function componentCards() {
  const c = components.value;
  return [
    { key: 'redis', label: 'Redis', data: c.redis },
    { key: 'elasticsearch', label: 'Elasticsearch', data: c.elasticsearch },
    { key: 'vllmChat', label: '对话模型 (8000)', data: c.vllmChat },
    { key: 'vllmEmbedding', label: '向量模型 (8001)', data: c.vllmEmbedding },
    { key: 'kafka', label: 'Kafka', data: c.kafka }
  ];
}
</script>

<template>
  <div class="paper-page min-h-500px flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="m-0 text-18px font-600">运行监控</h2>
        <p v-if="status?.timestamp" class="m-0 mt-1 text-13px color-[rgb(var(--base-text-color)/0.58)]">
          更新时间：{{ status.timestamp }}
        </p>
      </div>
      <NButton type="primary" :loading="loading" @click="fetchStatus">
        <template #icon>
          <icon-mdi-refresh />
        </template>
        刷新
      </NButton>
    </div>

    <NSpin :show="loading">
      <NAlert
        v-if="embeddingDown && status"
        type="warning"
        title="向量模型未启动"
        class="mb-4"
        :bordered="false"
      >
        <p class="m-0 mb-2 text-13px leading-relaxed">
          新上传文件无法向量化入库，知识库索引会失败或长期停留在「待索引」。对话模型（8000）不提供
          <code>/embeddings</code>，必须单独启动 bge-m3（8001）。
        </p>
        <ul class="hint-list m-0">
          <li>
            <strong>单卡常见做法：</strong>先停止 8000 上的 Qwen 对话进程，再启动 8001 向量服务；索引完成后再恢复 8000。
          </li>
          <li>
            <strong>启动命令：</strong>见项目文档
            <code>docs/手工启动指南.md</code> 第 3 步（本地模型路径
            <code>/home/lhagent/models/bge-m3</code>）。
          </li>
          <li>
            <strong>若启动报 OOM：</strong>说明 GPU 仍被大模型占用，请确认 8000 已退出后再试，或改用云端 Embedding 并修改
            <code>application.yml</code> 中 <code>embedding.api.*</code>（维度须保持 1024）。
          </li>
        </ul>
      </NAlert>

      <NAlert
        v-if="chatDown && status"
        :type="embeddingDown ? 'warning' : 'info'"
        title="对话模型未启动"
        class="mb-4"
        :bordered="false"
      >
        <template v-if="embeddingDown">
          当前 8000、8001 均未运行：建议先按上方说明启动 <strong>8001 向量</strong> 完成文件索引，再按
          <code>docs/手工启动指南.md</code> 第 2 步恢复 <strong>8000 对话</strong> 以启用智能问答。
        </template>
        <template v-else>
          智能问答不可用，但向量服务正常时可继续完成文件上传与索引。请按 <code>docs/手工启动指南.md</code> 第 2 步启动 8000。
        </template>
      </NAlert>

      <div class="monitor-grid">
        <NCard
          v-for="item in componentCards()"
          :key="item.key"
          size="small"
          :title="item.label"
          class="paper-card"
          :class="{ 'monitor-card--down': item.data?.status === 'DOWN' }"
        >
          <NTag :type="statusType(item.data?.status as string)" size="small">{{ item.data?.status || 'UNKNOWN' }}</NTag>
          <ul class="detail-list">
            <li v-if="item.key === 'elasticsearch'">文档数：{{ item.data?.knowledgeBaseCount ?? '-' }}</li>
            <li v-if="item.key === 'kafka'">积压：{{ item.data?.totalLag ?? '-' }}</li>
            <li v-if="item.data?.detail" class="detail-line">详情：{{ item.data.detail }}</li>
          </ul>
          <p v-if="item.key === 'vllmEmbedding' && item.data?.status === 'DOWN'" class="card-hint">
            预期地址：<code>127.0.0.1:8001</code>，模型 <code>bge-m3</code>
          </p>
        </NCard>
      </div>

      <NCard title="业务指标" size="small" class="paper-card mt-4 card-wrapper">
        <div class="metric-grid">
          <div class="metric-item">
            <span>索引成功</span>
            <strong>{{ metrics.indexSuccessCount ?? 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>索引失败</span>
            <strong>{{ metrics.indexFailureCount ?? 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>问答次数</span>
            <strong>{{ metrics.chatRequestCount ?? 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>问答均耗时(ms)</span>
            <strong>{{ metrics.chatAverageDurationMs ?? 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>问答 P95(ms)</span>
            <strong>{{ metrics.chatP95EstimateMs ?? 0 }}</strong>
          </div>
        </div>
        <p v-if="metrics.lastIndexFailureMessage" class="failure-tip">
          最近索引失败：{{ metrics.lastIndexFailureMessage }}
          <span v-if="metrics.lastIndexFailureAt">（{{ metrics.lastIndexFailureAt }}）</span>
        </p>
      </NCard>
    </NSpin>
  </div>
</template>

<style scoped>
.monitor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.detail-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: rgb(var(--base-text-color) / 0.68);
  font-size: 13px;
  line-height: 1.7;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.metric-item {
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric-item span {
  font-size: 13px;
  color: rgb(var(--base-text-color) / 0.58);
}

.metric-item strong {
  font-size: 22px;
}

.failure-tip {
  margin: 16px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgb(var(--error-color) / 0.08);
  color: rgb(var(--error-color));
  font-size: 13px;
}

.hint-list {
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.75;
  color: rgb(var(--base-text-color) / 0.78);
}

.hint-list li + li {
  margin-top: 6px;
}

.detail-line {
  word-break: break-all;
}

.card-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: rgb(var(--warning-color));
  line-height: 1.5;
}

.monitor-card--down :deep(.n-card-header__main) {
  color: rgb(var(--error-color));
}

.monitor-grid code {
  font-size: 12px;
  padding: 0 4px;
  border-radius: 4px;
  background: rgb(var(--primary-color) / 0.08);
}
</style>
