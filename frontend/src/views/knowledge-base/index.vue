<script setup lang="tsx">
import type { UploadFileInfo } from 'naive-ui';
import { NButton, NEllipsis, NModal, NPopconfirm, NProgress, NTag, NTooltip, NUpload } from 'naive-ui';
import { VueDraggable } from 'vue-draggable-plus';
import { uploadAccept } from '@/constants/common';
import { fakePaginationRequest } from '@/service/request';
import { UploadStatus, IndexStatus } from '@/enum';
import SvgIcon from '@/components/custom/svg-icon.vue';
import FilePreview from '@/components/custom/file-preview.vue';
import {
  ACTIVE_KNOWLEDGE_SPACE_KEY,
  applySpaceLayout,
  buildKnowledgeSpaces,
  filterTasksBySpace,
  type KnowledgeSpace
} from './utils/knowledge-space';
import UploadDialog from './modules/upload-dialog.vue';
import SearchDialog from './modules/search-dialog.vue';

const appStore = useAppStore();
const authStore = useAuthStore();

// 文件预览相关状态
const previewVisible = ref(false);
const previewFileName = ref('');
const cleaningDetailVisible = ref(false);
const cleaningDetailTask = ref<Api.KnowledgeBase.UploadTask | null>(null);
const recleaning = ref(false);
const recleanRuleSetId = ref<number | null>(null);

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

async function openCleaningDetail(row: Api.KnowledgeBase.UploadTask) {
  cleaningDetailTask.value = row;
  recleanRuleSetId.value = row.cleaningRuleSetId ?? null;
  cleaningDetailVisible.value = true;
  if (cleaningRuleSets.value.length === 0) {
    await store.refreshCleaningRuleSets();
  }
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
      key: 'cleaningStatus',
      title: '清洗状态',
      width: 130,
      render: row => renderCleaningStatus(row)
    },
    {
      key: 'cleaningRuleName',
      title: '清洗规则',
      width: 150,
      render: row => renderCleaningRule(row)
    },
    {
      key: 'knowledgeScope',
      title: '知识类型',
      width: 120,
      render: row => renderKnowledgeScope(row)
    },
    {
      key: 'categoryName',
      title: '知识分类',
      width: 140,
      render: row => renderCategory(row)
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
          {renderPreviewButton(row)}
          {renderDeleteButton(row)}
        </div>
      )
    }
  ]
});

const store = useKnowledgeBaseStore();
const { tasks, uploadPreflight, uploadPreflightLoading } = storeToRefs(store);
const { categories, cleaningRuleSets, cleaningRuleSetLoading } = storeToRefs(store);

const filterModel = reactive({
  keyword: '',
  knowledgeScope: null as 'PUBLIC' | 'DEPARTMENT' | 'PRIVATE' | null,
  departmentId: null as string | null,
  categoryId: null as number | null
});

const knowledgeScopeOptions = [
  { label: '公共知识', value: 'PUBLIC' },
  { label: '部门知识', value: 'DEPARTMENT' },
  { label: '私人知识', value: 'PRIVATE' }
];

const cleaningRuleVisible = ref(false);
const ruleCreateLoading = ref(false);
const cleaningPreviewLoading = ref(false);
const selectedRuleSetId = ref<number | null>(null);
const editingRuleSetId = ref<number | null>(null);
const cleaningPreviewText = ref('企业制度正文\n第 1 页 / 共 3 页\n第一条 公司制度说明。\n第一条 公司制度说明。');
const cleaningPreviewResult = ref<Api.KnowledgeBase.CleaningPreviewResult | null>(null);
const rulePatternText = ref('^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$');
const ruleCreateModel = reactive<Api.KnowledgeBase.CleaningRuleSetCreateForm>({
  name: '',
  description: '',
  knowledgeScope: 'PUBLIC',
  departmentId: null,
  normalizeLineBreaks: true,
  normalizeUnicodeSpaces: true,
  normalizeWhitespace: true,
  trimLines: true,
  collapseBlankLines: true,
  removeDuplicateLines: true,
  minDuplicateLineLength: 8,
  dropLinePatterns: []
});

const knowledgeSpaceLayoutKey = computed(() => `knowledge-space-layout:${authStore.userInfo.id || authStore.userInfo.username || 'guest'}`);
const selectedSpaceKey = computed(() => `knowledge-space-selected:${authStore.userInfo.id || authStore.userInfo.username || 'guest'}`);
const selectedSpaceId = ref<string | null>(null);
const spaceBoardItems = ref<KnowledgeSpace[]>([]);
const serverKnowledgeSpaces = ref<KnowledgeSpace[] | null>(null);

const baseFilteredTasks = computed(() => {
  return tasks.value.filter(item => {
    const keyword = filterModel.keyword.trim().toLowerCase();
    if (keyword && !item.fileName.toLowerCase().includes(keyword)) return false;

    const scope = item.knowledgeScope || (item.public || item.isPublic ? 'PUBLIC' : 'DEPARTMENT');
    if (filterModel.knowledgeScope && scope !== filterModel.knowledgeScope) return false;

    const departmentId = item.departmentId || item.orgTag;
    if (filterModel.departmentId && departmentId !== filterModel.departmentId) return false;
    if (filterModel.categoryId && item.categoryId !== filterModel.categoryId) return false;

    return true;
  });
});

const hasActiveFilters = computed(
  () => Boolean(filterModel.keyword.trim()) || Boolean(filterModel.knowledgeScope) || Boolean(filterModel.departmentId) || Boolean(filterModel.categoryId)
);
const knowledgeSpaces = computed(() => {
  if (!hasActiveFilters.value && serverKnowledgeSpaces.value?.length) return serverKnowledgeSpaces.value;
  return buildKnowledgeSpaces(baseFilteredTasks.value);
});
const currentSpace = computed(() => knowledgeSpaces.value.find(item => item.id === selectedSpaceId.value) || knowledgeSpaces.value[0] || null);
const filteredTasks = computed(() => filterTasksBySpace(baseFilteredTasks.value, selectedSpaceId.value));

const totalCount = computed(() => tasks.value.length);
const completedCount = computed(() => tasks.value.filter(item => item.status === UploadStatus.Completed).length);
const departmentCount = computed(() => tasks.value.filter(item => normalizeScope(item) === 'DEPARTMENT').length);
const categoryCount = computed(() => new Set(tasks.value.map(item => item.categoryId).filter(Boolean)).size);
const processingCount = computed(() => tasks.value.filter(item => item.status !== UploadStatus.Completed).length);
const cleanedCount = computed(() => tasks.value.filter(item => item.cleaningStatus === 'CLEANED').length);

const indexedCount = computed(() =>
  tasks.value.filter(item => item.indexStatus === IndexStatus.Indexed || item.indexStatus === undefined).length
);
const indexPendingCount = computed(() =>
  tasks.value.filter(item =>
    [IndexStatus.Pending, IndexStatus.Indexing].includes(item.indexStatus as IndexStatus)
  ).length
);

function readSpaceOrder() {
  try {
    return JSON.parse(localStorage.getItem(knowledgeSpaceLayoutKey.value) || '[]') as string[];
  } catch {
    return [];
  }
}

function saveSpaceOrder() {
  localStorage.setItem(knowledgeSpaceLayoutKey.value, JSON.stringify(spaceBoardItems.value.map(item => item.id)));
}

function selectSpace(spaceId: string) {
  selectedSpaceId.value = spaceId;
  localStorage.setItem(selectedSpaceKey.value, spaceId);
  const space = spaceBoardItems.value.find(item => item.id === spaceId);
  if (space) {
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
}

function syncSpaceBoardItems() {
  const ordered = applySpaceLayout(knowledgeSpaces.value, readSpaceOrder());
  spaceBoardItems.value = ordered;
  const savedSelected = localStorage.getItem(selectedSpaceKey.value);
  const nextSelected = ordered.some(item => item.id === selectedSpaceId.value)
    ? selectedSpaceId.value
    : ordered.find(item => item.id === savedSelected)?.id || ordered[0]?.id || null;
  selectedSpaceId.value = nextSelected;
  if (nextSelected) selectSpace(nextSelected);
}

function spaceTypeTag(space: KnowledgeSpace) {
  if (space.type === 'PUBLIC') return { label: '公共', type: 'success' as const };
  if (space.type === 'PRIVATE') return { label: '个人', type: 'warning' as const };
  return { label: '部门', type: 'info' as const };
}

function formatSpaceUpdatedAt(space: KnowledgeSpace) {
  if (!space.lastUpdatedAt) return '暂无更新';
  return dayjs(space.lastUpdatedAt).format('YYYY-MM-DD HH:mm');
}

watch([knowledgeSpaces, knowledgeSpaceLayoutKey], syncSpaceBoardItems, { immediate: true });

let indexPollTimer: ReturnType<typeof setInterval> | null = null;

onMounted(async () => {
  await Promise.all([getList(), refreshKnowledgeSpaces(), store.refreshUploadPreflight(), store.refreshCategories()]);
  startIndexPolling();
});

onUnmounted(() => {
  if (indexPollTimer) clearInterval(indexPollTimer);
});

function startIndexPolling() {
  if (indexPollTimer) clearInterval(indexPollTimer);
  indexPollTimer = setInterval(async () => {
    if (document.hidden || loading.value || indexPendingCount.value === 0) return;
    await getList();
  }, 8000);
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

async function refreshKnowledgeSpaces() {
  const { error, data } = await request<Api.KnowledgeBase.KnowledgeSpaceSummary[]>({
    url: '/documents/knowledge-spaces'
  });
  if (!error) {
    serverKnowledgeSpaces.value = data.map(item => ({
      ...item,
      lastUpdatedAt: item.lastUpdatedAt
    }));
  }
}

async function refreshKnowledgeBaseView() {
  await getList();
  await refreshKnowledgeSpaces();
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
    await refreshKnowledgeSpaces();
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

function resetRuleCreateModel() {
  editingRuleSetId.value = null;
  ruleCreateModel.name = '';
  ruleCreateModel.description = '';
  ruleCreateModel.knowledgeScope = authStore.isSuperAdmin || authStore.userInfo.role === 'KNOWLEDGE_ADMIN' ? 'PUBLIC' : 'DEPARTMENT';
  ruleCreateModel.departmentId = null;
  ruleCreateModel.normalizeLineBreaks = true;
  ruleCreateModel.normalizeUnicodeSpaces = true;
  ruleCreateModel.normalizeWhitespace = true;
  ruleCreateModel.trimLines = true;
  ruleCreateModel.collapseBlankLines = true;
  ruleCreateModel.removeDuplicateLines = true;
  ruleCreateModel.minDuplicateLineLength = 8;
  ruleCreateModel.dropLinePatterns = [];
  rulePatternText.value = '^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$';
}

function fillRuleForm(ruleSet: Api.KnowledgeBase.CleaningRuleSet) {
  editingRuleSetId.value = ruleSet.id;
  selectedRuleSetId.value = ruleSet.id;
  ruleCreateModel.name = ruleSet.name;
  ruleCreateModel.description = ruleSet.description || '';
  ruleCreateModel.knowledgeScope = ruleSet.knowledgeScope;
  ruleCreateModel.departmentId = ruleSet.departmentId || null;
  ruleCreateModel.normalizeLineBreaks = ruleSet.normalizeLineBreaks;
  ruleCreateModel.normalizeUnicodeSpaces = ruleSet.normalizeUnicodeSpaces;
  ruleCreateModel.normalizeWhitespace = ruleSet.normalizeWhitespace;
  ruleCreateModel.trimLines = ruleSet.trimLines;
  ruleCreateModel.collapseBlankLines = ruleSet.collapseBlankLines;
  ruleCreateModel.removeDuplicateLines = ruleSet.removeDuplicateLines;
  ruleCreateModel.minDuplicateLineLength = ruleSet.minDuplicateLineLength;
  ruleCreateModel.dropLinePatterns = [...ruleSet.dropLinePatterns];
  rulePatternText.value = ruleSet.dropLinePatterns.join('\n');
}

async function openCleaningRuleManager() {
  cleaningRuleVisible.value = true;
  resetRuleCreateModel();
  cleaningPreviewResult.value = null;
  await store.refreshCleaningRuleSets();
  selectedRuleSetId.value = cleaningRuleSets.value[0]?.id ?? null;
}

function parseRulePatterns() {
  return rulePatternText.value
    .split('\n')
    .map(item => item.trim())
    .filter(Boolean);
}

async function handleCreateRuleSet() {
  const name = ruleCreateModel.name.trim();
  if (!name) {
    window.$message?.warning('请输入规则集名称');
    return;
  }
  if (ruleCreateModel.knowledgeScope === 'DEPARTMENT' && !ruleCreateModel.departmentId) {
    window.$message?.warning('请选择所属部门');
    return;
  }

  ruleCreateLoading.value = true;
  const payload = {
    ...ruleCreateModel,
    name,
    description: ruleCreateModel.description?.trim() || '',
    departmentId: ruleCreateModel.knowledgeScope === 'DEPARTMENT' ? ruleCreateModel.departmentId : null,
    dropLinePatterns: parseRulePatterns()
  };
  const ok = editingRuleSetId.value
    ? await store.updateCleaningRuleSet(editingRuleSetId.value, payload)
    : await store.createCleaningRuleSet(payload);
  ruleCreateLoading.value = false;

  if (ok) {
    const wasEditing = Boolean(editingRuleSetId.value);
    const targetRuleSetId = editingRuleSetId.value;
    const saved = wasEditing
      ? cleaningRuleSets.value.find(item => item.id === targetRuleSetId)
      : cleaningRuleSets.value.find(item => item.name === name);
    selectedRuleSetId.value = saved?.id ?? cleaningRuleSets.value[0]?.id ?? null;
    resetRuleCreateModel();
    window.$message?.success(wasEditing ? '清洗规则集已更新' : '清洗规则集已创建');
  }
}

async function handleDisableRuleSet(ruleSet: Api.KnowledgeBase.CleaningRuleSet) {
  const ok = await store.disableCleaningRuleSet(ruleSet.id);
  if (ok) {
    if (selectedRuleSetId.value === ruleSet.id) selectedRuleSetId.value = cleaningRuleSets.value[0]?.id ?? null;
    if (editingRuleSetId.value === ruleSet.id) resetRuleCreateModel();
    window.$message?.success('清洗规则集已停用');
  }
}

async function handlePreviewCleaning() {
  if (!cleaningPreviewText.value.trim()) {
    window.$message?.warning('请输入预览文本');
    return;
  }

  cleaningPreviewLoading.value = true;
  cleaningPreviewResult.value = await store.previewCleaning({
    rawText: cleaningPreviewText.value,
    ruleSetId: selectedRuleSetId.value,
    ruleConfig: selectedRuleSetId.value
      ? null
      : {
          normalizeLineBreaks: ruleCreateModel.normalizeLineBreaks,
          normalizeUnicodeSpaces: ruleCreateModel.normalizeUnicodeSpaces,
          normalizeWhitespace: ruleCreateModel.normalizeWhitespace,
          trimLines: ruleCreateModel.trimLines,
          collapseBlankLines: ruleCreateModel.collapseBlankLines,
          removeDuplicateLines: ruleCreateModel.removeDuplicateLines,
          minDuplicateLineLength: ruleCreateModel.minDuplicateLineLength,
          dropLinePatterns: parseRulePatterns()
        }
  });
  cleaningPreviewLoading.value = false;
}

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
  filterModel.categoryId = null;
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

function renderCategory(row: Api.KnowledgeBase.UploadTask) {
  if (!row.categoryName) return <NTag bordered={false}>未分类</NTag>;
  return <NTag type="primary">{row.categoryName}</NTag>;
}

function renderCleaningRule(row: Api.KnowledgeBase.UploadTask) {
  if (row.status !== UploadStatus.Completed) return <NTag bordered={false}>-</NTag>;
  return <NTag bordered={false}>{cleaningRuleName(row)}</NTag>;
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
  if (authStore.userInfo.role === 'KNOWLEDGE_ADMIN' && normalizeScope(row) === 'PUBLIC') return true;
  if (String(authStore.userInfo.id) === String(row.userId)) return true;
  if (!authStore.isDeptLead) return false;

  const scope = normalizeScope(row);
  const departmentId = row.departmentId || row.orgTag;
  return scope === 'DEPARTMENT' && Boolean(departmentId) && authStore.userInfo.orgTags.includes(departmentId!);
}

function canPreviewDocument(row: Api.KnowledgeBase.UploadTask) {
  if (typeof row.canPreview === 'boolean') return row.canPreview;
  if (typeof row.canView === 'boolean') return row.canView;
  return true;
}

function canDeleteDocument(row: Api.KnowledgeBase.UploadTask) {
  if (typeof row.canDelete === 'boolean') return row.canDelete;
  return canManageDocument(row);
}

function canRecleanDocument(row: Api.KnowledgeBase.UploadTask) {
  if (typeof row.canReclean === 'boolean') return row.canReclean;
  return canManageDocument(row) && row.status === UploadStatus.Completed;
}

function canReindexDocument(row: Api.KnowledgeBase.UploadTask) {
  if (typeof row.canReindex === 'boolean') return row.canReindex;
  return canManageDocument(row) && row.status === UploadStatus.Completed;
}

function canResumeDocumentUpload(row: Api.KnowledgeBase.UploadTask) {
  if (typeof row.canResumeUpload === 'boolean') return row.canResumeUpload;
  return String(authStore.userInfo.id) === String(row.userId) && row.status !== UploadStatus.Completed;
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

function cleaningRemovedRatio(row: Api.KnowledgeBase.UploadTask) {
  const originalChars = row.originalChars || 0;
  if (!originalChars) return 0;
  return Math.round(((row.removedChars || 0) / originalChars) * 100);
}

function cleaningStatusText(status?: Api.KnowledgeBase.UploadTask['cleaningStatus']) {
  if (status === 'CLEANING') return '清洗中';
  if (status === 'FAILED') return '清洗失败';
  if (status === 'CLEANED') return '已清洗';
  return '待清洗';
}

function cleaningQualityText(status?: Api.KnowledgeBase.UploadTask['cleaningQualityStatus'] | Api.KnowledgeBase.CleaningPreviewResult['qualityStatus']) {
  if (status === 'FAILED') return '质量异常';
  if (status === 'WARNING') return '需检查';
  return '质量正常';
}

function cleaningQualityType(status?: Api.KnowledgeBase.UploadTask['cleaningQualityStatus'] | Api.KnowledgeBase.CleaningPreviewResult['qualityStatus']) {
  if (status === 'FAILED') return 'error';
  if (status === 'WARNING') return 'warning';
  return 'success';
}

function cleaningQualityIssueText(issue: string) {
  const issueMap: Record<string, string> = {
    CLEANED_EMPTY: '清洗后为空',
    CLEANED_TOO_SHORT: '清洗后正文过短',
    REMOVED_RATIO_HIGH: '删除比例过高',
    REMOVED_RATIO_MEDIUM: '删除比例偏高',
    GARBLED_TEXT: '疑似乱码',
    EMPTY_RESULT: '没有清洗结果'
  };
  return issueMap[issue] || issue;
}

function cleaningQualityIssues(row: Api.KnowledgeBase.UploadTask) {
  if (!row.cleaningQualityIssues) return [];
  return row.cleaningQualityIssues.split(',').filter(Boolean);
}

function renderCleaningStatus(row: Api.KnowledgeBase.UploadTask) {
  if (row.status !== UploadStatus.Completed) {
    return <NTag bordered={false}>-</NTag>;
  }
  const status = row.cleaningStatus || 'PENDING';

  if (status === 'CLEANING') {
    return <NTag type="info">清洗中</NTag>;
  }
  if (status === 'FAILED') {
    return <NTag type="error">清洗失败</NTag>;
  }
  if (status === 'CLEANED') {
    return (
      <div class="cleaning-status-cell">
        <NButton text type="success" onClick={() => openCleaningDetail(row)}>
          已清洗 {cleaningRemovedRatio(row)}%
        </NButton>
        <NTag size="small" type={cleaningQualityType(row.cleaningQualityStatus)} bordered={false}>
          {cleaningQualityText(row.cleaningQualityStatus)}
        </NTag>
      </div>
    );
  }
  return <NTag type="warning">待清洗</NTag>;
}

function availableCleaningRuleOptions(row: Api.KnowledgeBase.UploadTask | null) {
  if (!row) return [];
  const scope = normalizeScope(row);
  const departmentId = row.departmentId || row.orgTag;
  return cleaningRuleSets.value
    .filter(item => {
      if (scope === 'PUBLIC') return item.knowledgeScope === 'PUBLIC';
      if (scope === 'DEPARTMENT') {
        return item.knowledgeScope === 'PUBLIC' || item.departmentId === departmentId;
      }
      return false;
    })
    .map(item => ({
      label: `${item.name}${item.knowledgeScope === 'PUBLIC' ? ' · 公共' : ' · 部门'}`,
      value: item.id
    }));
}

function cleaningRuleName(rowOrRuleSetId?: Api.KnowledgeBase.UploadTask | number | null) {
  if (typeof rowOrRuleSetId === 'object' && rowOrRuleSetId) {
    if (rowOrRuleSetId.cleaningRuleName) return rowOrRuleSetId.cleaningRuleName;
    return cleaningRuleName(rowOrRuleSetId.cleaningRuleSetId);
  }
  if (!rowOrRuleSetId) return '默认清洗规则';
  return cleaningRuleSets.value.find(item => item.id === rowOrRuleSetId)?.name || `规则集 #${rowOrRuleSetId}`;
}

function renderReindexButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status !== UploadStatus.Completed) return null;
  if (!canReindexDocument(row)) return null;
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

function renderPreviewButton(row: Api.KnowledgeBase.UploadTask) {
  if (!canPreviewDocument(row)) return null;
  return (
    <NButton type="primary" ghost size="small" onClick={() => handleFilePreview(row.fileName)}>
      预览
    </NButton>
  );
}

function renderDeleteButton(row: Api.KnowledgeBase.UploadTask) {
  if (!canDeleteDocument(row)) return null;
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

async function handleReclean(row: Api.KnowledgeBase.UploadTask) {
  if (!canRecleanDocument(row)) {
    window.$message?.warning('当前账号暂无重新清洗此文档的权限');
    return;
  }
  recleaning.value = true;
  try {
    const { error } = await request({
      url: `/documents/${row.fileMd5}/reclean`,
      method: 'POST',
      data: { cleaningRuleSetId: recleanRuleSetId.value }
    });

    if (!error) {
      Object.assign(row, {
        cleaningRuleSetId: recleanRuleSetId.value,
        cleaningStatus: 'PENDING',
        originalChars: 0,
        cleanedChars: 0,
        removedChars: 0,
        duplicateLinesRemoved: 0,
        indexStatus: IndexStatus.Pending,
        indexError: null
      });
      window.$message?.success('清洗与索引任务已重新提交');
      await getList();
      const latest = tasks.value.find(task => task.fileMd5 === row.fileMd5);
      if (latest) cleaningDetailTask.value = latest;
    }
  } finally {
    recleaning.value = false;
  }
}

// #region 文件续传
function renderResumeUploadButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status === UploadStatus.Break && canResumeDocumentUpload(row)) {
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
  if (!canResumeDocumentUpload(row)) {
    window.$message?.warning('只能续传自己上传中断的文件');
    return;
  }
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
  if (!canResumeDocumentUpload(row)) {
    window.$message?.warning('只能续传自己上传中断的文件');
    return false;
  }
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
          <p>知识分类</p>
          <strong>{{ categoryCount }}</strong>
        </div>
      </div>
      <div class="overview-card">
        <span class="overview-icon info">
          <icon-solar:refresh-circle-bold-duotone />
        </span>
        <div>
          <p>已清洗</p>
          <strong>{{ cleanedCount }}</strong>
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
        <NFormItem label="知识分类">
          <NSelect
            v-model:value="filterModel.categoryId"
            :options="categories.map(item => ({ label: item.name, value: item.id }))"
            placeholder="全部分类"
            clearable
            class="w-180px!"
          />
        </NFormItem>
        <NFormItem>
          <NButton ghost @click="resetFilters">重置</NButton>
        </NFormItem>
      </NForm>
    </NCard>

    <section class="knowledge-space-section">
      <div class="section-title">
        <div>
          <strong>知识库分区</strong>
          <span>公共知识库和部门知识库按块管理，可拖动调整顺序</span>
        </div>
        <NTag v-if="currentSpace" :type="spaceTypeTag(currentSpace).type" :bordered="false">
          当前：{{ currentSpace.title }}
        </NTag>
      </div>
      <VueDraggable v-model="spaceBoardItems" :animation="180" class="knowledge-space-grid" @end="saveSpaceOrder">
        <button
          v-for="(space, index) in spaceBoardItems"
          :key="space.id"
          class="knowledge-space-card"
          :class="[space.type.toLowerCase(), `space-color-${index % 7}`, { active: selectedSpaceId === space.id }]"
          type="button"
          @click="selectSpace(space.id)"
        >
          <div class="space-card-header">
            <span class="space-icon" :class="space.type.toLowerCase()">
              <icon-solar:folder-with-files-bold-duotone />
            </span>
            <NTag size="small" :type="spaceTypeTag(space).type" :bordered="false">{{ spaceTypeTag(space).label }}</NTag>
          </div>
          <strong>{{ space.title }}</strong>
          <div class="space-stats">
            <span>
              <b>{{ space.fileCount }}</b>
              文件
            </span>
            <span>
              <b>{{ space.indexedCount }}</b>
              可检索
            </span>
            <span>
              <b>{{ space.cleaningIssueCount }}</b>
              清洗异常
            </span>
          </div>
          <div class="space-footer">
            <span>{{ formatSpaceUpdatedAt(space) }}</span>
            <span v-if="space.interruptedCount">中断 {{ space.interruptedCount }}</span>
          </div>
        </button>
      </VueDraggable>
    </section>

    <NCard :title="currentSpace ? `${currentSpace.title} · 文件` : '知识资产'" :bordered="false" size="small" class="paper-card sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <TableHeaderOperation
          v-model:columns="columnChecks"
          :addable="canUploadKnowledge"
          :loading="loading"
          @add="handleUpload"
          @refresh="refreshKnowledgeBaseView"
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
            <NButton size="small" ghost type="info" @click="openCleaningRuleManager">
              清洗规则
            </NButton>
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
        :scroll-x="1242"
        :loading="loading"
        remote
        :row-key="row => row.fileMd5"
        :pagination="false"
        class="sm:h-full"
      />
    </NCard>
    <UploadDialog v-model:visible="uploadVisible" :initial-space="currentSpace" />
    <SearchDialog v-model:visible="searchVisible" />

    <NModal v-model:show="cleaningRuleVisible" preset="card" title="清洗规则集" class="paper-modal max-w-980px w-[94%]">
      <div class="rule-manager">
        <section class="rule-panel">
          <div class="rule-panel-title">
            <strong>已有规则集</strong>
            <NButton size="tiny" ghost :loading="cleaningRuleSetLoading" @click="store.refreshCleaningRuleSets">
              刷新
            </NButton>
          </div>
          <NSelect
            v-model:value="selectedRuleSetId"
            :options="cleaningRuleSets.map(item => ({ label: `${item.name} · ${item.knowledgeScope === 'PUBLIC' ? '公共' : item.departmentId || '部门'}`, value: item.id }))"
            placeholder="选择规则集"
            clearable
            :loading="cleaningRuleSetLoading"
          />
          <div class="rule-list">
            <div v-for="item in cleaningRuleSets" :key="item.id" class="rule-list-item">
              <div>
                <strong>{{ item.name }}</strong>
                <span>{{ item.description || '未填写说明' }}</span>
              </div>
              <div class="rule-list-actions">
                <NTag :type="item.knowledgeScope === 'PUBLIC' ? 'success' : 'info'" size="small">
                  {{ item.knowledgeScope === 'PUBLIC' ? '公共' : item.departmentId || '部门' }}
                </NTag>
                <NButton size="tiny" text type="primary" @click="fillRuleForm(item)">编辑</NButton>
                <NPopconfirm @positive-click="handleDisableRuleSet(item)">
                  <template #trigger>
                    <NButton size="tiny" text type="error">停用</NButton>
                  </template>
                  停用后上传和重清洗时将不再可选，确认继续吗？
                </NPopconfirm>
              </div>
            </div>
            <div v-if="!cleaningRuleSets.length" class="empty-line">暂无规则集</div>
          </div>
        </section>

        <section class="rule-panel">
          <div class="rule-panel-title">
            <strong>{{ editingRuleSetId ? '编辑规则集' : '新建规则集' }}</strong>
            <NButton v-if="editingRuleSetId" size="tiny" ghost @click="resetRuleCreateModel">取消编辑</NButton>
          </div>
          <div class="rule-form-grid">
            <NInput v-model:value="ruleCreateModel.name" placeholder="规则集名称" />
            <NSelect
              v-model:value="ruleCreateModel.knowledgeScope"
              :options="knowledgeScopeOptions.filter(item => item.value !== 'PRIVATE')"
              placeholder="适用范围"
            />
            <OrgTagCascader
              v-if="ruleCreateModel.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin"
              v-model:value="ruleCreateModel.departmentId"
              clearable
            />
            <TheSelect
              v-else-if="ruleCreateModel.knowledgeScope === 'DEPARTMENT'"
              v-model:value="ruleCreateModel.departmentId"
              url="/users/org-tags"
              key-field="orgTagDetails"
              label-field="name"
              value-field="tagId"
              clearable
            />
          </div>
          <NInput v-model:value="ruleCreateModel.description" placeholder="说明" />
          <div class="rule-switches">
            <NCheckbox v-model:checked="ruleCreateModel.normalizeWhitespace">压缩空白</NCheckbox>
            <NCheckbox v-model:checked="ruleCreateModel.collapseBlankLines">折叠空行</NCheckbox>
            <NCheckbox v-model:checked="ruleCreateModel.removeDuplicateLines">删除重复长行</NCheckbox>
            <NCheckbox v-model:checked="ruleCreateModel.normalizeUnicodeSpaces">归一特殊空格</NCheckbox>
          </div>
          <div class="rule-inline">
            <span>重复行最小长度</span>
            <NInputNumber v-model:value="ruleCreateModel.minDuplicateLineLength" :min="1" :max="200" class="w-120px" />
          </div>
          <NInput
            v-model:value="rulePatternText"
            type="textarea"
            placeholder="每行一条要删除整行的正则，例如 ^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$"
            :autosize="{ minRows: 3, maxRows: 5 }"
          />
          <div class="detail-actions">
            <NButton type="primary" ghost :loading="ruleCreateLoading" @click="handleCreateRuleSet">
              {{ editingRuleSetId ? '保存修改' : '保存规则集' }}
            </NButton>
          </div>
        </section>

        <section class="rule-panel rule-preview">
          <div class="rule-panel-title">
            <strong>清洗预览</strong>
            <NButton size="small" type="primary" ghost :loading="cleaningPreviewLoading" @click="handlePreviewCleaning">
              预览
            </NButton>
          </div>
          <div class="preview-grid">
            <div class="preview-pane">
              <div class="preview-pane-title">
                <strong>原文</strong>
                <NTag size="small">{{ cleaningPreviewText.length }} 字</NTag>
              </div>
              <NInput v-model:value="cleaningPreviewText" type="textarea" :autosize="{ minRows: 9, maxRows: 13 }" />
            </div>
            <div class="preview-output">
              <div class="preview-pane-title">
                <strong>清洗后</strong>
                <span>左右对照便于检查误删内容</span>
              </div>
              <div class="preview-stats">
                <NTag size="small">原始 {{ cleaningPreviewResult?.originalChars || 0 }}</NTag>
                <NTag size="small" type="success">清洗后 {{ cleaningPreviewResult?.cleanedChars || 0 }}</NTag>
                <NTag size="small" type="warning">删除 {{ cleaningPreviewResult?.removedChars || 0 }}</NTag>
                <NTag size="small" type="info">重复行 {{ cleaningPreviewResult?.duplicateLinesRemoved || 0 }}</NTag>
                <NTag size="small" :type="cleaningQualityType(cleaningPreviewResult?.qualityStatus)">
                  {{ cleaningQualityText(cleaningPreviewResult?.qualityStatus) }}
                </NTag>
              </div>
              <div v-if="cleaningPreviewResult?.qualityIssues?.length" class="quality-issues">
                <NTag v-for="issue in cleaningPreviewResult.qualityIssues" :key="issue" size="small" type="warning">
                  {{ cleaningQualityIssueText(issue) }}
                </NTag>
              </div>
              <pre>{{ cleaningPreviewResult?.cleanedText || '点击预览后显示清洗结果' }}</pre>
            </div>
          </div>
        </section>
      </div>
    </NModal>

    <NModal v-model:show="cleaningDetailVisible" preset="card" title="清洗详情" class="paper-modal max-w-620px w-[92%]">
      <div v-if="cleaningDetailTask" class="cleaning-detail">
        <div class="detail-file">
          <strong>{{ cleaningDetailTask.fileName }}</strong>
          <NTag :type="cleaningDetailTask.cleaningStatus === 'CLEANED' ? 'success' : 'warning'" :bordered="false">
            {{ cleaningStatusText(cleaningDetailTask.cleaningStatus) }}
          </NTag>
          <NTag :type="cleaningQualityType(cleaningDetailTask.cleaningQualityStatus)" :bordered="false">
            {{ cleaningQualityText(cleaningDetailTask.cleaningQualityStatus) }}
          </NTag>
        </div>
        <div class="detail-grid">
          <div>
            <span>原始字符</span>
            <strong>{{ cleaningDetailTask.originalChars || 0 }}</strong>
          </div>
          <div>
            <span>清洗后字符</span>
            <strong>{{ cleaningDetailTask.cleanedChars || 0 }}</strong>
          </div>
          <div>
            <span>删除字符</span>
            <strong>{{ cleaningDetailTask.removedChars || 0 }}</strong>
          </div>
          <div>
            <span>重复行</span>
            <strong>{{ cleaningDetailTask.duplicateLinesRemoved || 0 }}</strong>
          </div>
          <div>
            <span>质量分</span>
            <strong>{{ cleaningDetailTask.cleaningQualityScore ?? '-' }}</strong>
          </div>
        </div>
        <div v-if="cleaningQualityIssues(cleaningDetailTask).length" class="quality-issues">
          <NTag v-for="issue in cleaningQualityIssues(cleaningDetailTask)" :key="issue" size="small" type="warning">
            {{ cleaningQualityIssueText(issue) }}
          </NTag>
        </div>
        <div class="detail-ratio">
          <span>清洗压缩比例</span>
          <NProgress
            type="line"
            :percentage="cleaningRemovedRatio(cleaningDetailTask)"
            :show-indicator="true"
            status="success"
          />
        </div>
        <div class="detail-rule">
          <span>当前规则</span>
          <strong>{{ cleaningRuleName(cleaningDetailTask) }}</strong>
        </div>
        <div v-if="canRecleanDocument(cleaningDetailTask)" class="detail-actions">
          <NSelect
            v-model:value="recleanRuleSetId"
            :options="availableCleaningRuleOptions(cleaningDetailTask)"
            :loading="cleaningRuleSetLoading"
            clearable
            placeholder="默认清洗规则"
            class="min-w-240px"
          />
          <NPopconfirm @positive-click="handleReclean(cleaningDetailTask)">
            <template #trigger>
              <NButton
                type="warning"
                ghost
                :loading="recleaning"
                :disabled="cleaningDetailTask.status !== UploadStatus.Completed"
              >
                重新清洗并索引
              </NButton>
            </template>
            重新清洗会使用当前选择的清洗规则，覆盖清洗统计并重新生成检索索引，确认继续吗？
          </NPopconfirm>
        </div>
      </div>
    </NModal>

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

.knowledge-space-section {
  position: relative;
  display: grid;
  gap: 12px;
  overflow: hidden;
  border: 1px solid rgb(15 23 42 / 0.1);
  border-radius: 8px;
  background: rgb(var(--container-bg-color));
  padding: 16px;
  box-shadow: 0 16px 44px -36px rgb(15 23 42 / 0.42);
}

.section-title {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title > div {
  display: grid;
  gap: 2px;
}

.section-title strong {
  color: rgb(25 38 44);
  font-size: 17px;
  line-height: 1.3;
}

.section-title span {
  color: rgb(48 65 68 / 0.68);
  font-size: 12px;
}

.knowledge-space-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
  gap: 12px;
}

.knowledge-space-card {
  position: relative;
  display: grid;
  gap: 12px;
  min-height: 154px;
  overflow: hidden;
  border: 1px solid rgb(20 31 39 / 0.18);
  border-radius: 8px;
  background:
    linear-gradient(145deg, rgb(255 255 255 / 0.86), rgb(238 243 242 / 0.8)),
    rgb(255 255 255 / 0.72);
  padding: 14px;
  color: inherit;
  text-align: left;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.knowledge-space-card::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background:
    repeating-linear-gradient(
      168deg,
      rgb(255 255 255 / 0.18) 0,
      rgb(255 255 255 / 0.18) 5px,
      transparent 5px,
      transparent 18px
    ),
    linear-gradient(90deg, transparent, rgb(255 255 255 / 0.32), transparent);
  opacity: 0.9;
}

.knowledge-space-card::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 44px;
  pointer-events: none;
  content: '';
  background: linear-gradient(90deg, rgb(213 180 106 / 0.18), rgb(76 111 132 / 0.14), transparent);
}

.knowledge-space-card.space-color-0 {
  background:
    linear-gradient(145deg, #fee2e2, #fecaca 52%, #fff7f7),
    rgb(255 255 255);
}

.knowledge-space-card.space-color-1 {
  background:
    linear-gradient(145deg, #ffedd5, #fed7aa 52%, #fff7ed),
    rgb(255 255 255);
}

.knowledge-space-card.space-color-2 {
  background:
    linear-gradient(145deg, #fef9c3, #fde68a 52%, #fffbea),
    rgb(255 255 255);
}

.knowledge-space-card.space-color-3 {
  background:
    linear-gradient(145deg, #dcfce7, #bbf7d0 52%, #f0fdf4),
    rgb(255 255 255);
}

.knowledge-space-card.space-color-4 {
  background:
    linear-gradient(145deg, #dbeafe, #bfdbfe 52%, #eff6ff),
    rgb(255 255 255);
}

.knowledge-space-card.space-color-5 {
  background:
    linear-gradient(145deg, #e0e7ff, #c7d2fe 52%, #eef2ff),
    rgb(255 255 255);
}

.knowledge-space-card.space-color-6 {
  background:
    linear-gradient(145deg, #f3e8ff, #e9d5ff 52%, #faf5ff),
    rgb(255 255 255);
}

.knowledge-space-card:hover,
.knowledge-space-card.active {
  border-color: rgb(42 76 91 / 0.62);
  box-shadow:
    0 22px 42px -30px rgb(30 58 70 / 0.72),
    inset 0 0 0 1px rgb(255 255 255 / 0.5);
  transform: translateY(-2px);
}

.knowledge-space-card.active {
  outline: 2px solid rgb(213 180 106 / 0.42);
  outline-offset: -4px;
}

.space-card-header,
.space-footer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.space-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  border: 1px solid rgb(255 255 255 / 0.64);
  color: rgb(52 90 96);
  background: rgb(255 255 255 / 0.46);
  font-size: 22px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.5);
}

.space-icon.public {
  color: rgb(76 117 74);
  background: rgb(236 247 219 / 0.72);
}

.space-icon.department {
  color: rgb(54 94 119);
  background: rgb(223 240 249 / 0.74);
}

.space-icon.private {
  color: rgb(136 101 51);
  background: rgb(251 236 207 / 0.74);
}

.knowledge-space-card strong {
  position: relative;
  z-index: 1;
  min-width: 0;
  overflow: hidden;
  color: rgb(25 38 44);
  font-size: 15px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.space-stats span {
  display: grid;
  gap: 2px;
  border: 1px solid rgb(255 255 255 / 0.58);
  border-radius: 8px;
  background: rgb(255 255 255 / 0.5);
  padding: 8px;
  color: rgb(47 68 70 / 0.74);
  font-size: 12px;
  backdrop-filter: blur(6px);
}

.space-stats b {
  color: rgb(28 53 60);
  font-size: 17px;
  line-height: 1;
}

.space-footer {
  min-height: 20px;
  color: rgb(59 76 76 / 0.68);
  font-size: 12px;
}

html.dark .knowledge-space-section,
html.dark .knowledge-space-card {
  border-color: rgb(255 255 255 / 0.08);
}

html.dark .knowledge-space-section {
  background:
    linear-gradient(135deg, rgb(31 47 50 / 0.98), rgb(25 42 49 / 0.95) 48%, rgb(48 45 35 / 0.88)),
    rgb(var(--container-bg-color));
}

html.dark .knowledge-space-section::before {
  background:
    repeating-linear-gradient(
      116deg,
      rgb(255 255 255 / 0.05) 0,
      rgb(255 255 255 / 0.05) 2px,
      transparent 2px,
      transparent 12px
    ),
    linear-gradient(90deg, rgb(137 172 156 / 0.08), transparent 38%, rgb(220 184 121 / 0.08));
  mix-blend-mode: screen;
}

html.dark .section-title strong,
html.dark .knowledge-space-card strong,
html.dark .space-stats b {
  color: rgb(231 241 235);
}

html.dark .section-title span,
html.dark .space-footer,
html.dark .space-stats span {
  color: rgb(224 235 229 / 0.72);
}

html.dark .knowledge-space-card {
  background:
    linear-gradient(145deg, rgb(255 255 255 / 0.08), rgb(142 178 168 / 0.1)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.space-color-0 {
  background:
    linear-gradient(145deg, rgb(185 28 28 / 0.34), rgb(127 29 29 / 0.2) 52%, rgb(248 113 113 / 0.12)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.space-color-1 {
  background:
    linear-gradient(145deg, rgb(194 65 12 / 0.34), rgb(124 45 18 / 0.2) 52%, rgb(251 146 60 / 0.12)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.space-color-2 {
  background:
    linear-gradient(145deg, rgb(202 138 4 / 0.34), rgb(113 63 18 / 0.2) 52%, rgb(250 204 21 / 0.12)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.space-color-3 {
  background:
    linear-gradient(145deg, rgb(22 163 74 / 0.32), rgb(20 83 45 / 0.2) 52%, rgb(74 222 128 / 0.12)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.space-color-4 {
  background:
    linear-gradient(145deg, rgb(37 99 235 / 0.34), rgb(30 64 175 / 0.2) 52%, rgb(96 165 250 / 0.12)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.space-color-5 {
  background:
    linear-gradient(145deg, rgb(79 70 229 / 0.34), rgb(49 46 129 / 0.2) 52%, rgb(129 140 248 / 0.12)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.space-color-6 {
  background:
    linear-gradient(145deg, rgb(147 51 234 / 0.34), rgb(88 28 135 / 0.2) 52%, rgb(192 132 252 / 0.12)),
    rgb(255 255 255 / 0.04);
}

html.dark .knowledge-space-card.active {
  outline-color: rgb(241 211 136 / 0.42);
}

html.dark .space-stats span {
  border-color: rgb(255 255 255 / 0.09);
  background: rgb(255 255 255 / 0.08);
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

.rule-manager {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(320px, 1.1fr);
  gap: 16px;
}

.rule-panel {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
}

.rule-preview {
  grid-column: 1 / -1;
}

.rule-panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rule-list {
  display: grid;
  gap: 8px;
  max-height: 240px;
  overflow: auto;
}

.rule-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 10px;
}

.rule-list-item > div:first-child {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.rule-list-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
}

.rule-list-item span,
.empty-line {
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 12px;
}

.rule-form-grid,
.preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.rule-switches {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.rule-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: rgb(var(--base-text-color) / 0.72);
  font-size: 13px;
}

.preview-pane,
.preview-output {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 8px;
  min-height: 210px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 10px;
}

.preview-pane-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 24px;
}

.preview-pane-title span {
  color: rgb(var(--base-text-color) / 0.56);
  font-size: 12px;
}

.preview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.quality-issues {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-output pre {
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: rgb(var(--base-text-color) / 0.86);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}

html.dark .rule-list-item,
html.dark .preview-pane,
html.dark .preview-output {
  border-color: rgb(255 255 255 / 0.08);
}

.cleaning-detail {
  display: grid;
  gap: 16px;
}

.detail-file {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

.detail-file strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(112px, 1fr));
  gap: 10px;
}

.detail-grid > div {
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.detail-grid span,
.detail-ratio span,
.detail-rule span {
  display: block;
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 12px;
}

.detail-grid strong {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  line-height: 1;
}

.cleaning-status-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.detail-ratio {
  display: grid;
  gap: 8px;
}

.detail-rule {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
  border-top: 1px solid rgb(15 23 42 / 0.08);
  padding-top: 14px;
}

html.dark .detail-grid > div,
html.dark .detail-rule {
  border-color: rgb(255 255 255 / 0.08);
}

html.dark .detail-actions {
  border-top-color: rgb(255 255 255 / 0.08);
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

  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rule-manager,
  .rule-form-grid,
  .preview-grid {
    grid-template-columns: 1fr;
  }
}

:deep() {
  .n-progress-icon.n-progress-icon--as-text {
    white-space: nowrap;
  }
}
</style>
