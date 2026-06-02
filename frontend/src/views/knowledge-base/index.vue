<script setup lang="tsx">
import type { UploadFileInfo } from 'naive-ui';
import { NButton, NEllipsis, NModal, NPopconfirm, NProgress, NTag, NTooltip, NUpload } from 'naive-ui';
import { uploadAccept } from '@/constants/common';
import { fakePaginationRequest } from '@/service/request';
import { UploadStatus, IndexStatus } from '@/enum';
import SvgIcon from '@/components/custom/svg-icon.vue';
import FilePreview from '@/components/custom/file-preview.vue';
import UploadDialog from './modules/upload-dialog.vue';
import SearchDialog from './modules/search-dialog.vue';

const appStore = useAppStore();
const authStore = useAuthStore();

// 文件预览相关状态
const previewVisible = ref(false);
const previewFileName = ref('');

function apiFn() {
  return fakePaginationRequest<Api.KnowledgeBase.List>({ url: '/documents/accessible' });
}

function renderIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    if (uploadAccept.split(',').includes(`.${ext}`)) return <SvgIcon localIcon={ext} class="mx-4 text-12" />;
    return <SvgIcon localIcon="dflt" class="mx-4 text-12" />;
  }
  return null;
}

// 处理文件预览
function handleFilePreview(fileName: string) {
  previewFileName.value = fileName;
  previewVisible.value = true;
}

// 关闭文件预览
function closeFilePreview() {
  previewVisible.value = false;
  previewFileName.value = '';
}

const { columns, columnChecks, data, getData, loading } = useTable({
  apiFn,
  immediate: false,
  columns: () => [
    {
      key: 'fileName',
      title: '文件名',
      minWidth: 400,
      render: row => (
        <div class="flex items-center">
          {renderIcon(row.fileName)}
          <NEllipsis lineClamp={2} tooltip>
            <span
              class="cursor-pointer transition-colors hover:text-primary"
              onClick={() => handleFilePreview(row.fileName)}
            >
              {row.fileName}
            </span>
          </NEllipsis>
        </div>
      )
    },
    {
      key: 'totalSize',
      title: '文件大小',
      width: 100,
      render: row => fileSize(row.totalSize)
    },
    {
      key: 'status',
      title: '上传状态',
      width: 100,
      render: row => renderStatus(row.status, row.progress)
    },
    {
      key: 'indexStatus',
      title: '索引状态',
      width: 110,
      render: row => renderIndexStatus(row)
    },
    {
      key: 'knowledgeScope',
      title: '知识类型',
      width: 120,
      render: row => renderKnowledgeScope(row)
    },
    {
      key: 'orgTagName',
      title: '所属部门',
      width: 150,
      ellipsis: { tooltip: true, lineClamp: 2 }
    },
    {
      key: 'isPublic',
      title: '可见范围',
      width: 100,
      render: row => renderVisibility(row)
    },
    {
      key: 'createdAt',
      title: '上传时间',
      width: 100,
      render: row => dayjs(row.createdAt).format('YYYY-MM-DD')
    },
    {
      key: 'operate',
      title: '操作',
      width: 240,
      render: row => (
        <div class="flex flex-wrap gap-4">
          {renderResumeUploadButton(row)}
          {renderReindexButton(row)}
          <NButton type="primary" ghost size="small" onClick={() => handleFilePreview(row.fileName)}>
            预览
          </NButton>
          {renderDeleteButton(row)}
        </div>
      )
    }
  ]
});

const store = useKnowledgeBaseStore();
const { tasks, uploadPreflight, uploadPreflightLoading } = storeToRefs(store);

const filterModel = reactive({
  keyword: '',
  knowledgeScope: null as 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE' | null,
  departmentId: null as string | null
});

const knowledgeScopeOptions = [
  { label: '公共知识', value: 'PUBLIC' },
  { label: '部门知识', value: 'DEPARTMENT' },
  { label: '私人知识', value: 'PRIVATE' }
];

const filteredTasks = computed(() => {
  return tasks.value.filter(item => {
    const keyword = filterModel.keyword.trim().toLowerCase();
    if (keyword && !item.fileName.toLowerCase().includes(keyword)) return false;

    const scope = item.knowledgeScope || (item.public || item.isPublic ? 'PUBLIC' : 'DEPARTMENT');
    if (filterModel.knowledgeScope && scope !== filterModel.knowledgeScope) return false;

    const departmentId = item.departmentId || item.orgTag;
    if (filterModel.departmentId && departmentId !== filterModel.departmentId) return false;

    return true;
  });
});

const totalCount = computed(() => tasks.value.length);
const completedCount = computed(() => tasks.value.filter(item => item.status === UploadStatus.Completed).length);
const departmentCount = computed(() => tasks.value.filter(item => normalizeScope(item) === 'DEPARTMENT').length);
const processingCount = computed(() => tasks.value.filter(item => item.status !== UploadStatus.Completed).length);

const indexedCount = computed(() =>
  tasks.value.filter(item => item.indexStatus === IndexStatus.Indexed || item.indexStatus === undefined).length
);
const indexPendingCount = computed(() =>
  tasks.value.filter(item =>
    [IndexStatus.Pending, IndexStatus.Indexing].includes(item.indexStatus as IndexStatus)
  ).length
);

let indexPollTimer: ReturnType<typeof setInterval> | null = null;

onMounted(async () => {
  await Promise.all([getList(), store.refreshUploadPreflight()]);
  startIndexPolling();
});

onUnmounted(() => {
  if (indexPollTimer) clearInterval(indexPollTimer);
});

function startIndexPolling() {
  if (indexPollTimer) clearInterval(indexPollTimer);
  indexPollTimer = setInterval(async () => {
    if (indexPendingCount.value > 0) {
      await getList();
    }
  }, 5000);
}

/** 异步获取列表函数 该函数主要用于更新或初始化上传任务列表 它首先调用getData函数获取数据，然后根据获取到的数据状态更新任务列表 */
async function getList() {
  // 等待获取最新数据
  await getData();

  const serverTasks = data.value.map(item => ({
    ...item,
    status: item.status === UploadStatus.Completed ? UploadStatus.Completed : UploadStatus.Break
  }));
  const resumableLocalTasks = tasks.value.filter(task => task.file && task.status !== UploadStatus.Completed);
  const mergedServerTasks = serverTasks.map(serverTask => {
    const localTask = resumableLocalTasks.find(task => task.fileMd5 === serverTask.fileMd5);
    if (!localTask) return serverTask;

    return {
      ...serverTask,
      file: localTask.file,
      chunk: localTask.chunk,
      chunkIndex: localTask.chunkIndex,
      requestIds: localTask.requestIds,
      uploadedChunks: localTask.uploadedChunks,
      progress: localTask.progress,
      uploadError: localTask.uploadError,
      status: localTask.status
    };
  });
  const localOnlyTasks = resumableLocalTasks.filter(
    task => !serverTasks.some(serverTask => serverTask.fileMd5 === task.fileMd5)
  );

  tasks.value = [...mergedServerTasks, ...localOnlyTasks];
}

async function handleDelete(fileMd5: string) {
  const index = tasks.value.findIndex(task => task.fileMd5 === fileMd5);

  if (index !== -1) {
    tasks.value[index].requestIds?.forEach(requestId => {
      request.cancelRequest(requestId);
    });
  }

  // 如果文件一个分片也没有上传完成，则直接删除
  if (index !== -1 && tasks.value[index].uploadedChunks && tasks.value[index].uploadedChunks.length === 0) {
    tasks.value.splice(index, 1);
    return;
  }

  const { error } = await request({ url: `/documents/${fileMd5}`, method: 'DELETE' });
  if (!error) {
    tasks.value.splice(index, 1);
    window.$message?.success('删除成功');
    await getData();
  }
}

// #region 文件上传
const uploadVisible = ref(false);
const canUploadKnowledge = computed(() => authStore.isSuperAdmin || authStore.isDeptLead || authStore.userInfo.role === 'KNOWLEDGE_ADMIN');
const accessModeTag = computed(() => {
  if (authStore.isSuperAdmin) return { label: '全局管理', type: 'success' as const };
  if (authStore.userInfo.role === 'KNOWLEDGE_ADMIN') return { label: '公共上传', type: 'success' as const };
  if (authStore.isDeptLead) return { label: '部门上传', type: 'info' as const };
  return { label: '仅查看', type: 'warning' as const };
});
const uploadServiceTag = computed(() => {
  if (!uploadPreflight.value) return { label: '上传服务未检查', type: 'default' as const };
  if (uploadPreflight.value.ready) return { label: '上传服务正常', type: 'success' as const };
  return { label: '上传服务未就绪', type: 'error' as const };
});
const uploadServiceComponents = computed(() => {
  const components = uploadPreflight.value?.components ?? {};
  return [
    { key: 'minio', label: 'MinIO', data: components.minio },
    { key: 'redis', label: 'Redis', data: components.redis },
    { key: 'kafka', label: 'Kafka', data: components.kafka }
  ];
});
const uploadServiceCommand = 'docker-compose -f docs/docker-compose.yaml up -d redis minio kafka';
async function handleUpload() {
  if (!canUploadKnowledge.value) {
    window.$message?.warning('当前角色暂无上传公共或部门知识的权限');
    return;
  }
  const ready = await store.checkUploadPreflight();
  if (!ready) return;
  uploadVisible.value = true;
}
// #endregion

// #region 检索知识库
const searchVisible = ref(false);
function handleSearch() {
  searchVisible.value = true;
}
// #endregion

function resetFilters() {
  filterModel.keyword = '';
  filterModel.knowledgeScope = null;
  filterModel.departmentId = null;
}

function normalizeScope(row: Api.KnowledgeBase.UploadTask) {
  return row.knowledgeScope || (row.public || row.isPublic ? 'PUBLIC' : 'DEPARTMENT');
}

function renderKnowledgeScope(row: Api.KnowledgeBase.UploadTask) {
  const scope = normalizeScope(row);
  if (scope === 'PUBLIC') return <NTag type="success">公共知识</NTag>;
  if (scope === 'PRIVATE') return <NTag type="warning">私人知识</NTag>;
  return <NTag type="info">部门知识</NTag>;
}

function renderVisibility(row: Api.KnowledgeBase.UploadTask) {
  const scope = normalizeScope(row);
  if (scope === 'PUBLIC') return <NTag type="success">全员可见</NTag>;
  if (scope === 'PRIVATE') return <NTag type="warning">仅本人</NTag>;
  return <NTag type="info">部门可见</NTag>;
}

function canManageDocument(row: Api.KnowledgeBase.UploadTask) {
  if (typeof row.canManage === 'boolean') return row.canManage;
  if (authStore.isSuperAdmin) return true;
  if (String(authStore.userInfo.id) === String(row.userId)) return true;
  if (!authStore.isDeptLead) return false;

  const scope = normalizeScope(row);
  const departmentId = row.departmentId || row.orgTag;
  return scope === 'DEPARTMENT' && Boolean(departmentId) && authStore.userInfo.orgTags.includes(departmentId!);
}

// 渲染上传状态
function renderStatus(status: UploadStatus, percentage: number) {
  if (status === UploadStatus.Completed) return <NTag type="success">已完成</NTag>;
  else if (status === UploadStatus.Break) return <NTag type="error">上传中断</NTag>;
  return <NProgress percentage={percentage} processing />;
}

function renderIndexStatus(row: Api.KnowledgeBase.UploadTask) {
  if (row.status !== UploadStatus.Completed) {
    return <NTag bordered={false}>-</NTag>;
  }
  const status = row.indexStatus ?? IndexStatus.Indexed;
  if (status === IndexStatus.Pending) {
    return <NTag type="warning">待索引</NTag>;
  }
  if (status === IndexStatus.Indexing) {
    return <NTag type="info">索引中</NTag>;
  }
  if (status === IndexStatus.Failed) {
    return (
      <NTooltip trigger="hover">
        {{
          trigger: () => <NTag type="error">索引失败</NTag>,
          default: () => row.indexError || '请查看后端日志或重新上传'
        }}
      </NTooltip>
    );
  }
  return <NTag type="success">可检索</NTag>;
}

function renderReindexButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status !== UploadStatus.Completed) return null;
  if (!canManageDocument(row)) return null;
  const indexStatus = row.indexStatus ?? IndexStatus.Indexed;
  if (indexStatus !== IndexStatus.Failed && indexStatus !== IndexStatus.Pending && indexStatus !== IndexStatus.Indexing) {
    return null;
  }
  return (
    <NButton type="warning" ghost size="small" onClick={() => handleReindex(row.fileMd5)}>
      重试索引
    </NButton>
  );
}

function renderDeleteButton(row: Api.KnowledgeBase.UploadTask) {
  if (!canManageDocument(row)) return null;
  return (
    <NPopconfirm onPositiveClick={() => handleDelete(row.fileMd5)}>
      {{
        default: () => '确认删除当前文件吗？',
        trigger: () => (
          <NButton type="error" ghost size="small">
            删除
          </NButton>
        )
      }}
    </NPopconfirm>
  );
}

async function handleReindex(fileMd5: string) {
  const { error } = await request({
    url: `/documents/${fileMd5}/reindex`,
    method: 'POST'
  });
  if (!error) {
    window.$message?.success('索引任务已重新提交');
    await getList();
  }
}

// #region 文件续传
function renderResumeUploadButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status === UploadStatus.Break) {
    if (row.file)
      return (
        <NButton type="primary" size="small" ghost onClick={() => resumeUpload(row)}>
          续传
        </NButton>
      );
    return (
      <NUpload
        show-file-list={false}
        default-upload={false}
        accept={uploadAccept}
        onBeforeUpload={options => onBeforeUpload(options, row)}
        class="w-fit"
      >
        <NButton type="primary" size="small" ghost>
          选择文件续传
        </NButton>
      </NUpload>
    );
  }
  return null;
}

// 任务列表存在文件，直接续传
async function resumeUpload(row: Api.KnowledgeBase.UploadTask) {
  const ready = await store.checkUploadPreflight();
  if (!ready) return;
  row.uploadError = undefined;
  row.status = UploadStatus.Pending;
  store.startUpload();
}

async function onBeforeUpload(
  options: { file: UploadFileInfo; fileList: UploadFileInfo[] },
  row: Api.KnowledgeBase.UploadTask
) {
  const md5 = await calculateMD5(options.file.file!);
  if (md5 !== row.fileMd5) {
    window.$message?.error('两次上传的文件不一致');
    return false;
  }
  const ready = await store.checkUploadPreflight();
  if (!ready) return false;

  loading.value = true;
  const { error, data: progress } = await request<Api.KnowledgeBase.Progress>({
    url: '/upload/status',
    params: { file_md5: row.fileMd5 }
  });
  if (!error) {
    row.file = options.file.file!;
    row.uploadError = undefined;
    row.status = UploadStatus.Pending;
    row.progress = progress.progress;
    row.uploadedChunks = progress.uploaded;
    store.startUpload();
    loading.value = false;
    return true;
  }
  loading.value = false;
  return false;
}
</script>

<template>
  <div class="paper-page min-h-500px flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <div class="knowledge-overview">
      <div class="overview-card">
        <span class="overview-icon">
          <icon-solar:documents-bold-duotone />
        </span>
        <div>
          <p>知识文件</p>
          <strong>{{ totalCount }}</strong>
        </div>
      </div>
      <div class="overview-card">
        <span class="overview-icon success">
          <icon-solar:check-circle-bold-duotone />
        </span>
        <div>
          <p>可检索</p>
          <strong>{{ indexedCount }}</strong>
        </div>
      </div>
      <div class="overview-card">
        <span class="overview-icon warning">
          <icon-solar:lock-keyhole-bold-duotone />
        </span>
        <div>
          <p>部门知识</p>
          <strong>{{ departmentCount }}</strong>
        </div>
      </div>
      <div class="overview-card">
        <span class="overview-icon info">
          <icon-solar:refresh-circle-bold-duotone />
        </span>
        <div>
          <p>索引中</p>
          <strong>{{ indexPendingCount }}</strong>
        </div>
      </div>
    </div>

    <NCard :bordered="false" size="small" class="paper-filter">
      <NForm label-placement="left" :show-feedback="false" inline>
        <NFormItem label="文件名">
          <NInput v-model:value="filterModel.keyword" placeholder="搜索文件名" clearable class="w-220px!" />
        </NFormItem>
        <NFormItem label="知识类型">
          <NSelect
            v-model:value="filterModel.knowledgeScope"
            :options="knowledgeScopeOptions"
            placeholder="全部类型"
            clearable
            class="w-180px!"
          />
        </NFormItem>
        <NFormItem label="所属部门">
          <OrgTagCascader v-if="authStore.isSuperAdmin" v-model:value="filterModel.departmentId" clearable class="w-220px!" />
          <TheSelect
            v-else
            v-model:value="filterModel.departmentId"
            url="/users/org-tags"
            key-field="orgTagDetails"
            label-field="name"
            value-field="tagId"
            clearable
            class="w-220px!"
          />
        </NFormItem>
        <NFormItem>
          <NButton ghost @click="resetFilters">重置</NButton>
        </NFormItem>
      </NForm>
    </NCard>

    <NCard title="知识资产" :bordered="false" size="small" class="paper-card sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <TableHeaderOperation
          v-model:columns="columnChecks"
          :addable="canUploadKnowledge"
          :loading="loading"
          @add="handleUpload"
          @refresh="getList"
        >
          <template #prefix>
            <NPopover trigger="click" placement="bottom-start" class="upload-service-popover">
              <template #trigger>
                <NButton size="small" ghost :type="uploadServiceTag.type" :loading="uploadPreflightLoading">
                  {{ uploadServiceTag.label }}
                </NButton>
              </template>
              <div class="upload-service-panel">
                <div class="service-panel-title">上传服务状态</div>
                <div class="service-list">
                  <div v-for="item in uploadServiceComponents" :key="item.key" class="service-row">
                    <span>{{ item.label }}</span>
                    <NTag size="small" :type="item.data?.status === 'UP' ? 'success' : 'error'">
                      {{ item.data?.status || 'UNKNOWN' }}
                    </NTag>
                  </div>
                </div>
                <div v-if="uploadPreflight?.message" class="service-message">{{ uploadPreflight.message }}</div>
                <div class="service-command">{{ uploadServiceCommand }}</div>
                <NButton size="small" ghost :loading="uploadPreflightLoading" @click="store.refreshUploadPreflight">
                  重新检查
                </NButton>
              </div>
            </NPopover>
            <NTag :type="accessModeTag.type" :bordered="false">{{ accessModeTag.label }}</NTag>
            <NButton size="small" ghost type="primary" @click="handleSearch">
              <template #icon>
                <icon-ic-round-search class="text-icon" />
              </template>
              检索知识库
            </NButton>
          </template>
        </TableHeaderOperation>
      </template>
      <NDataTable
        striped
        :columns="columns"
        :data="filteredTasks"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="962"
        :loading="loading"
        remote
        :row-key="row => row.fileMd5"
        :pagination="false"
        class="sm:h-full"
      />
    </NCard>
    <UploadDialog v-model:visible="uploadVisible" />
    <SearchDialog v-model:visible="searchVisible" />

    <!-- 文件预览弹窗 -->
    <NModal v-model:show="previewVisible" preset="card" title="文件预览" class="paper-modal max-w-1000px w-[80%]">
      <FilePreview :file-name="previewFileName" :visible="previewVisible" @close="closeFilePreview" />
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.knowledge-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 86px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: rgb(var(--container-bg-color));
  padding: 16px;
  box-shadow: 0 10px 28px -24px rgb(15 23 42 / 0.28);
}

.overview-card p {
  margin: 0;
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 13px;
}

.overview-card strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
  line-height: 1;
}

.overview-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  color: rgb(var(--primary-color));
  background: rgb(var(--primary-color) / 0.08);
  font-size: 24px;
}

.overview-icon.success {
  color: rgb(var(--success-color));
  background: rgb(var(--success-color) / 0.1);
}

.overview-icon.warning {
  color: rgb(var(--warning-color));
  background: rgb(var(--warning-color) / 0.1);
}

.overview-icon.info {
  color: rgb(var(--info-color));
  background: rgb(var(--info-color) / 0.1);
}

html.dark .overview-card {
  border-color: rgb(255 255 255 / 0.08);
  box-shadow: 0 18px 40px -30px rgb(0 0 0 / 0.5);
}

.upload-service-panel {
  width: min(320px, calc(100vw - 48px));
  font-size: 13px;
}

.service-panel-title {
  margin-bottom: 10px;
  font-weight: 600;
}

.service-list {
  display: grid;
  gap: 8px;
}

.service-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.service-message {
  margin-top: 12px;
  color: rgb(var(--error-color));
  line-height: 1.5;
}

.service-command {
  margin: 12px 0;
  border-radius: 6px;
  background: rgb(15 23 42 / 0.06);
  padding: 8px 10px;
  color: rgb(var(--base-text-color) / 0.78);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.5;
  word-break: break-all;
}

html.dark .service-command {
  background: rgb(255 255 255 / 0.08);
}

@media (width < 1024px) {
  .knowledge-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width < 640px) {
  .knowledge-overview {
    grid-template-columns: 1fr;
  }
}

:deep() {
  .n-progress-icon.n-progress-icon--as-text {
    white-space: nowrap;
  }
}
</style>
