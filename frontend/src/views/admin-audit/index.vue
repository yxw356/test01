<script setup lang="tsx">
import { NButton, NTag } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';

defineOptions({ name: 'AdminAudit' });

const actionOptions = [
  { label: '全部', value: '' },
  { label: '登录', value: 'LOGIN' },
  { label: '上传', value: 'UPLOAD' },
  { label: '删除', value: 'DELETE' },
  { label: '检索', value: 'SEARCH' },
  { label: '预览', value: 'PREVIEW' },
  { label: '下载', value: 'DOWNLOAD' },
  { label: '问答', value: 'CHAT' },
  { label: '索引成功', value: 'INDEX_SUCCESS' },
  { label: '索引失败', value: 'INDEX_FAILURE' }
];

const searchParams = reactive({
  userId: '',
  action: null as string | null,
  page: 0,
  size: 20
});

const loading = ref(false);
const list = ref<Api.Admin.AuditLog[]>([]);
const total = ref(0);

const columns: DataTableColumns<Api.Admin.AuditLog> = [
  {
    title: '时间',
    key: 'createdAt',
    width: 170,
    render: row => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss')
  },
  {
    title: '用户',
    key: 'username',
    width: 120,
    render: row => row.username || row.userId || '-'
  },
  { title: '操作', key: 'action', width: 110 },
  { title: '资源', key: 'resourceId', ellipsis: { tooltip: true }, minWidth: 140 },
  { title: '详情', key: 'detail', ellipsis: { tooltip: true }, minWidth: 180 },
  {
    title: '结果',
    key: 'result',
    width: 90,
    render: row => {
      if (row.result === 'SUCCESS') return <NTag type="success">成功</NTag>;
      if (row.result === 'FAILURE') return <NTag type="error">失败</NTag>;
      return <NTag>{row.result || '-'}</NTag>;
    }
  },
  {
    title: '耗时(ms)',
    key: 'durationMs',
    width: 90,
    render: row => row.durationMs ?? '-'
  },
  { title: 'IP', key: 'clientIp', width: 130 }
];

async function fetchLogs() {
  loading.value = true;
  const params: Record<string, string | number> = {
    page: searchParams.page,
    size: searchParams.size
  };
  if (searchParams.userId.trim()) params.userId = searchParams.userId.trim();
  if (searchParams.action) params.action = searchParams.action;

  const { error, data: response } = await request<{ data: Api.Admin.AuditLog[]; total: number }>({
    url: '/admin/audit-logs',
    params
  });

  if (!error && response) {
    list.value = response.data ?? [];
    total.value = response.total ?? 0;
  }
  loading.value = false;
}

onMounted(fetchLogs);

function handleSearch() {
  searchParams.page = 0;
  fetchLogs();
}

function handlePageChange(page: number) {
  searchParams.page = page - 1;
  fetchLogs();
}
</script>

<template>
  <div class="paper-page min-h-500px flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <NCard title="审计日志" :bordered="false" size="small" class="paper-card card-wrapper">
      <NForm inline label-placement="left" :show-feedback="false" class="mb-4">
        <NFormItem label="用户ID">
          <NInput v-model:value="searchParams.userId" placeholder="用户名或用户ID" clearable class="w-200px!" />
        </NFormItem>
        <NFormItem label="操作">
          <NSelect
            v-model:value="searchParams.action"
            :options="actionOptions"
            clearable
            placeholder="全部"
            class="w-160px!"
          />
        </NFormItem>
        <NFormItem>
          <NButton type="primary" :loading="loading" @click="handleSearch">查询</NButton>
        </NFormItem>
      </NForm>

      <NDataTable :loading="loading" :columns="columns" :data="list" :scroll-x="1100" size="small" />

      <div class="mt-4 flex justify-end">
        <NPagination
          :page="searchParams.page + 1"
          :page-size="searchParams.size"
          :item-count="total"
          @update:page="handlePageChange"
        />
      </div>
    </NCard>
  </div>
</template>
