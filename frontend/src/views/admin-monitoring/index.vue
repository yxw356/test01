<script setup lang="ts">
defineOptions({ name: 'AdminMonitoring' });

const loading = ref(false);
const status = ref<Api.Admin.MonitoringStatus | null>(null);

async function fetchStatus() {
  loading.value = true;
  const { error, data: response } = await request<{ data: Api.Admin.MonitoringStatus }>({
    url: '/admin/monitoring/status'
  });
  if (!error && response?.data) {
    status.value = response.data;
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

function componentCards() {
  const c = components.value;
  return [
    { key: 'redis', label: 'Redis', data: c.redis },
    { key: 'elasticsearch', label: 'Elasticsearch', data: c.elasticsearch },
    { key: 'vllmChat', label: '对话模型', data: c.vllmChat },
    { key: 'vllmEmbedding', label: '向量模型', data: c.vllmEmbedding },
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
      <div class="monitor-grid">
        <NCard v-for="item in componentCards()" :key="item.key" size="small" :title="item.label" class="paper-card">
          <NTag :type="statusType(item.data?.status as string)" size="small">{{ item.data?.status || 'UNKNOWN' }}</NTag>
          <ul class="detail-list">
            <li v-if="item.key === 'elasticsearch'">文档数：{{ item.data?.knowledgeBaseCount ?? '-' }}</li>
            <li v-if="item.key === 'kafka'">积压：{{ item.data?.totalLag ?? '-' }}</li>
            <li v-if="item.data?.detail" class="truncate">详情：{{ item.data.detail }}</li>
          </ul>
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
</style>
