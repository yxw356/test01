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
import { fetchGetOrgTagList } from '@/service/api/org-tag';

const appStore = useAppStore();
const authStore = useAuthStore();

// 文件预览相关状态
const previewVisible = ref(false);
const previewFileName = ref('');

const kbFilterOptions = ref<{ label: string; value: string }[]>([]);

function apiFn(params?: { orgTag?: string | null }) {
  const query = params?.orgTag ? { orgTag: params.orgTag } : undefined;
  return fakePaginationRequest<Api.KnowledgeBase.List>({ url: '/documents/accessible', params: query });
}

function flattenKbTags(nodes: Api.OrgTag.Item[]): { label: string; value: string }[] {
  const out: { label: string; value: string }[] = [];
  function walk(list: Api.OrgTag.Item[]) {
    list.forEach(n => {
      if (n.tagId?.startsWith('KB_')) {
        out.push({ label: n.name || n.tagId, value: n.tagId });
      }
      if (n.children?.length) walk(n.children);
    });
  }
  walk(nodes);
  return out;
}

async function loadKbFilterOptions() {
  if (authStore.isAdmin) {
    const { error, data } = await fetchGetOrgTagList();
    if (!error && data?.length) {
      kbFilterOptions.value = flattenKbTags(data);
      return;
    }
  }
  const { error, data } = await request<Api.OrgTag.Mine>({ url: '/users/org-tags' });
  if (!error && data?.orgTagDetails) {
    kbFilterOptions.value = data.orgTagDetails
      .filter(t => t.tagId?.startsWith('KB_'))
      .map(t => ({ label: t.name, value: t.tagId }));
  }
}

function renderIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    if (uploadAccept.split(',').includes(`.${ext}`)) return <SvgIcon localIcon={ext} class="mx-4 text-12" />;
    return <SvgIcon icon="mdi:file-document-outline" class="mx-4 text-12" />;
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

const { columns, columnChecks, data, getData, loading, updateSearchParams } = useTable({
  apiFn,
  apiParams: { orgTag: null as string | null },
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
      key: 'orgTagName',
      title: '组织标签',
      width: 150,
      ellipsis: { tooltip: true, lineClamp: 2 }
    },
    {
      key: 'isPublic',
      title: '是否公开',
      width: 100,
      render: row => (row.public || row.isPublic ? <NTag type="success">公开</NTag> : <NTag type="warning">私有</NTag>)
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
        </div>
      )
    }
  ]
});

const store = useKnowledgeBaseStore();
const { tasks } = storeToRefs(store);

const totalCount = computed(() => tasks.value.length);
const completedCount = computed(() => tasks.value.filter(item => item.status === UploadStatus.Completed).length);
const privateCount = computed(() => tasks.value.filter(item => !(item.public || item.isPublic)).length);
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

const filterOrgTag = ref<string | null>(null);

async function onKbFilterChange(tagId: string | null) {
  filterOrgTag.value = tagId;
  updateSearchParams({ orgTag: tagId });
  await getList();
}

onMounted(async () => {
  await loadKbFilterOptions();
  await getList();
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

/** 根据后端返回字段解析上传状态（兼容 status 未回写但已合并/已索引的记录） */
function resolveUploadStatus(item: Api.KnowledgeBase.UploadTask): UploadStatus {
  if (item.status === UploadStatus.Completed) {
    return UploadStatus.Completed;
  }
  if (item.mergedAt || (item.indexStatus !== undefined && item.indexStatus >= IndexStatus.Pending)) {
    return UploadStatus.Completed;
  }
  if (item.status === UploadStatus.Uploading) {
    return UploadStatus.Break;
  }
  return UploadStatus.Break;
}

/** 异步获取列表函数 该函数主要用于更新或初始化上传任务列表 它首先调用getData函数获取数据，然后根据获取到的数据状态更新任务列表 */
async function getList() {
  // 等待获取最新数据
  await getData();

  if (data.value.length === 0) {
    tasks.value = [];
    return;
  }

  // 遍历获取到的数据，以处理每个项目
  data.value.forEach(item => {
    const resolvedStatus = resolveUploadStatus(item);
    item.status = resolvedStatus;

    // 检查项目状态是否为已完成
    if (resolvedStatus === UploadStatus.Completed) {
      // 查找任务列表中是否有匹配的文件MD5
      const index = tasks.value.findIndex(task => task.fileMd5 === item.fileMd5);
      // 如果找到匹配项，则更新其状态
      if (index !== -1) {
        tasks.value[index].status = UploadStatus.Completed;
        tasks.value[index].indexStatus = item.indexStatus;
        tasks.value[index].indexError = item.indexError;
      } else {
        // 如果没有找到匹配项，则将该项目添加到任务列表中
        tasks.value.push(item);
      }
    } else if (!tasks.value.some(task => task.fileMd5 === item.fileMd5)) {
      // 未完成的上传任务（无本地 File 对象时需通过续传按钮重新选择文件）
      tasks.value.push(item);
    }
  });
}

async function handleDelete(fileMd5: string) {
  const index = tasks.value.findIndex(task => task.fileMd5 === fileMd5);

  if (index !== -1) {
    tasks.value[index].requestIds?.forEach(requestId => {
      request.cancelRequest(requestId);
    });
  }

  // 如果文件一个分片也没有上传完成，则直接删除
  if (tasks.value[index].uploadedChunks && tasks.value[index].uploadedChunks.length === 0) {
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
function handleUpload() {
  uploadVisible.value = true;
}
// #endregion

// #region 检索知识库
const searchVisible = ref(false);
function handleSearch() {
  searchVisible.value = true;
}
// #endregion

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
          续传
        </NButton>
      </NUpload>
    );
  }
  return null;
}

// 任务列表存在文件，直接续传
function resumeUpload(row: Api.KnowledgeBase.UploadTask) {
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
  loading.value = true;
  const { error, data: progress } = await request<Api.KnowledgeBase.Progress>({
    url: '/upload/status',
    params: { file_md5: row.fileMd5 }
  });
  if (!error) {
    row.file = options.file.file!;
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
          <p>私有文档</p>
          <strong>{{ privateCount }}</strong>
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

    <NCard title="知识资产" :bordered="false" size="small" class="paper-card sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <TableHeaderOperation v-model:columns="columnChecks" :loading="loading" @add="handleUpload" @refresh="getList">
          <template #prefix>
            <NSelect
              v-if="kbFilterOptions.length"
              v-model:value="filterOrgTag"
              class="w-180px!"
              size="small"
              clearable
              placeholder="业务知识库"
              :options="kbFilterOptions"
              @update:value="onKbFilterChange"
            />
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
        :data="tasks"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="962"
        :loading="loading"
        remote
        :row-key="row => row.id"
        :pagination="false"
        class="sm:h-full"
      />
    </NCard>
    <UploadDialog v-model:visible="uploadVisible" />
    <SearchDialog v-model:visible="searchVisible" />

    <!-- 文件预览弹窗 -->
    <NModal
      v-model:show="previewVisible"
      preset="card"
      title="文件预览"
      class="paper-modal max-w-1000px w-[80%]"
      @after-leave="closeFilePreview"
    >
      <FilePreview
        v-if="previewFileName"
        in-modal
        :file-name="previewFileName"
        :visible="previewVisible"
        @close="closeFilePreview"
      />
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
