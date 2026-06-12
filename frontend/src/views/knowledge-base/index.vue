<script setup lang="tsx">
import type { UploadFileInfo } from 'naive-ui';
import { NButton, NEllipsis, NModal, NPopconfirm, NProgress, NTag, NTooltip, NUpload } from 'naive-ui';
import * as echarts from 'echarts';
import { VueDraggable } from 'vue-draggable-plus';
import { uploadAccept } from '@/constants/common';
import { fakePaginationRequest } from '@/service/request';
import { localStg } from '@/utils/storage';
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
import { fetchGetOrgTagList } from '@/service/api/org-tag';

const appStore = useAppStore();
const authStore = useAuthStore();

// 文件预览相关状态
const previewVisible = ref(false);
const previewFileName = ref('');
const previewFileMd5 = ref('');
const cleaningDetailVisible = ref(false);
const cleaningDetailTask = ref<Api.KnowledgeBase.UploadTask | null>(null);
const recleaning = ref(false);
const recleanRuleSetId = ref<number | null>(null);
const lifecycleVisible = ref(false);
const lifecycleSaving = ref(false);
const lifecycleTask = ref<Api.KnowledgeBase.UploadTask | null>(null);
const lifecycleForm = reactive({
  lifecycleStatus: 'ACTIVE' as NonNullable<Api.KnowledgeBase.UploadTask['lifecycleStatus']>,
  effectiveAt: null as number | null,
  abolishedAt: null as number | null,
  publishedAt: null as number | null,
  versionNo: '',
  supersedesFileMd5: '',
  supersededByFileMd5: ''
});

const governanceVisible = ref(false);
const governanceActiveTab = ref<'faq' | 'term' | 'case' | 'suggestion'>('faq');
const governanceLoading = ref(false);
const governanceSaving = ref(false);
const faqItems = ref<Api.KnowledgeBase.AssistantFaq[]>([]);
const termItems = ref<Api.KnowledgeBase.AssistantTerm[]>([]);
const caseItems = ref<Api.KnowledgeBase.AssistantCase[]>([]);
const faqSuggestionItems = ref<Api.KnowledgeBase.AssistantFaqSuggestion[]>([]);
const editingFaqId = ref<number | null>(null);
const editingTermId = ref<number | null>(null);
const editingCaseId = ref<number | null>(null);
const casePolicyDraft = ref<Api.KnowledgeBase.CasePolicyDraft | null>(null);
const faqForm = reactive({
  question: '',
  answer: '',
  aliases: '',
  knowledgeScope: 'PUBLIC' as 'PUBLIC' | 'DEPARTMENT',
  departmentId: null as string | null
});
const faqEditForm = reactive({
  question: '',
  answer: '',
  aliases: '',
  knowledgeScope: 'PUBLIC' as 'PUBLIC' | 'DEPARTMENT',
  departmentId: null as string | null,
  enabled: true
});
const termForm = reactive({
  term: '',
  definition: '',
  synonyms: '',
  knowledgeScope: 'PUBLIC' as 'PUBLIC' | 'DEPARTMENT',
  departmentId: null as string | null
});
const termEditForm = reactive({
  term: '',
  definition: '',
  synonyms: '',
  knowledgeScope: 'PUBLIC' as 'PUBLIC' | 'DEPARTMENT',
  departmentId: null as string | null,
  enabled: true
});
const caseForm = reactive({
  title: '',
  scenario: '',
  handling: '',
  conclusion: '',
  tags: '',
  knowledgeScope: 'DEPARTMENT' as 'PUBLIC' | 'DEPARTMENT',
  departmentId: null as string | null,
  status: 'DRAFT' as Api.KnowledgeBase.AssistantCase['status']
});
const caseEditForm = reactive({
  title: '',
  scenario: '',
  handling: '',
  conclusion: '',
  tags: '',
  knowledgeScope: 'DEPARTMENT' as 'PUBLIC' | 'DEPARTMENT',
  departmentId: null as string | null,
  status: 'DRAFT' as Api.KnowledgeBase.AssistantCase['status'],
  enabled: true
});
const topologyVisible = ref(false);
const topologyLoading = ref(false);
const documentTopology = ref<Api.KnowledgeBase.DocumentTopology | null>(null);
const topologyGraphRef = ref<HTMLDivElement | null>(null);
let topologyChart: echarts.ECharts | null = null;
const trainingQuizVisible = ref(false);
const trainingQuizLoading = ref(false);
const trainingQuizSpace = ref<KnowledgeSpace | null>(null);
const trainingQuizResult = ref<Api.KnowledgeBase.TrainingQuizResult | null>(null);
const trainingQuizSubmitResult = ref<Api.KnowledgeBase.TrainingExamSubmitResult | null>(null);
const trainingQuizRanking = ref<Api.KnowledgeBase.TrainingExamRankingRow[]>([]);
const trainingQuizSubmitting = ref(false);
const trainingQuizStartedAt = ref<number | null>(null);
const trainingQuizAnswers = reactive<Record<string, string[]>>({});
const trainingQuizForm = reactive({
  questionCount: 8,
  difficulty: '混合',
  questionTypes: ['single_choice'] as string[]
});
const trainingQuizDifficultyOptions = [
  { label: '基础', value: '基础' },
  { label: '进阶', value: '进阶' },
  { label: '混合', value: '混合' }
];
const trainingQuizTypeOptions = [
  { label: '单选题', value: 'single_choice' },
  { label: '多选题', value: 'multiple_choice' }
];
const trainingDeckVisible = ref(false);
const trainingDeckLoading = ref(false);
const trainingDeckExporting = ref(false);
const trainingDeckSpace = ref<KnowledgeSpace | null>(null);
const trainingDeckResult = ref<Api.KnowledgeBase.TrainingDeckResult | null>(null);
const trainingDeckForm = reactive({
  slideCount: 8,
  audience: '部门员工',
  tone: '正式清晰'
});
const trainingDeckToneOptions = [
  { label: '正式清晰', value: '正式清晰' },
  { label: '培训讲解', value: '培训讲解' },
  { label: '考核导向', value: '考核导向' }
];
const auditVisible = ref(false);
const auditLoading = ref(false);
const auditSaving = ref(false);
const auditTask = ref<Api.KnowledgeBase.UploadTask | null>(null);
const auditDetail = ref<Api.KnowledgeBase.PolicyAuditDetail | null>(null);
const auditReviewForm = reactive({
  status: 'PASS' as NonNullable<Api.KnowledgeBase.UploadTask['policyAuditStatus']>,
  score: 100,
  summary: '',
  issues: ''
});

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
function handleFilePreview(fileName: string, fileMd5 = '') {
  previewFileName.value = fileName;
  previewFileMd5.value = fileMd5;
  previewVisible.value = true;
}

// 关闭文件预览
function closeFilePreview() {
  previewVisible.value = false;
  previewFileName.value = '';
  previewFileMd5.value = '';
}

async function openCleaningDetail(row: Api.KnowledgeBase.UploadTask) {
  cleaningDetailTask.value = row;
  recleanRuleSetId.value = row.cleaningRuleSetId ?? null;
  cleaningDetailVisible.value = true;
  if (cleaningRuleSets.value.length === 0) {
    await store.refreshCleaningRuleSets();
  }
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
      key: 'lifecycleStatus',
      title: '制度状态',
      width: 120,
      render: row => renderLifecycleStatus(row)
    },
    {
      key: 'policyAuditStatus',
      title: '审计状态',
      width: 140,
      render: row => renderPolicyAuditStatus(row)
    },
    {
      key: 'effectiveAt',
      title: '生效边界',
      width: 180,
      render: row => renderEffectiveRange(row)
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
      width: 300,
      render: row => (
        <div class="flex flex-wrap gap-4">
          {renderResumeUploadButton(row)}
          {renderLifecycleButton(row)}
          {renderPolicyAuditButton(row)}
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
  categoryId: null as number | null,
  retrievability: null as 'RETRIEVABLE' | 'PENDING' | 'EXPIRED' | 'AUDIT_REJECTED' | 'INDEX_ISSUE' | null
});

const knowledgeScopeOptions = [
  { label: '公共知识', value: 'PUBLIC' },
  { label: '部门知识', value: 'DEPARTMENT' },
  { label: '私人知识', value: 'PRIVATE' }
];

const lifecycleStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审计', value: 'PENDING_AUDIT' },
  { label: '审计驳回', value: 'AUDIT_REJECTED' },
  { label: '已批准', value: 'APPROVED' },
  { label: '生效中', value: 'ACTIVE' },
  { label: '已废止', value: 'EXPIRED' },
  { label: '已撤销', value: 'REVOKED' },
  { label: '已被替代', value: 'SUPERSEDED' }
];

const retrievabilityOptions = [
  { label: '可检索', value: 'RETRIEVABLE' },
  { label: '待生效/待审计', value: 'PENDING' },
  { label: '已废止', value: 'EXPIRED' },
  { label: '审计未通过', value: 'AUDIT_REJECTED' },
  { label: '索引异常', value: 'INDEX_ISSUE' }
];

const auditStatusOptions = [
  { label: '免审', value: 'NOT_REQUIRED' },
  { label: '待审计', value: 'PENDING' },
  { label: '通过', value: 'PASS' },
  { label: '通过但有提醒', value: 'PASS_WITH_WARNINGS' },
  { label: '未通过', value: 'REJECT' },
  { label: '需人工复核', value: 'NEED_MANUAL_REVIEW' }
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
    if (filterModel.retrievability && retrievalState(item).key !== filterModel.retrievability) return false;

    return true;
  });
});

const hasActiveFilters = computed(
  () =>
    Boolean(filterModel.keyword.trim()) ||
    Boolean(filterModel.knowledgeScope) ||
    Boolean(filterModel.departmentId) ||
    Boolean(filterModel.categoryId) ||
    Boolean(filterModel.retrievability)
);
const knowledgeSpaces = computed(() => {
  if (!hasActiveFilters.value && serverKnowledgeSpaces.value?.length) return serverKnowledgeSpaces.value;
  return buildKnowledgeSpaces(baseFilteredTasks.value);
});
const currentSpace = computed(() => knowledgeSpaces.value.find(item => item.id === selectedSpaceId.value) || knowledgeSpaces.value[0] || null);
const filteredTasks = computed(() => filterTasksBySpace(baseFilteredTasks.value, selectedSpaceId.value));
const filteredRetrievableCount = computed(() => filteredTasks.value.filter(item => retrievalState(item).key === 'RETRIEVABLE').length);

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

async function openTrainingQuiz(space: KnowledgeSpace) {
  if (space.type === 'PRIVATE') {
    window.$message?.warning('个人知识库题库生成后续再开放');
    return;
  }
  selectSpace(space.id);
  trainingQuizSpace.value = space;
  trainingQuizResult.value = null;
  trainingQuizSubmitResult.value = null;
  trainingQuizRanking.value = [];
  clearQuizAnswers();
  trainingQuizStartedAt.value = null;
  trainingQuizVisible.value = true;
}

async function generateTrainingQuiz() {
  const space = trainingQuizSpace.value;
  if (!space) return;
  if (!trainingQuizForm.questionTypes.length) {
    window.$message?.warning('请至少选择一种题型');
    return;
  }
  trainingQuizLoading.value = true;
  try {
    const { error, data } = await request<Api.KnowledgeBase.TrainingQuizResult>({
      url: '/knowledge-training/quiz',
      method: 'POST',
      data: {
        knowledgeScope: space.type === 'DEPARTMENT' ? 'DEPARTMENT' : 'PUBLIC',
        departmentId: space.type === 'DEPARTMENT' ? space.departmentId : null,
        questionCount: trainingQuizForm.questionCount,
        difficulty: trainingQuizForm.difficulty,
        questionTypes: trainingQuizForm.questionTypes
      }
    });
    if (!error) {
      trainingQuizResult.value = data;
      trainingQuizSubmitResult.value = null;
      trainingQuizRanking.value = [];
      clearQuizAnswers();
      trainingQuizStartedAt.value = Date.now();
      window.$message?.success('题库已生成');
    }
  } finally {
    trainingQuizLoading.value = false;
  }
}

function clearQuizAnswers() {
  Object.keys(trainingQuizAnswers).forEach(key => {
    delete trainingQuizAnswers[key];
  });
}

function answerValue(answer: Api.KnowledgeBase.TrainingQuizQuestion['answer']) {
  if (Array.isArray(answer)) return answer.join('、');
  return answer || '-';
}

function optionKey(option: string) {
  const match = option.trim().match(/^([A-Za-z])[\.\s、]/);
  return match ? match[1].toUpperCase() : option.trim();
}

function setSingleChoiceAnswer(index: number, value: string | null) {
  trainingQuizAnswers[String(index)] = value ? [value] : [];
}

function setMultipleChoiceAnswer(index: number, values: string[] | null) {
  trainingQuizAnswers[String(index)] = values || [];
}

function selectedSingleChoice(index: number) {
  return trainingQuizAnswers[String(index)]?.[0] || null;
}

function selectedMultipleChoice(index: number) {
  return trainingQuizAnswers[String(index)] || [];
}

async function submitTrainingQuiz() {
  const result = trainingQuizResult.value;
  if (!result) return;
  const unanswered = result.questions.findIndex((_question, index) => !(trainingQuizAnswers[String(index)] || []).length);
  if (unanswered >= 0) {
    window.$message?.warning(`第 ${unanswered + 1} 题还没有作答`);
    return;
  }
  trainingQuizSubmitting.value = true;
  try {
    const durationSeconds = trainingQuizStartedAt.value
      ? Math.max(1, Math.round((Date.now() - trainingQuizStartedAt.value) / 1000))
      : null;
    const { error, data } = await request<Api.KnowledgeBase.TrainingExamSubmitResult>({
      url: '/knowledge-training/quiz/submit',
      method: 'POST',
      data: {
        title: result.title,
        knowledgeScope: result.knowledgeScope,
        departmentId: result.departmentId || null,
        questions: result.questions,
        answers: trainingQuizAnswers,
        sources: result.sources,
        durationSeconds
      }
    });
    if (!error) {
      trainingQuizSubmitResult.value = data;
      window.$message?.success(`已自动审阅：${data.score} 分`);
      await loadTrainingQuizRanking();
    }
  } finally {
    trainingQuizSubmitting.value = false;
  }
}

async function loadTrainingQuizRanking() {
  const result = trainingQuizResult.value;
  if (!result) return;
  const { error, data } = await request<Api.KnowledgeBase.TrainingExamRankingRow[]>({
    url: '/knowledge-training/quiz/ranking',
    params: {
      knowledgeScope: result.knowledgeScope,
      departmentId: result.departmentId || undefined,
      limit: 20
    }
  });
  if (!error) {
    trainingQuizRanking.value = data || [];
  }
}

function quizReview(index: number) {
  return trainingQuizSubmitResult.value?.reviews.find(item => item.index === index);
}

async function openTrainingDeck(space: KnowledgeSpace) {
  if (space.type === 'PRIVATE') {
    window.$message?.warning('个人知识库课件生成后续再开放');
    return;
  }
  selectSpace(space.id);
  trainingDeckSpace.value = space;
  trainingDeckResult.value = null;
  trainingDeckVisible.value = true;
}

async function generateTrainingDeck() {
  await generateTrainingDeckResult(true);
}

async function generateTrainingDeckResult(showSuccess = false) {
  const space = trainingDeckSpace.value;
  if (!space) return null;
  trainingDeckLoading.value = true;
  try {
    const { error, data } = await request<Api.KnowledgeBase.TrainingDeckResult>({
      url: '/knowledge-training/deck',
      method: 'POST',
      data: {
        knowledgeScope: space.type === 'DEPARTMENT' ? 'DEPARTMENT' : 'PUBLIC',
        departmentId: space.type === 'DEPARTMENT' ? space.departmentId : null,
        slideCount: trainingDeckForm.slideCount,
        audience: trainingDeckForm.audience,
        tone: trainingDeckForm.tone
      }
    });
    if (!error) {
      trainingDeckResult.value = data;
      if (showSuccess) window.$message?.success('培训课件已生成');
      return data;
    }
    return null;
  } finally {
    trainingDeckLoading.value = false;
  }
}

async function exportTrainingDeck(deck = trainingDeckResult.value) {
  if (!deck) {
    window.$message?.warning('请先生成课件预览');
    return;
  }
  const token = localStg.get('token');
  if (!token) {
    window.$message?.error('登录已失效，请重新登录');
    return;
  }
  trainingDeckExporting.value = true;
  try {
    const response = await fetch('/proxy-default/api/v1/knowledge-training/deck/export', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(deck)
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `导出失败：${response.status}`);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${deck.title || '培训课件'}.pptx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    window.$message?.success('PPTX 已开始下载');
  } catch (error) {
    window.$message?.error(error instanceof Error ? error.message : '导出 PPTX 失败');
  } finally {
    trainingDeckExporting.value = false;
  }
}

async function generateAndExportTrainingDeck() {
  const deck = trainingDeckResult.value || (await generateTrainingDeckResult(false));
  if (!deck) return;
  await exportTrainingDeck(deck);
}

watch([knowledgeSpaces, knowledgeSpaceLayoutKey], syncSpaceBoardItems, { immediate: true });

let indexPollTimer: ReturnType<typeof setInterval> | null = null;

const filterOrgTag = ref<string | null>(null);

async function onKbFilterChange(tagId: string | null) {
  filterOrgTag.value = tagId;
  updateSearchParams({ orgTag: tagId });
  await getList();
}

onMounted(async () => {
  await loadKbFilterOptions();
  await Promise.all([getList(), refreshKnowledgeSpaces(), store.refreshUploadPreflight(), store.refreshCategories()]);
  startIndexPolling();
});

onUnmounted(() => {
  if (indexPollTimer) clearInterval(indexPollTimer);
  topologyChart?.dispose();
  topologyChart = null;
});

function startIndexPolling() {
  if (indexPollTimer) clearInterval(indexPollTimer);
  indexPollTimer = setInterval(async () => {
    if (document.hidden || loading.value || indexPendingCount.value === 0) return;
    await getList();
  }, 8000);
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

async function openTopologyViewer() {
  topologyVisible.value = true;
  topologyLoading.value = true;
  try {
    const { error, data } = await request<Api.KnowledgeBase.DocumentTopology>({
      url: '/documents/topology',
      params: filterOrgTag.value ? { orgTag: filterOrgTag.value } : undefined
    });
    if (!error) {
      documentTopology.value = data;
      await nextTick();
      renderTopologyGraph();
    }
  } finally {
    topologyLoading.value = false;
  }
}

function renderTopologyGraph() {
  if (!topologyGraphRef.value || !documentTopology.value) return;
  if (!topologyChart) {
    topologyChart = echarts.init(topologyGraphRef.value);
    topologyChart.on('click', params => {
      const data = params.data as Partial<Api.KnowledgeBase.DocumentTopologyNode> | undefined;
      if (params.dataType === 'node' && data?.fileName) {
        handleFilePreview(data.fileName, data.fileMd5 || '');
      }
    });
  }
  const palette = ['#2563eb', '#16a34a', '#f97316', '#9333ea', '#dc2626', '#0891b2', '#ca8a04', '#db2777'];
  const groups = Array.from(new Set(documentTopology.value.nodes.map(node => node.group || 'UNKNOWN')));
  const colorForGroup = (group?: string) => palette[Math.max(0, groups.indexOf(group || 'UNKNOWN')) % palette.length];
  topologyChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter(params: any) {
        if (params.dataType === 'edge') return `${params.data.label || params.data.type}<br/>${params.data.description || ''}`;
        const node = params.data as Api.KnowledgeBase.DocumentTopologyNode;
        return `${node.fileName}<br/>${node.departmentId || (node.knowledgeScope === 'PUBLIC' ? '公共知识库' : '未归属部门')}<br/>${node.retrievable ? '可检索' : '需关注'}`;
      }
    },
    legend: {
      top: 0,
      data: groups,
      textStyle: { color: '#64748b' }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        categories: groups.map(group => ({ name: group })),
        force: {
          repulsion: 180,
          edgeLength: [70, 180],
          gravity: 0.08
        },
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          color: '#0f172a',
          fontSize: 12
        },
        lineStyle: {
          color: '#94a3b8',
          opacity: 0.45,
          width: 1.2,
          curveness: 0.08
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 }
        },
        data: documentTopology.value.nodes.map(node => ({
          ...node,
          name: node.label || node.fileName,
          category: node.group || 'UNKNOWN',
          symbolSize: node.symbolSize || 36,
          itemStyle: {
            color: colorForGroup(node.group),
            borderColor: node.riskLevel === 'HIGH' ? '#ef4444' : node.riskLevel === 'MEDIUM' ? '#f59e0b' : '#ffffff',
            borderWidth: node.riskLevel === 'NONE' ? 1 : 3
          }
        })),
        links: documentTopology.value.edges.map(edge => ({
          ...edge,
          value: edge.label,
          lineStyle: {
            width: Math.max(1, edge.weight || 1),
            curveness: edge.curveness ?? 0.08,
            color: edge.type === 'SUPERSEDES' ? '#f97316' : '#94a3b8'
          }
        }))
      }
    ]
  });
}

watch(topologyVisible, visible => {
  if (visible) {
    nextTick(renderTopologyGraph);
  }
});

function topologyNodeName(fileMd5?: string | null) {
  if (!fileMd5) return '-';
  return documentTopology.value?.nodes.find(item => item.fileMd5 === fileMd5)?.fileName || fileMd5;
}

function topologyNodeMeta(node: Api.KnowledgeBase.DocumentTopologyNode) {
  const lifecycle = lifecycleMeta(node.lifecycleStatus);
  const audit = auditMeta(node.policyAuditStatus);
  return {
    lifecycle,
    audit,
    scope: node.knowledgeScope === 'PUBLIC' ? '公共' : node.departmentId || '部门',
    boundary: `${formatBoundaryDate(node.effectiveAt, '立即')} - ${formatBoundaryDate(node.abolishedAt, '长期')}`
  };
}

function topologyNodeRiskClass(node: Api.KnowledgeBase.DocumentTopologyNode) {
  if (node.policyAuditStatus === 'REJECT' || node.lifecycleStatus === 'AUDIT_REJECTED') return 'audit-reject';
  if (node.policyAuditStatus === 'NEED_MANUAL_REVIEW' || node.policyAuditStatus === 'PASS_WITH_WARNINGS') return 'audit-warning';
  if (!node.retrievable) return 'not-retrievable';
  return '';
}

function topologyEdgeMeta(edge: Api.KnowledgeBase.DocumentTopologyEdge) {
  const map: Record<Api.KnowledgeBase.DocumentTopologyEdge['type'], { label: string; type: 'default' | 'success' | 'info' | 'warning' | 'error' }> = {
    SUPERSEDES: { label: '替代', type: 'warning' },
    SAME_DEPARTMENT: { label: '同部门', type: 'info' },
    SAME_CATEGORY: { label: '同分类', type: 'success' },
    SAME_LIFECYCLE: { label: '同状态', type: 'default' }
  };
  return map[edge.type] || { label: edge.label || edge.type, type: 'default' as const };
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
  filterModel.retrievability = null;
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

function lifecycleMeta(status?: Api.KnowledgeBase.UploadTask['lifecycleStatus']) {
  const map = {
    DRAFT: { label: '草稿', type: 'default' as const },
    PENDING_AUDIT: { label: '待审计', type: 'warning' as const },
    AUDIT_REJECTED: { label: '审计驳回', type: 'error' as const },
    APPROVED: { label: '已批准', type: 'success' as const },
    ACTIVE: { label: '生效中', type: 'success' as const },
    EXPIRED: { label: '已废止', type: 'default' as const },
    REVOKED: { label: '已撤销', type: 'error' as const },
    SUPERSEDED: { label: '已被替代', type: 'warning' as const }
  };
  return map[status || 'ACTIVE'] || map.ACTIVE;
}

function auditMeta(status?: Api.KnowledgeBase.UploadTask['policyAuditStatus']) {
  const map = {
    NOT_REQUIRED: { label: '免审', type: 'default' as const },
    PENDING: { label: '待审计', type: 'warning' as const },
    PASS: { label: '通过', type: 'success' as const },
    PASS_WITH_WARNINGS: { label: '有提醒', type: 'warning' as const },
    REJECT: { label: '未通过', type: 'error' as const },
    NEED_MANUAL_REVIEW: { label: '需人工复核', type: 'warning' as const }
  };
  return map[status || 'PASS'] || map.PASS;
}

function isBeforeEffectiveAt(row: Api.KnowledgeBase.UploadTask) {
  return Boolean(row.effectiveAt && dayjs(row.effectiveAt).isAfter(dayjs()));
}

function isAfterAbolishedAt(row: Api.KnowledgeBase.UploadTask) {
  return Boolean(row.abolishedAt && dayjs(row.abolishedAt).isBefore(dayjs()));
}

function retrievalState(row: Api.KnowledgeBase.UploadTask): {
  key: 'RETRIEVABLE' | 'PENDING' | 'EXPIRED' | 'AUDIT_REJECTED' | 'INDEX_ISSUE';
  label: string;
  type: 'success' | 'warning' | 'error' | 'default';
  reason: string;
} {
  if (row.status !== UploadStatus.Completed) {
    return { key: 'INDEX_ISSUE', label: '不可检索', type: 'error', reason: '文件尚未上传完成' };
  }

  const indexStatus = row.indexStatus ?? IndexStatus.Indexed;
  if (indexStatus === IndexStatus.Failed) {
    return { key: 'INDEX_ISSUE', label: '索引异常', type: 'error', reason: row.indexError || '索引失败，需要重新索引' };
  }
  if (indexStatus === IndexStatus.Pending || indexStatus === IndexStatus.Indexing) {
    return { key: 'INDEX_ISSUE', label: '索引处理中', type: 'warning', reason: '文件索引尚未完成' };
  }

  const lifecycleStatus = row.lifecycleStatus || 'ACTIVE';
  if (['EXPIRED', 'REVOKED', 'SUPERSEDED'].includes(lifecycleStatus) || isAfterAbolishedAt(row)) {
    return { key: 'EXPIRED', label: '不可检索', type: 'default', reason: '文件已废止、撤销或被替代' };
  }
  if (lifecycleStatus === 'AUDIT_REJECTED') {
    return { key: 'AUDIT_REJECTED', label: '不可检索', type: 'error', reason: '制度审计未通过' };
  }
  if (row.policyAuditStatus === 'REJECT') {
    return { key: 'AUDIT_REJECTED', label: '不可检索', type: 'error', reason: '审计结果为未通过' };
  }
  if (['DRAFT', 'PENDING_AUDIT', 'APPROVED'].includes(lifecycleStatus) || isBeforeEffectiveAt(row)) {
    return { key: 'PENDING', label: '待生效', type: 'warning', reason: '文件尚未进入生效中状态' };
  }

  return { key: 'RETRIEVABLE', label: '可检索', type: 'success', reason: '已完成上传、审计通过且处于生效期内' };
}

function renderLifecycleStatus(row: Api.KnowledgeBase.UploadTask) {
  const meta = lifecycleMeta(row.lifecycleStatus);
  const retrieval = retrievalState(row);
  return (
    <div class="lifecycle-status-stack">
      <NTag type={meta.type}>{meta.label}</NTag>
      {retrieval.key !== 'RETRIEVABLE' ? (
        <NTooltip trigger="hover">
          {{
            trigger: () => (
              <NTag size="small" type={retrieval.type} bordered={false}>
                {retrieval.label}
              </NTag>
            ),
            default: () => retrieval.reason
          }}
        </NTooltip>
      ) : null}
    </div>
  );
}

function renderPolicyAuditStatus(row: Api.KnowledgeBase.UploadTask) {
  const meta = auditMeta(row.policyAuditStatus);
  const score = typeof row.policyAuditScore === 'number' ? ` · ${Math.round(row.policyAuditScore)}分` : '';
  const label = `${meta.label}${score}`;
  if (!row.policyAuditSummary && !row.policyAuditIssues) {
    return <NTag type={meta.type}>{label}</NTag>;
  }
  return (
    <NTooltip trigger="hover">
      {{
        trigger: () => <NTag type={meta.type}>{label}</NTag>,
        default: () => row.policyAuditSummary || row.policyAuditIssues || '审计完成'
      }}
    </NTooltip>
  );
}

function formatBoundaryDate(value?: string | null, fallback = '-') {
  if (!value) return fallback;
  return dayjs(value).format('YYYY-MM-DD');
}

function renderEffectiveRange(row: Api.KnowledgeBase.UploadTask) {
  return (
    <div class="lifecycle-range">
      <span>生效 {formatBoundaryDate(row.effectiveAt, '立即')}</span>
      <span>废止 {formatBoundaryDate(row.abolishedAt, '长期')}</span>
    </div>
  );
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
  const retrieval = retrievalState(row);
  if (retrieval.key !== 'RETRIEVABLE' && retrieval.key !== 'INDEX_ISSUE') {
    return (
      <NTooltip trigger="hover">
        {{
          trigger: () => <NTag type={retrieval.type}>{retrieval.label}</NTag>,
          default: () => retrieval.reason
        }}
      </NTooltip>
    );
  }
  const status = row.indexStatus ?? IndexStatus.Indexed;
  if (status === IndexStatus.Pending) {
    return (
      <NTooltip trigger="hover">
        {{
          trigger: () => <NTag type="warning">待索引</NTag>,
          default: () => retrieval.reason
        }}
      </NTooltip>
    );
  }
  if (status === IndexStatus.Indexing) {
    return (
      <NTooltip trigger="hover">
        {{
          trigger: () => <NTag type="info">索引中</NTag>,
          default: () => retrieval.reason
        }}
      </NTooltip>
    );
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
  return (
    <NTooltip trigger="hover">
      {{
        trigger: () => <NTag type="success">可检索</NTag>,
        default: () => retrieval.reason
      }}
    </NTooltip>
  );
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

function renderPolicyAuditButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status !== UploadStatus.Completed) return null;
  if (!canManageDocument(row)) return null;
  return (
    <NButton type="info" ghost size="small" onClick={() => openAuditDetail(row)}>
      审计详情
    </NButton>
  );
}

function renderLifecycleButton(row: Api.KnowledgeBase.UploadTask) {
  if (!canManageDocument(row)) return null;
  return (
    <NButton type="primary" ghost size="small" onClick={() => openLifecycleEditor(row)}>
      生命周期
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

function applyAuditDetailToForm(detail: Api.KnowledgeBase.PolicyAuditDetail | null) {
  auditReviewForm.status = detail?.policyAuditStatus || 'PASS';
  auditReviewForm.score = Math.round(detail?.policyAuditScore ?? 100);
  auditReviewForm.summary = detail?.policyAuditSummary || '';
  auditReviewForm.issues = detail?.policyAuditIssues || '';
}

async function openAuditDetail(row: Api.KnowledgeBase.UploadTask) {
  auditTask.value = row;
  auditVisible.value = true;
  auditLoading.value = true;
  try {
    const { error, data } = await request<Api.KnowledgeBase.PolicyAuditDetail>({
      url: `/documents/${row.fileMd5}/audit`
    });
    if (!error) {
      auditDetail.value = data;
      applyAuditDetailToForm(data);
    }
  } finally {
    auditLoading.value = false;
  }
}

async function handleRunPolicyAudit() {
  if (!auditTask.value) return;
  auditSaving.value = true;
  try {
    const { error, data } = await request<Api.KnowledgeBase.PolicyAuditDetail>({
      url: `/documents/${auditTask.value.fileMd5}/audit/run`,
      method: 'POST'
    });
    if (!error) {
      auditDetail.value = data;
      applyAuditDetailToForm(data);
      window.$message?.success(data?.policyAuditSummary || '制度审计已完成');
      await getList();
    }
  } finally {
    auditSaving.value = false;
  }
}

async function handleReviewPolicyAudit() {
  if (!auditTask.value) return;
  auditSaving.value = true;
  try {
    const { error, data } = await request<Api.KnowledgeBase.PolicyAuditDetail>({
      url: `/documents/${auditTask.value.fileMd5}/audit/review`,
      method: 'POST',
      data: {
        status: auditReviewForm.status,
        score: auditReviewForm.score,
        summary: auditReviewForm.summary || null,
        issues: auditReviewForm.issues || null
      }
    });
    if (!error) {
      auditDetail.value = data;
      applyAuditDetailToForm(data);
      window.$message?.success('审计复核已保存');
      await getList();
    }
  } finally {
    auditSaving.value = false;
  }
}

async function handleRunPolicyAuditLegacy(row: Api.KnowledgeBase.UploadTask) {
  const { error, data } = await request<Api.KnowledgeBase.PolicyAuditDetail>({
    url: `/documents/${row.fileMd5}/audit/run`,
    method: 'POST'
  });
  if (!error) {
    window.$message?.success(data?.policyAuditSummary || '制度审计已完成');
    await getList();
  }
}

function toDatePickerValue(value?: string | null) {
  if (!value) return null;
  const time = dayjs(value).valueOf();
  return Number.isNaN(time) ? null : time;
}

function toLocalDateTime(value: number | null) {
  return value ? dayjs(value).format('YYYY-MM-DDTHH:mm:ss') : null;
}

function openLifecycleEditor(row: Api.KnowledgeBase.UploadTask) {
  lifecycleTask.value = row;
  lifecycleForm.lifecycleStatus = row.lifecycleStatus || 'ACTIVE';
  lifecycleForm.effectiveAt = toDatePickerValue(row.effectiveAt);
  lifecycleForm.abolishedAt = toDatePickerValue(row.abolishedAt);
  lifecycleForm.publishedAt = toDatePickerValue(row.publishedAt);
  lifecycleForm.versionNo = row.versionNo || '';
  lifecycleForm.supersedesFileMd5 = row.supersedesFileMd5 || '';
  lifecycleForm.supersededByFileMd5 = row.supersededByFileMd5 || '';
  lifecycleVisible.value = true;
}

async function handleSaveLifecycle() {
  if (!lifecycleTask.value) return;
  lifecycleSaving.value = true;
  try {
    const { error } = await request({
      url: `/documents/${lifecycleTask.value.fileMd5}/lifecycle`,
      method: 'PUT',
      data: {
        lifecycleStatus: lifecycleForm.lifecycleStatus,
        effectiveAt: toLocalDateTime(lifecycleForm.effectiveAt),
        abolishedAt: toLocalDateTime(lifecycleForm.abolishedAt),
        publishedAt: toLocalDateTime(lifecycleForm.publishedAt),
        versionNo: lifecycleForm.versionNo || null,
        supersedesFileMd5: lifecycleForm.supersedesFileMd5 || null,
        supersededByFileMd5: lifecycleForm.supersededByFileMd5 || null
      }
    });
    if (!error) {
      window.$message?.success('文档生命周期已保存');
      lifecycleVisible.value = false;
      await refreshKnowledgeBaseView();
    }
  } finally {
    lifecycleSaving.value = false;
  }
}

async function refreshGovernanceData() {
  governanceLoading.value = true;
  try {
    const [faqResp, termResp, caseResp, suggestionResp] = await Promise.all([
      request<Api.KnowledgeBase.AssistantFaq[]>({ url: '/knowledge-assistant/faqs' }),
      request<Api.KnowledgeBase.AssistantTerm[]>({ url: '/knowledge-assistant/terms' }),
      request<Api.KnowledgeBase.AssistantCase[]>({ url: '/knowledge-assistant/cases' }),
      request<Api.KnowledgeBase.AssistantFaqSuggestion[]>({ url: '/knowledge-assistant/faq-suggestions' })
    ]);
    if (!faqResp.error) faqItems.value = faqResp.data || [];
    if (!termResp.error) termItems.value = termResp.data || [];
    if (!caseResp.error) caseItems.value = caseResp.data || [];
    if (!suggestionResp.error) faqSuggestionItems.value = suggestionResp.data || [];
  } finally {
    governanceLoading.value = false;
  }
}

async function openGovernanceManager() {
  resetFaqForm();
  resetTermForm();
  resetCaseForm();
  governanceVisible.value = true;
  await refreshGovernanceData();
}

function governanceDepartmentId(scope: 'PUBLIC' | 'DEPARTMENT', departmentId: string | null) {
  if (scope === 'PUBLIC') return null;
  return departmentId || currentSpace.value?.departmentId || authStore.userInfo.primaryOrg || null;
}

function resetFaqForm() {
  faqForm.question = '';
  faqForm.answer = '';
  faqForm.aliases = '';
  faqForm.knowledgeScope = currentSpace.value?.type === 'DEPARTMENT' ? 'DEPARTMENT' : 'PUBLIC';
  faqForm.departmentId = currentSpace.value?.departmentId || null;
}

function beginEditFaq(item: Api.KnowledgeBase.AssistantFaq) {
  editingFaqId.value = item.id;
  faqEditForm.question = item.question;
  faqEditForm.answer = item.answer;
  faqEditForm.aliases = item.aliases || '';
  faqEditForm.knowledgeScope = item.knowledgeScope === 'DEPARTMENT' ? 'DEPARTMENT' : 'PUBLIC';
  faqEditForm.departmentId = item.departmentId || null;
  faqEditForm.enabled = item.enabled;
}

function cancelEditFaq() {
  editingFaqId.value = null;
}

function resetTermForm() {
  termForm.term = '';
  termForm.definition = '';
  termForm.synonyms = '';
  termForm.knowledgeScope = currentSpace.value?.type === 'DEPARTMENT' ? 'DEPARTMENT' : 'PUBLIC';
  termForm.departmentId = currentSpace.value?.departmentId || null;
}

function beginEditTerm(item: Api.KnowledgeBase.AssistantTerm) {
  editingTermId.value = item.id;
  termEditForm.term = item.term;
  termEditForm.definition = item.definition || '';
  termEditForm.synonyms = item.synonyms || '';
  termEditForm.knowledgeScope = item.knowledgeScope === 'DEPARTMENT' ? 'DEPARTMENT' : 'PUBLIC';
  termEditForm.departmentId = item.departmentId || null;
  termEditForm.enabled = item.enabled;
}

function cancelEditTerm() {
  editingTermId.value = null;
}

function resetCaseForm() {
  caseForm.title = '';
  caseForm.scenario = '';
  caseForm.handling = '';
  caseForm.conclusion = '';
  caseForm.tags = '';
  caseForm.knowledgeScope = currentSpace.value?.type === 'PUBLIC' ? 'PUBLIC' : 'DEPARTMENT';
  caseForm.departmentId = currentSpace.value?.departmentId || null;
  caseForm.status = 'DRAFT';
}

function beginEditCase(item: Api.KnowledgeBase.AssistantCase) {
  editingCaseId.value = item.id;
  caseEditForm.title = item.title;
  caseEditForm.scenario = item.scenario || '';
  caseEditForm.handling = item.handling || '';
  caseEditForm.conclusion = item.conclusion || '';
  caseEditForm.tags = item.tags || '';
  caseEditForm.knowledgeScope = item.knowledgeScope === 'PUBLIC' ? 'PUBLIC' : 'DEPARTMENT';
  caseEditForm.departmentId = item.departmentId || null;
  caseEditForm.status = item.status;
  caseEditForm.enabled = item.enabled;
}

function cancelEditCase() {
  editingCaseId.value = null;
}

function faqPayload(form: typeof faqForm | typeof faqEditForm) {
  return {
    question: form.question,
    answer: form.answer,
    aliases: form.aliases || null,
    knowledgeScope: form.knowledgeScope,
    departmentId: governanceDepartmentId(form.knowledgeScope, form.departmentId),
    enabled: 'enabled' in form ? form.enabled : true
  };
}

function termPayload(form: typeof termForm | typeof termEditForm) {
  return {
    term: form.term,
    definition: form.definition || null,
    synonyms: form.synonyms || null,
    knowledgeScope: form.knowledgeScope,
    departmentId: governanceDepartmentId(form.knowledgeScope, form.departmentId),
    enabled: 'enabled' in form ? form.enabled : true
  };
}

function casePayload(form: typeof caseForm | typeof caseEditForm) {
  return {
    title: form.title,
    scenario: form.scenario,
    handling: form.handling,
    conclusion: form.conclusion,
    tags: form.tags || null,
    knowledgeScope: form.knowledgeScope,
    departmentId: governanceDepartmentId(form.knowledgeScope, form.departmentId),
    status: form.status,
    enabled: 'enabled' in form ? form.enabled : true
  };
}

async function handleCreateFaq() {
  if (!faqForm.question.trim() || !faqForm.answer.trim()) {
    window.$message?.warning('标准问题和答案不能为空');
    return;
  }
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: '/knowledge-assistant/faqs',
      method: 'POST',
      data: faqPayload(faqForm)
    });
    if (!error) {
      window.$message?.success('问答对已保存');
      resetFaqForm();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleUpdateFaq() {
  if (!editingFaqId.value) return;
  if (!faqEditForm.question.trim() || !faqEditForm.answer.trim()) {
    window.$message?.warning('标准问题和答案不能为空');
    return;
  }
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/faqs/${editingFaqId.value}`,
      method: 'PUT',
      data: faqPayload(faqEditForm)
    });
    if (!error) {
      window.$message?.success('问答对已更新');
      cancelEditFaq();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleToggleFaq(item: Api.KnowledgeBase.AssistantFaq) {
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/faqs/${item.id}`,
      method: 'PUT',
      data: {
        question: item.question,
        answer: item.answer,
        aliases: item.aliases || null,
        knowledgeScope: item.knowledgeScope,
        departmentId: item.departmentId || null,
        enabled: !item.enabled
      }
    });
    if (!error) {
      window.$message?.success(item.enabled ? '问答对已停用' : '问答对已启用');
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleDeleteFaq(item: Api.KnowledgeBase.AssistantFaq) {
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/faqs/${item.id}`,
      method: 'DELETE'
    });
    if (!error) {
      window.$message?.success('问答对已删除');
      if (editingFaqId.value === item.id) cancelEditFaq();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleCreateTerm() {
  if (!termForm.term.trim()) {
    window.$message?.warning('术语名称不能为空');
    return;
  }
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: '/knowledge-assistant/terms',
      method: 'POST',
      data: termPayload(termForm)
    });
    if (!error) {
      window.$message?.success('术语已保存');
      resetTermForm();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleUpdateTerm() {
  if (!editingTermId.value) return;
  if (!termEditForm.term.trim()) {
    window.$message?.warning('术语名称不能为空');
    return;
  }
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/terms/${editingTermId.value}`,
      method: 'PUT',
      data: termPayload(termEditForm)
    });
    if (!error) {
      window.$message?.success('术语已更新');
      cancelEditTerm();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleToggleTerm(item: Api.KnowledgeBase.AssistantTerm) {
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/terms/${item.id}`,
      method: 'PUT',
      data: {
        term: item.term,
        definition: item.definition || null,
        synonyms: item.synonyms || null,
        knowledgeScope: item.knowledgeScope,
        departmentId: item.departmentId || null,
        enabled: !item.enabled
      }
    });
    if (!error) {
      window.$message?.success(item.enabled ? '术语已停用' : '术语已启用');
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleDeleteTerm(item: Api.KnowledgeBase.AssistantTerm) {
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/terms/${item.id}`,
      method: 'DELETE'
    });
    if (!error) {
      window.$message?.success('术语已删除');
      if (editingTermId.value === item.id) cancelEditTerm();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleUpdateSuggestionStatus(item: Api.KnowledgeBase.AssistantFaqSuggestion, status: Api.KnowledgeBase.AssistantFaqSuggestion['status']) {
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/faq-suggestions/${item.id}/status`,
      method: 'PUT',
      data: { status }
    });
    if (!error) {
      window.$message?.success('学习建议状态已更新');
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleCreateCase() {
  if (!caseForm.title.trim() || !caseForm.scenario.trim() || !caseForm.handling.trim() || !caseForm.conclusion.trim()) {
    window.$message?.warning('案例标题、场景、处理过程和结论不能为空');
    return;
  }
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: '/knowledge-assistant/cases',
      method: 'POST',
      data: casePayload(caseForm)
    });
    if (!error) {
      window.$message?.success('案例已保存');
      resetCaseForm();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleUpdateCase() {
  if (!editingCaseId.value) return;
  if (!caseEditForm.title.trim() || !caseEditForm.scenario.trim() || !caseEditForm.handling.trim() || !caseEditForm.conclusion.trim()) {
    window.$message?.warning('案例标题、场景、处理过程和结论不能为空');
    return;
  }
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/cases/${editingCaseId.value}`,
      method: 'PUT',
      data: casePayload(caseEditForm)
    });
    if (!error) {
      window.$message?.success('案例已更新');
      cancelEditCase();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleToggleCase(item: Api.KnowledgeBase.AssistantCase) {
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/cases/${item.id}`,
      method: 'PUT',
      data: {
        title: item.title,
        scenario: item.scenario,
        handling: item.handling,
        conclusion: item.conclusion,
        tags: item.tags || null,
        knowledgeScope: item.knowledgeScope,
        departmentId: item.departmentId || null,
        status: item.status,
        enabled: !item.enabled
      }
    });
    if (!error) {
      window.$message?.success(item.enabled ? '案例已停用' : '案例已启用');
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleDeleteCase(item: Api.KnowledgeBase.AssistantCase) {
  governanceSaving.value = true;
  try {
    const { error } = await request({
      url: `/knowledge-assistant/cases/${item.id}`,
      method: 'DELETE'
    });
    if (!error) {
      window.$message?.success('案例已删除');
      if (editingCaseId.value === item.id) cancelEditCase();
      await refreshGovernanceData();
    }
  } finally {
    governanceSaving.value = false;
  }
}

async function handleGeneratePolicyDraft() {
  governanceSaving.value = true;
  try {
    const scope = currentSpace.value?.type === 'PUBLIC' ? 'PUBLIC' : 'DEPARTMENT';
    const { error, data } = await request<Api.KnowledgeBase.CasePolicyDraft>({
      url: '/knowledge-assistant/cases/policy-draft',
      method: 'POST',
      data: {
        knowledgeScope: scope,
        departmentId: scope === 'DEPARTMENT' ? currentSpace.value?.departmentId || authStore.userInfo.primaryOrg || null : null,
        title: `${currentSpace.value?.title || '部门'}案例沉淀制度草案`,
        category: currentSpace.value?.title || null
      }
    });
    if (!error) {
      casePolicyDraft.value = data;
      window.$message?.success('制度草案已生成');
    }
  } finally {
    governanceSaving.value = false;
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
        <NFormItem label="检索状态">
          <NSelect
            v-model:value="filterModel.retrievability"
            :options="retrievabilityOptions"
            placeholder="全部状态"
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
        <NTag type="success" :bordered="false">当前可检索 {{ filteredRetrievableCount }}</NTag>
      </div>
      <VueDraggable v-model="spaceBoardItems" :animation="180" class="knowledge-space-grid" @end="saveSpaceOrder">
        <article
          v-for="(space, index) in spaceBoardItems"
          :key="space.id"
          class="knowledge-space-card"
          :class="[space.type.toLowerCase(), `space-color-${index % 7}`, { active: selectedSpaceId === space.id }]"
          role="button"
          tabindex="0"
          @click="selectSpace(space.id)"
          @keydown.enter.prevent="selectSpace(space.id)"
          @keydown.space.prevent="selectSpace(space.id)"
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
          <div class="space-card-actions">
            <button type="button" class="space-mini-action" @click.stop="openTrainingQuiz(space)">生成题库</button>
            <button type="button" class="space-mini-action" @click.stop="openTrainingDeck(space)">生成 PPT</button>
          </div>
        </article>
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
            <NButton size="small" ghost type="warning" @click="openGovernanceManager">
              治理配置
            </NButton>
            <NButton size="small" ghost type="success" @click="openTopologyViewer">
              关系图
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
        :scroll-x="1720"
        :loading="loading"
        remote
        :row-key="row => row.fileMd5"
        :pagination="false"
        class="sm:h-full"
      />
    </NCard>

    <NModal v-model:show="trainingQuizVisible" preset="card" :title="trainingQuizResult?.title || `${trainingQuizSpace?.title || '知识库'} · 培训题库`" class="paper-modal max-w-960px w-[94%]">
      <div class="training-quiz-viewer">
        <div class="training-quiz-config">
          <div>
            <span>题目数量</span>
            <NInputNumber v-model:value="trainingQuizForm.questionCount" :min="1" :max="20" size="small" />
          </div>
          <div>
            <span>难度</span>
            <NSelect v-model:value="trainingQuizForm.difficulty" :options="trainingQuizDifficultyOptions" size="small" />
          </div>
          <div>
            <span>题型</span>
            <NCheckboxGroup v-model:value="trainingQuizForm.questionTypes">
              <NSpace>
                <NCheckbox v-for="item in trainingQuizTypeOptions" :key="item.value" :value="item.value" :label="item.label" />
              </NSpace>
            </NCheckboxGroup>
          </div>
          <NButton type="primary" :loading="trainingQuizLoading" @click="generateTrainingQuiz">开始生成</NButton>
        </div>

        <div v-if="trainingQuizLoading" class="empty-line">题库生成中，请稍候...</div>
        <template v-else-if="trainingQuizResult">
          <div class="training-quiz-summary">
            <div>
              <span>题目数量</span>
              <strong>{{ trainingQuizResult.questionCount }}</strong>
            </div>
            <div>
              <span>难度</span>
              <strong>{{ trainingQuizResult.difficulty }}</strong>
            </div>
            <div>
              <span>来源文件</span>
              <strong>{{ trainingQuizResult.sources.length }}</strong>
            </div>
          </div>

          <div class="training-quiz-list">
            <article v-for="(question, index) in trainingQuizResult.questions" :key="`${question.question}-${index}`" class="training-question-card">
              <div class="training-question-head">
                <NTag size="small" type="info" :bordered="false">第 {{ index + 1 }} 题</NTag>
                <NTag size="small" :bordered="false">{{ question.type === 'multiple_choice' ? '多选题' : '单选题' }}</NTag>
                <NTag size="small" :bordered="false">{{ question.difficulty }}</NTag>
                <NTag v-if="quizReview(index)" size="small" :type="quizReview(index)?.correct ? 'success' : 'error'" :bordered="false">
                  {{ quizReview(index)?.correct ? '正确' : '错误' }}
                </NTag>
              </div>
              <strong>{{ question.question }}</strong>
              <NRadioGroup
                v-if="question.type !== 'multiple_choice'"
                :value="selectedSingleChoice(index)"
                class="training-choice-group"
                @update:value="value => setSingleChoiceAnswer(index, value)"
              >
                <NSpace vertical>
                  <NRadio v-for="option in question.options" :key="option" :value="optionKey(option)">
                    {{ option }}
                  </NRadio>
                </NSpace>
              </NRadioGroup>
              <NCheckboxGroup
                v-else
                :value="selectedMultipleChoice(index)"
                class="training-choice-group"
                @update:value="value => setMultipleChoiceAnswer(index, value as string[])"
              >
                <NSpace vertical>
                  <NCheckbox v-for="option in question.options" :key="option" :value="optionKey(option)" :label="option" />
                </NSpace>
              </NCheckboxGroup>
              <div v-if="trainingQuizSubmitResult" class="training-answer">
                <span>答案</span>
                <p>{{ answerValue(question.answer) }}</p>
              </div>
              <div v-if="trainingQuizSubmitResult" class="training-answer">
                <span>解析</span>
                <p>{{ question.explanation }}</p>
              </div>
              <div class="training-source">来源：{{ question.sourceFile || '知识库材料' }}</div>
            </article>
          </div>

          <div class="training-submit-panel">
            <NButton type="primary" :loading="trainingQuizSubmitting" :disabled="Boolean(trainingQuizSubmitResult)" @click="submitTrainingQuiz">
              提交并自动审阅
            </NButton>
            <div v-if="trainingQuizSubmitResult" class="training-score">
              <strong>{{ trainingQuizSubmitResult.score }} 分</strong>
              <span>{{ trainingQuizSubmitResult.correctCount }} / {{ trainingQuizSubmitResult.totalCount }} 题正确</span>
            </div>
          </div>

          <div v-if="trainingQuizRanking.length" class="training-ranking">
            <div class="rule-panel-title">
              <strong>部门排名</strong>
              <NTag :bordered="false">按成绩排序</NTag>
            </div>
            <div v-for="row in trainingQuizRanking" :key="row.attemptId" class="ranking-row">
              <span class="ranking-no">#{{ row.rank }}</span>
              <strong>{{ row.username }}</strong>
              <span>{{ row.score }} 分</span>
              <span>{{ row.correctCount }}/{{ row.totalCount }}</span>
              <span>{{ row.durationSeconds || '-' }} 秒</span>
            </div>
          </div>

          <div class="training-source-list">
            <NTag v-for="source in trainingQuizResult.sources" :key="source.fileMd5" size="small" :bordered="false">
              {{ source.fileName }}
            </NTag>
          </div>
        </template>
        <div v-else class="empty-line">暂无题库结果</div>
      </div>
    </NModal>

    <NModal v-model:show="trainingDeckVisible" preset="card" :title="trainingDeckResult?.title || `${trainingDeckSpace?.title || '知识库'} · 培训课件`" class="paper-modal max-w-1080px w-[94%]">
      <div class="training-deck-viewer">
        <div class="training-deck-config">
          <div>
            <span>页数</span>
            <NInputNumber v-model:value="trainingDeckForm.slideCount" :min="3" :max="18" size="small" />
          </div>
          <div>
            <span>受众</span>
            <NInput v-model:value="trainingDeckForm.audience" size="small" />
          </div>
          <div>
            <span>风格</span>
            <NSelect v-model:value="trainingDeckForm.tone" :options="trainingDeckToneOptions" size="small" />
          </div>
          <div class="training-deck-actions">
            <NButton type="primary" :loading="trainingDeckLoading" @click="generateTrainingDeck">生成预览</NButton>
            <NButton ghost type="success" :disabled="!trainingDeckResult" :loading="trainingDeckExporting" @click="exportTrainingDeck">
              导出 PPTX
            </NButton>
            <NButton ghost type="primary" :loading="trainingDeckLoading || trainingDeckExporting" @click="generateAndExportTrainingDeck">
              一键生成并导出
            </NButton>
          </div>
        </div>

        <div v-if="trainingDeckLoading" class="empty-line">课件生成中，请稍候...</div>
        <template v-else-if="trainingDeckResult">
          <div class="training-quiz-summary">
            <div>
              <span>幻灯片</span>
              <strong>{{ trainingDeckResult.slideCount }}</strong>
            </div>
            <div>
              <span>受众</span>
              <strong>{{ trainingDeckResult.audience }}</strong>
            </div>
            <div>
              <span>来源文件</span>
              <strong>{{ trainingDeckResult.sources.length }}</strong>
            </div>
          </div>

          <div class="training-deck-list">
            <article v-for="slide in trainingDeckResult.slides" :key="`${slide.index}-${slide.title}`" class="training-slide-card">
              <div class="training-question-head">
                <NTag size="small" type="info" :bordered="false">第 {{ slide.index }} 页</NTag>
                <NTag size="small" :bordered="false">{{ trainingDeckResult.tone }}</NTag>
              </div>
              <strong>{{ slide.title }}</strong>
              <ul class="training-options">
                <li v-for="bullet in slide.bullets" :key="bullet">{{ bullet }}</li>
              </ul>
              <div class="training-answer">
                <span>讲师备注</span>
                <p>{{ slide.speakerNotes }}</p>
              </div>
              <div class="training-source">来源：{{ slide.sourceFiles?.join('、') || '知识库材料' }}</div>
            </article>
          </div>
        </template>
        <div v-else class="empty-line">暂无课件预览</div>
      </div>
    </NModal>

    <NModal v-model:show="topologyVisible" preset="card" title="文件拓扑结构图" class="paper-modal max-w-1080px w-[94%]">
      <div class="topology-viewer">
        <div class="topology-summary">
          <div>
            <span>文件节点</span>
            <strong>{{ documentTopology?.summary.nodeCount || 0 }}</strong>
          </div>
          <div>
            <span>关系总数</span>
            <strong>{{ documentTopology?.summary.edgeCount || 0 }}</strong>
          </div>
          <div>
            <span>同部门</span>
            <strong>{{ documentTopology?.summary.departmentEdgeCount || 0 }}</strong>
          </div>
          <div>
            <span>同分类</span>
            <strong>{{ documentTopology?.summary.categoryEdgeCount || 0 }}</strong>
          </div>
          <div>
            <span>生效中</span>
            <strong>{{ documentTopology?.summary.activeCount || 0 }}</strong>
          </div>
          <div>
            <span>已废止</span>
            <strong>{{ documentTopology?.summary.expiredCount || 0 }}</strong>
          </div>
          <div>
            <span>审计关注</span>
            <strong>{{ documentTopology?.summary.auditIssueCount || 0 }}</strong>
          </div>
        </div>

        <div v-if="topologyLoading" class="empty-line">拓扑加载中...</div>
        <div v-else class="topology-grid">
          <section class="rule-panel topology-graph-panel">
            <div class="rule-panel-title">
              <strong>知识网络</strong>
              <NTag :bordered="false">可拖拽 · 可缩放 · 点击节点预览文件</NTag>
            </div>
            <div ref="topologyGraphRef" class="topology-graph"></div>
          </section>

          <section class="rule-panel">
            <div class="rule-panel-title">
              <strong>文件节点</strong>
              <NTag :bordered="false">按生命周期排序</NTag>
            </div>
            <div class="topology-node-list">
              <article v-for="node in documentTopology?.nodes || []" :key="node.id" class="topology-node-card" :class="topologyNodeRiskClass(node)">
                <div class="topology-node-title">
                  <strong>{{ node.fileName }}</strong>
                  <NTag size="small" :type="node.retrievable ? 'success' : 'warning'" :bordered="false">
                    {{ node.retrievable ? '可检索' : '需关注' }}
                  </NTag>
                </div>
                <div class="topology-node-tags">
                  <NTag size="small" :type="topologyNodeMeta(node).lifecycle.type">{{ topologyNodeMeta(node).lifecycle.label }}</NTag>
                  <NTag size="small" :type="topologyNodeMeta(node).audit.type">{{ topologyNodeMeta(node).audit.label }}</NTag>
                  <NTag size="small" :bordered="false">{{ topologyNodeMeta(node).scope }}</NTag>
                  <NTag v-if="node.versionNo" size="small" :bordered="false">{{ node.versionNo }}</NTag>
                </div>
                <div class="topology-node-boundary">{{ topologyNodeMeta(node).boundary }}</div>
              </article>
              <div v-if="!(documentTopology?.nodes || []).length" class="empty-line">暂无文件节点</div>
            </div>
          </section>

          <section class="rule-panel">
            <div class="rule-panel-title">
              <strong>文件关系</strong>
              <NTag :bordered="false">替代 / 部门 / 分类 / 状态</NTag>
            </div>
            <div class="topology-edge-list">
              <article v-for="edge in documentTopology?.edges || []" :key="edge.id" class="topology-edge-card">
                <div>
                  <strong>{{ topologyNodeName(edge.source) }}</strong>
                  <NTag size="small" :type="topologyEdgeMeta(edge).type" :bordered="false">
                    {{ topologyEdgeMeta(edge).label }}
                  </NTag>
                  <strong>{{ topologyNodeName(edge.target) }}</strong>
                </div>
              </article>
              <div v-if="!(documentTopology?.edges || []).length" class="empty-line">暂无文件关系，维护部门、分类、生命周期或替代文件后会显示在这里</div>
            </div>
          </section>
        </div>
      </div>
    </NModal>

    <UploadDialog v-model:visible="uploadVisible" :initial-space="currentSpace" />
    <SearchDialog v-model:visible="searchVisible" />

    <NModal v-model:show="auditVisible" preset="card" title="制度审计详情" class="paper-modal max-w-780px w-[92%]">
      <div v-if="auditTask" class="audit-detail">
        <div class="detail-file">
          <strong>{{ auditTask.fileName }}</strong>
          <NTag :type="auditMeta(auditDetail?.policyAuditStatus || auditTask.policyAuditStatus).type">
            {{ auditMeta(auditDetail?.policyAuditStatus || auditTask.policyAuditStatus).label }}
          </NTag>
        </div>

        <div v-if="auditLoading" class="empty-line">审计结果加载中...</div>
        <template v-else>
          <div class="audit-score-card">
            <span>审计评分</span>
            <strong>{{ Math.round(auditDetail?.policyAuditScore ?? auditTask.policyAuditScore ?? 0) }}</strong>
          </div>
          <div class="audit-result-grid">
            <section>
              <span>审计摘要</span>
              <p>{{ auditDetail?.policyAuditSummary || '暂无审计摘要' }}</p>
            </section>
            <section>
              <span>问题与建议</span>
              <pre>{{ auditDetail?.policyAuditIssues || '暂无问题记录' }}</pre>
            </section>
          </div>

          <section class="audit-review-panel">
            <div class="rule-panel-title">
              <strong>人工复核</strong>
              <NButton size="small" ghost type="info" :loading="auditSaving" @click="handleRunPolicyAudit">
                重新运行审计
              </NButton>
            </div>
            <div class="rule-form-grid">
              <NSelect v-model:value="auditReviewForm.status" :options="auditStatusOptions" placeholder="复核状态" />
              <NInputNumber v-model:value="auditReviewForm.score" :min="0" :max="100" placeholder="评分" />
            </div>
            <NInput v-model:value="auditReviewForm.summary" type="textarea" placeholder="复核摘要" :autosize="{ minRows: 3, maxRows: 6 }" />
            <NInput v-model:value="auditReviewForm.issues" type="textarea" placeholder="问题与整改建议" :autosize="{ minRows: 4, maxRows: 8 }" />
            <div class="detail-actions">
              <NButton ghost @click="auditVisible = false">关闭</NButton>
              <NButton type="primary" :loading="auditSaving" @click="handleReviewPolicyAudit">保存复核</NButton>
            </div>
          </section>
        </template>
      </div>
    </NModal>

    <NModal v-model:show="lifecycleVisible" preset="card" title="文档生命周期" class="paper-modal max-w-760px w-[92%]">
      <div v-if="lifecycleTask" class="lifecycle-editor">
        <div class="detail-file">
          <strong>{{ lifecycleTask.fileName }}</strong>
          <NTag :type="lifecycleMeta(lifecycleForm.lifecycleStatus).type" :bordered="false">
            {{ lifecycleMeta(lifecycleForm.lifecycleStatus).label }}
          </NTag>
        </div>
        <div class="lifecycle-form-grid">
          <NFormItem label="生命周期状态">
            <NSelect v-model:value="lifecycleForm.lifecycleStatus" :options="lifecycleStatusOptions" />
          </NFormItem>
          <NFormItem label="版本号">
            <NInput v-model:value="lifecycleForm.versionNo" placeholder="例如 A1、V2026.06" />
          </NFormItem>
          <NFormItem label="生效时间">
            <NDatePicker v-model:value="lifecycleForm.effectiveAt" type="datetime" clearable class="w-full" />
          </NFormItem>
          <NFormItem label="废止时间">
            <NDatePicker v-model:value="lifecycleForm.abolishedAt" type="datetime" clearable class="w-full" />
          </NFormItem>
          <NFormItem label="发布时间">
            <NDatePicker v-model:value="lifecycleForm.publishedAt" type="datetime" clearable class="w-full" />
          </NFormItem>
          <NFormItem label="替代文件 MD5">
            <NInput v-model:value="lifecycleForm.supersedesFileMd5" placeholder="当前文件替代的旧文件" />
          </NFormItem>
          <NFormItem label="被替代文件 MD5">
            <NInput v-model:value="lifecycleForm.supersededByFileMd5" placeholder="替代当前文件的新文件" />
          </NFormItem>
        </div>
        <div class="detail-actions">
          <NButton ghost @click="lifecycleVisible = false">取消</NButton>
          <NButton type="primary" :loading="lifecycleSaving" @click="handleSaveLifecycle">保存生命周期</NButton>
        </div>
      </div>
    </NModal>

    <NModal v-model:show="governanceVisible" preset="card" title="知识治理配置" class="paper-modal max-w-1080px w-[94%]">
      <div class="governance-manager">
        <NTabs v-model:value="governanceActiveTab" type="segment" animated>
          <NTabPane name="faq" tab="标准问答">
            <div class="governance-grid">
              <section class="rule-panel">
                <div class="rule-panel-title">
                  <strong>问答对</strong>
                  <NButton size="tiny" ghost :loading="governanceLoading" @click="refreshGovernanceData">刷新</NButton>
                </div>
                <div class="governance-list">
                  <div v-for="item in faqItems" :key="item.id" class="governance-list-item">
                    <div class="governance-item-main">
                      <div class="governance-item-content">
                        <strong>{{ item.question }}</strong>
                        <span>{{ item.answer }}</span>
                        <em v-if="item.aliases">别名：{{ item.aliases }}</em>
                      </div>
                      <div class="governance-item-meta">
                        <NTag size="small" :type="item.knowledgeScope === 'PUBLIC' ? 'success' : 'info'">
                          {{ item.knowledgeScope === 'PUBLIC' ? '公共' : item.departmentId || '部门' }}
                        </NTag>
                        <NTag size="small" :type="item.enabled ? 'success' : 'warning'">
                          {{ item.enabled ? '启用' : '停用' }}
                        </NTag>
                      </div>
                      <div class="governance-actions">
                        <NButton size="tiny" quaternary type="primary" @click="beginEditFaq(item)">编辑</NButton>
                        <NButton size="tiny" quaternary :type="item.enabled ? 'warning' : 'success'" @click="handleToggleFaq(item)">
                          {{ item.enabled ? '停用' : '启用' }}
                        </NButton>
                        <NPopconfirm @positive-click="handleDeleteFaq(item)">
                          <template #trigger>
                            <NButton size="tiny" quaternary type="error">删除</NButton>
                          </template>
                          确认删除这个问答对吗？
                        </NPopconfirm>
                      </div>
                    </div>
                    <div v-if="editingFaqId === item.id" class="governance-edit-panel">
                      <div class="rule-form-grid">
                        <NSelect
                          v-model:value="faqEditForm.knowledgeScope"
                          :options="knowledgeScopeOptions.filter(option => option.value !== 'PRIVATE')"
                          placeholder="适用范围"
                        />
                        <OrgTagCascader
                          v-if="faqEditForm.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin"
                          v-model:value="faqEditForm.departmentId"
                          clearable
                        />
                        <TheSelect
                          v-else-if="faqEditForm.knowledgeScope === 'DEPARTMENT'"
                          v-model:value="faqEditForm.departmentId"
                          url="/users/org-tags"
                          key-field="orgTagDetails"
                          label-field="name"
                          value-field="tagId"
                          clearable
                        />
                      </div>
                      <NInput v-model:value="faqEditForm.question" placeholder="标准问题" />
                      <NInput v-model:value="faqEditForm.aliases" placeholder="同义问法，多个用逗号或换行分隔" />
                      <NInput
                        v-model:value="faqEditForm.answer"
                        type="textarea"
                        placeholder="标准答案"
                        :autosize="{ minRows: 4, maxRows: 8 }"
                      />
                      <div class="detail-actions">
                        <NButton ghost @click="cancelEditFaq">取消</NButton>
                        <NButton type="primary" :loading="governanceSaving" @click="handleUpdateFaq">保存修改</NButton>
                      </div>
                    </div>
                  </div>
                  <div v-if="!faqItems.length" class="empty-line">暂无问答对</div>
                </div>
              </section>
              <section class="rule-panel">
                <div class="rule-panel-title">
                  <strong>新增问答对</strong>
                </div>
                <div class="rule-form-grid">
                  <NSelect
                    v-model:value="faqForm.knowledgeScope"
                    :options="knowledgeScopeOptions.filter(item => item.value !== 'PRIVATE')"
                    placeholder="适用范围"
                  />
                  <OrgTagCascader
                    v-if="faqForm.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin"
                    v-model:value="faqForm.departmentId"
                    clearable
                  />
                  <TheSelect
                    v-else-if="faqForm.knowledgeScope === 'DEPARTMENT'"
                    v-model:value="faqForm.departmentId"
                    url="/users/org-tags"
                    key-field="orgTagDetails"
                    label-field="name"
                    value-field="tagId"
                    clearable
                  />
                </div>
                <NInput v-model:value="faqForm.question" placeholder="标准问题" />
                <NInput v-model:value="faqForm.aliases" placeholder="同义问法，多个用逗号或换行分隔" />
                <NInput v-model:value="faqForm.answer" type="textarea" placeholder="标准答案" :autosize="{ minRows: 5, maxRows: 10 }" />
                <div class="detail-actions">
                  <NButton type="primary" :loading="governanceSaving" @click="handleCreateFaq">保存问答对</NButton>
                </div>
              </section>
            </div>
          </NTabPane>
          <NTabPane name="term" tab="术语词典">
            <div class="governance-grid">
              <section class="rule-panel">
                <div class="rule-panel-title">
                  <strong>术语</strong>
                  <NButton size="tiny" ghost :loading="governanceLoading" @click="refreshGovernanceData">刷新</NButton>
                </div>
                <div class="governance-list">
                  <div v-for="item in termItems" :key="item.id" class="governance-list-item">
                    <div class="governance-item-main">
                      <div class="governance-item-content">
                        <strong>{{ item.term }}</strong>
                        <span>{{ item.definition || '未填写定义' }}</span>
                        <em v-if="item.synonyms">同义词：{{ item.synonyms }}</em>
                      </div>
                      <div class="governance-item-meta">
                        <NTag size="small" :type="item.knowledgeScope === 'PUBLIC' ? 'success' : 'info'">
                          {{ item.knowledgeScope === 'PUBLIC' ? '公共' : item.departmentId || '部门' }}
                        </NTag>
                        <NTag size="small" :type="item.enabled ? 'success' : 'warning'">
                          {{ item.enabled ? '启用' : '停用' }}
                        </NTag>
                      </div>
                      <div class="governance-actions">
                        <NButton size="tiny" quaternary type="primary" @click="beginEditTerm(item)">编辑</NButton>
                        <NButton size="tiny" quaternary :type="item.enabled ? 'warning' : 'success'" @click="handleToggleTerm(item)">
                          {{ item.enabled ? '停用' : '启用' }}
                        </NButton>
                        <NPopconfirm @positive-click="handleDeleteTerm(item)">
                          <template #trigger>
                            <NButton size="tiny" quaternary type="error">删除</NButton>
                          </template>
                          确认删除这个术语吗？
                        </NPopconfirm>
                      </div>
                    </div>
                    <div v-if="editingTermId === item.id" class="governance-edit-panel">
                      <div class="rule-form-grid">
                        <NSelect
                          v-model:value="termEditForm.knowledgeScope"
                          :options="knowledgeScopeOptions.filter(option => option.value !== 'PRIVATE')"
                          placeholder="适用范围"
                        />
                        <OrgTagCascader
                          v-if="termEditForm.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin"
                          v-model:value="termEditForm.departmentId"
                          clearable
                        />
                        <TheSelect
                          v-else-if="termEditForm.knowledgeScope === 'DEPARTMENT'"
                          v-model:value="termEditForm.departmentId"
                          url="/users/org-tags"
                          key-field="orgTagDetails"
                          label-field="name"
                          value-field="tagId"
                          clearable
                        />
                      </div>
                      <NInput v-model:value="termEditForm.term" placeholder="标准术语" />
                      <NInput v-model:value="termEditForm.synonyms" placeholder="同义表达，多个用逗号或换行分隔" />
                      <NInput
                        v-model:value="termEditForm.definition"
                        type="textarea"
                        placeholder="定义说明"
                        :autosize="{ minRows: 4, maxRows: 8 }"
                      />
                      <div class="detail-actions">
                        <NButton ghost @click="cancelEditTerm">取消</NButton>
                        <NButton type="primary" :loading="governanceSaving" @click="handleUpdateTerm">保存修改</NButton>
                      </div>
                    </div>
                  </div>
                  <div v-if="!termItems.length" class="empty-line">暂无术语</div>
                </div>
              </section>
              <section class="rule-panel">
                <div class="rule-panel-title">
                  <strong>新增术语</strong>
                </div>
                <div class="rule-form-grid">
                  <NSelect
                    v-model:value="termForm.knowledgeScope"
                    :options="knowledgeScopeOptions.filter(item => item.value !== 'PRIVATE')"
                    placeholder="适用范围"
                  />
                  <OrgTagCascader
                    v-if="termForm.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin"
                    v-model:value="termForm.departmentId"
                    clearable
                  />
                  <TheSelect
                    v-else-if="termForm.knowledgeScope === 'DEPARTMENT'"
                    v-model:value="termForm.departmentId"
                    url="/users/org-tags"
                    key-field="orgTagDetails"
                    label-field="name"
                    value-field="tagId"
                    clearable
                  />
                </div>
                <NInput v-model:value="termForm.term" placeholder="标准术语" />
                <NInput v-model:value="termForm.synonyms" placeholder="同义表达，多个用逗号或换行分隔" />
                <NInput v-model:value="termForm.definition" type="textarea" placeholder="定义说明" :autosize="{ minRows: 5, maxRows: 10 }" />
                <div class="detail-actions">
                  <NButton type="primary" :loading="governanceSaving" @click="handleCreateTerm">保存术语</NButton>
                </div>
              </section>
            </div>
          </NTabPane>
          <NTabPane name="case" tab="案例库">
            <div class="governance-grid">
              <section class="rule-panel">
                <div class="rule-panel-title">
                  <strong>案例</strong>
                  <NButton size="tiny" ghost :loading="governanceLoading" @click="refreshGovernanceData">刷新</NButton>
                </div>
                <div class="governance-list">
                  <div v-for="item in caseItems" :key="item.id" class="governance-list-item">
                    <div class="governance-item-main">
                      <div class="governance-item-content">
                        <strong>{{ item.title }}</strong>
                        <span>{{ item.scenario }}</span>
                        <em>结论：{{ item.conclusion }}</em>
                      </div>
                      <div class="governance-item-meta">
                        <NTag size="small" :type="item.knowledgeScope === 'PUBLIC' ? 'success' : 'info'">
                          {{ item.knowledgeScope === 'PUBLIC' ? '公共' : item.departmentId || '部门' }}
                        </NTag>
                        <NTag size="small" :type="item.status === 'APPROVED' ? 'success' : item.status === 'ARCHIVED' ? 'default' : 'warning'">
                          {{ item.status === 'APPROVED' ? '已审核' : item.status === 'ARCHIVED' ? '归档' : '草稿' }}
                        </NTag>
                        <NTag size="small" :type="item.enabled ? 'success' : 'warning'">
                          {{ item.enabled ? '启用' : '停用' }}
                        </NTag>
                      </div>
                      <div class="governance-actions">
                        <NButton size="tiny" quaternary type="primary" @click="beginEditCase(item)">编辑</NButton>
                        <NButton size="tiny" quaternary :type="item.enabled ? 'warning' : 'success'" @click="handleToggleCase(item)">
                          {{ item.enabled ? '停用' : '启用' }}
                        </NButton>
                        <NPopconfirm @positive-click="handleDeleteCase(item)">
                          <template #trigger>
                            <NButton size="tiny" quaternary type="error">删除</NButton>
                          </template>
                          确认删除这个案例吗？
                        </NPopconfirm>
                      </div>
                    </div>
                    <div v-if="editingCaseId === item.id" class="governance-edit-panel">
                      <div class="rule-form-grid">
                        <NSelect
                          v-model:value="caseEditForm.knowledgeScope"
                          :options="knowledgeScopeOptions.filter(option => option.value !== 'PRIVATE')"
                          placeholder="适用范围"
                        />
                        <NSelect
                          v-model:value="caseEditForm.status"
                          :options="[
                            { label: '草稿', value: 'DRAFT' },
                            { label: '已审核', value: 'APPROVED' },
                            { label: '归档', value: 'ARCHIVED' }
                          ]"
                        />
                        <OrgTagCascader
                          v-if="caseEditForm.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin"
                          v-model:value="caseEditForm.departmentId"
                          clearable
                        />
                        <TheSelect
                          v-else-if="caseEditForm.knowledgeScope === 'DEPARTMENT'"
                          v-model:value="caseEditForm.departmentId"
                          url="/users/org-tags"
                          key-field="orgTagDetails"
                          label-field="name"
                          value-field="tagId"
                          clearable
                        />
                      </div>
                      <NInput v-model:value="caseEditForm.title" placeholder="案例标题" />
                      <NInput v-model:value="caseEditForm.tags" placeholder="标签，多个用逗号分隔" />
                      <NInput v-model:value="caseEditForm.scenario" type="textarea" placeholder="案例场景" :autosize="{ minRows: 3, maxRows: 6 }" />
                      <NInput v-model:value="caseEditForm.handling" type="textarea" placeholder="处理过程" :autosize="{ minRows: 3, maxRows: 6 }" />
                      <NInput v-model:value="caseEditForm.conclusion" type="textarea" placeholder="处理结论" :autosize="{ minRows: 3, maxRows: 6 }" />
                      <div class="detail-actions">
                        <NButton ghost @click="cancelEditCase">取消</NButton>
                        <NButton type="primary" :loading="governanceSaving" @click="handleUpdateCase">保存修改</NButton>
                      </div>
                    </div>
                  </div>
                  <div v-if="!caseItems.length" class="empty-line">暂无案例</div>
                </div>
              </section>
              <section class="rule-panel">
                <div class="rule-panel-title">
                  <strong>新增案例</strong>
                </div>
                <div class="rule-form-grid">
                  <NSelect
                    v-model:value="caseForm.knowledgeScope"
                    :options="knowledgeScopeOptions.filter(item => item.value !== 'PRIVATE')"
                    placeholder="适用范围"
                  />
                  <NSelect
                    v-model:value="caseForm.status"
                    :options="[
                      { label: '草稿', value: 'DRAFT' },
                      { label: '已审核', value: 'APPROVED' },
                      { label: '归档', value: 'ARCHIVED' }
                    ]"
                  />
                  <OrgTagCascader
                    v-if="caseForm.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin"
                    v-model:value="caseForm.departmentId"
                    clearable
                  />
                  <TheSelect
                    v-else-if="caseForm.knowledgeScope === 'DEPARTMENT'"
                    v-model:value="caseForm.departmentId"
                    url="/users/org-tags"
                    key-field="orgTagDetails"
                    label-field="name"
                    value-field="tagId"
                    clearable
                  />
                </div>
                <NInput v-model:value="caseForm.title" placeholder="案例标题" />
                <NInput v-model:value="caseForm.tags" placeholder="标签，多个用逗号分隔" />
                <NInput v-model:value="caseForm.scenario" type="textarea" placeholder="案例场景" :autosize="{ minRows: 3, maxRows: 6 }" />
                <NInput v-model:value="caseForm.handling" type="textarea" placeholder="处理过程" :autosize="{ minRows: 3, maxRows: 6 }" />
                <NInput v-model:value="caseForm.conclusion" type="textarea" placeholder="处理结论" :autosize="{ minRows: 3, maxRows: 6 }" />
                <div class="detail-actions">
                  <NButton type="primary" :loading="governanceSaving" @click="handleCreateCase">保存案例</NButton>
                  <NButton ghost type="success" :loading="governanceSaving" @click="handleGeneratePolicyDraft">生成制度草案</NButton>
                </div>
                <div v-if="casePolicyDraft" class="policy-draft-box">
                  <div class="rule-panel-title">
                    <strong>{{ casePolicyDraft.title }}</strong>
                    <NTag size="small" :bordered="false">来源 {{ casePolicyDraft.caseCount }} 条案例</NTag>
                  </div>
                  <pre>{{ casePolicyDraft.draft }}</pre>
                </div>
              </section>
            </div>
          </NTabPane>
          <NTabPane name="suggestion" tab="学习建议">
            <section class="rule-panel">
              <div class="rule-panel-title">
                <strong>高频问答建议</strong>
                <NButton size="tiny" ghost :loading="governanceLoading" @click="refreshGovernanceData">刷新</NButton>
              </div>
              <div class="governance-list">
                <div v-for="item in faqSuggestionItems" :key="item.id" class="governance-list-item">
                  <div class="governance-item-main">
                    <div class="governance-item-content">
                      <strong>{{ item.question }}</strong>
                      <span>{{ item.suggestedAnswer || '暂无建议答案' }}</span>
                      <em>
                        命中 {{ item.hitCount }} 次 · 证据 {{ item.evidenceCount }} 条
                        <template v-if="item.lastAskedAt"> · 最近 {{ dayjs(item.lastAskedAt).format('YYYY-MM-DD HH:mm') }}</template>
                      </em>
                    </div>
                    <div class="governance-item-meta">
                      <NTag size="small" :type="item.knowledgeScope === 'PUBLIC' ? 'success' : 'info'">
                        {{ item.knowledgeScope === 'PUBLIC' ? '公共' : item.departmentId || '部门' }}
                      </NTag>
                      <NTag size="small" :type="item.status === 'PENDING' ? 'warning' : item.status === 'ACCEPTED' ? 'success' : 'default'">
                        {{ item.status === 'PENDING' ? '待处理' : item.status === 'ACCEPTED' ? '已采纳' : '已忽略' }}
                      </NTag>
                    </div>
                    <div class="governance-actions">
                      <NButton
                        size="tiny"
                        quaternary
                        type="success"
                        :disabled="item.status === 'ACCEPTED'"
                        @click="handleUpdateSuggestionStatus(item, 'ACCEPTED')"
                      >
                        标记采纳
                      </NButton>
                      <NButton
                        size="tiny"
                        quaternary
                        :type="item.status === 'IGNORED' ? 'warning' : 'default'"
                        @click="handleUpdateSuggestionStatus(item, item.status === 'IGNORED' ? 'PENDING' : 'IGNORED')"
                      >
                        {{ item.status === 'IGNORED' ? '恢复' : '忽略' }}
                      </NButton>
                    </div>
                  </div>
                </div>
                <div v-if="!faqSuggestionItems.length" class="empty-line">暂无学习建议，助手基于知识库回答高频问题后会自动沉淀在这里</div>
              </div>
            </section>
          </NTabPane>
        </NTabs>
      </div>
    </NModal>

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
    <NModal
      v-model:show="previewVisible"
      preset="card"
      title="文件预览"
      class="paper-modal max-w-1000px w-[80%]"
      @after-leave="closeFilePreview"
    >
      <FilePreview
        v-if="previewFileName || previewFileMd5"
        in-modal
        :file-name="previewFileName"
        :file-md5="previewFileMd5"
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
  font: inherit;
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

.space-card-actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.space-mini-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  border: 1px solid rgb(255 255 255 / 0.68);
  border-radius: 999px;
  background: rgb(255 255 255 / 0.58);
  padding: 0 10px;
  color: rgb(28 53 60);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.58);
  cursor: pointer;
}

.space-mini-action:hover {
  background: rgb(255 255 255 / 0.82);
}

.training-quiz-viewer {
  display: grid;
  gap: 16px;
}

.training-quiz-config,
.training-deck-config {
  display: grid;
  grid-template-columns: 120px 160px minmax(220px, 1fr) auto;
  align-items: end;
  gap: 10px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.training-deck-config {
  grid-template-columns: 120px minmax(180px, 1fr) 160px minmax(190px, auto);
}

.training-quiz-config > div,
.training-deck-config > div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.training-quiz-config span,
.training-deck-config span {
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 12px;
}

.training-deck-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.training-quiz-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.training-quiz-summary > div {
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.training-quiz-summary span,
.training-source,
.training-answer span {
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 12px;
}

.training-quiz-summary strong {
  display: block;
  margin-top: 4px;
  font-size: 18px;
}

.training-quiz-list {
  display: grid;
  gap: 10px;
  max-height: 58vh;
  overflow: auto;
}

.training-question-card {
  display: grid;
  gap: 10px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.training-choice-group {
  border: 1px solid rgb(15 23 42 / 0.06);
  border-radius: 8px;
  background: rgb(248 250 252 / 0.7);
  padding: 10px 12px;
}

.training-submit-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.training-score {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.training-score strong {
  color: rgb(var(--primary-color));
  font-size: 22px;
}

.training-ranking {
  display: grid;
  gap: 8px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.ranking-row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 90px 80px 80px;
  align-items: center;
  gap: 8px;
  border-radius: 6px;
  background: rgb(248 250 252 / 0.72);
  padding: 8px 10px;
  font-size: 13px;
}

.ranking-no {
  color: rgb(var(--primary-color));
  font-weight: 700;
}

.policy-draft-box {
  display: grid;
  gap: 10px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: rgb(248 250 252 / 0.72);
  padding: 12px;
}

.policy-draft-box pre {
  max-height: 420px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  line-height: 1.7;
}

.training-deck-viewer {
  display: grid;
  gap: 16px;
}

.training-deck-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  max-height: 58vh;
  overflow: auto;
}

.training-slide-card {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 220px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: linear-gradient(145deg, rgb(255 255 255), rgb(246 248 250));
  padding: 14px;
  box-shadow: 0 14px 34px -28px rgb(15 23 42 / 0.45);
}

.training-slide-card > strong {
  font-size: 16px;
  line-height: 1.35;
}

.training-question-head,
.training-source-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.training-options {
  margin: 0;
  padding-left: 18px;
  color: rgb(var(--base-text-color) / 0.78);
  font-size: 13px;
  line-height: 1.7;
}

.training-answer {
  display: grid;
  gap: 4px;
}

.training-answer p {
  margin: 0;
  color: rgb(var(--base-text-color) / 0.82);
  line-height: 1.6;
}

html.dark .training-quiz-config,
html.dark .training-deck-config,
html.dark .training-quiz-summary > div,
html.dark .training-question-card,
html.dark .training-slide-card {
  border-color: rgb(255 255 255 / 0.08);
}

html.dark .training-slide-card {
  background: linear-gradient(145deg, rgb(31 41 55), rgb(17 24 39));
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

.lifecycle-range {
  display: grid;
  gap: 2px;
  color: rgb(var(--base-text-color) / 0.72);
  font-size: 12px;
  line-height: 1.45;
}

.lifecycle-status-stack {
  display: inline-grid;
  gap: 4px;
}

.lifecycle-editor,
.governance-manager {
  display: grid;
  gap: 16px;
}

.lifecycle-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
}

.governance-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 14px;
}

.governance-list {
  display: grid;
  gap: 8px;
  max-height: 420px;
  overflow: auto;
}

.governance-list-item {
  display: grid;
  gap: 10px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 10px 12px;
}

.governance-item-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.governance-item-content {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.governance-item-content strong,
.governance-item-content span,
.governance-item-content em {
  overflow: hidden;
  text-overflow: ellipsis;
}

.governance-item-content span,
.governance-item-content em {
  color: rgb(var(--base-text-color) / 0.62);
  font-size: 12px;
  line-height: 1.55;
}

.governance-item-content em {
  font-style: normal;
}

.governance-item-meta,
.governance-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 6px;
}

.governance-edit-panel {
  display: grid;
  gap: 10px;
  border-radius: 8px;
  background: rgb(15 23 42 / 0.035);
  padding: 10px;
}

html.dark .governance-edit-panel {
  background: rgb(255 255 255 / 0.06);
}

.topology-viewer {
  display: grid;
  gap: 16px;
}

.topology-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.topology-summary > div {
  display: grid;
  gap: 6px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.topology-summary span,
.topology-node-boundary,
.topology-edge-card span {
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 12px;
}

.topology-summary strong {
  font-size: 20px;
  line-height: 1;
}

.topology-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: 14px;
}

.topology-graph-panel {
  grid-column: 1 / -1;
}

.topology-graph {
  width: 100%;
  height: min(58vh, 540px);
  min-height: 380px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background:
    radial-gradient(circle at 18% 22%, rgb(59 130 246 / 0.10), transparent 24%),
    radial-gradient(circle at 78% 32%, rgb(16 185 129 / 0.10), transparent 26%),
    linear-gradient(180deg, rgb(248 250 252 / 0.88), rgb(255 255 255));
}

.topology-node-list,
.topology-edge-list {
  display: grid;
  gap: 10px;
  max-height: 520px;
  overflow: auto;
}

.topology-node-card,
.topology-edge-card {
  display: grid;
  gap: 8px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: linear-gradient(145deg, rgb(255 255 255 / 0.9), rgb(248 250 252 / 0.72));
  padding: 12px;
}

.topology-node-card.audit-reject {
  border-color: rgb(239 68 68 / 0.42);
  background: linear-gradient(145deg, rgb(254 242 242), rgb(255 255 255 / 0.86));
}

.topology-node-card.audit-warning {
  border-color: rgb(245 158 11 / 0.42);
  background: linear-gradient(145deg, rgb(255 251 235), rgb(255 255 255 / 0.86));
}

.topology-node-card.not-retrievable {
  border-color: rgb(100 116 139 / 0.28);
  background: linear-gradient(145deg, rgb(248 250 252), rgb(255 255 255 / 0.78));
}

.topology-node-title {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 10px;
}

.topology-node-title strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topology-node-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.topology-edge-card > div {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.topology-edge-card strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audit-detail {
  display: grid;
  gap: 16px;
}

.audit-score-card {
  display: grid;
  gap: 6px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  background: rgb(16 185 129 / 0.08);
  padding: 14px;
}

.audit-score-card span,
.audit-result-grid span {
  color: rgb(var(--base-text-color) / 0.58);
  font-size: 12px;
}

.audit-score-card strong {
  color: rgb(5 150 105);
  font-size: 28px;
  line-height: 1;
}

.audit-result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.audit-result-grid section,
.audit-review-panel {
  display: grid;
  gap: 8px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 8px;
  padding: 12px;
}

.audit-result-grid p,
.audit-result-grid pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: rgb(var(--base-text-color) / 0.82);
  font-size: 13px;
  line-height: 1.7;
}

html.dark .topology-summary > div,
html.dark .topology-node-card,
html.dark .topology-edge-card,
html.dark .audit-score-card,
html.dark .audit-result-grid section,
html.dark .audit-review-panel {
  border-color: rgb(255 255 255 / 0.08);
  background: rgb(255 255 255 / 0.06);
}

html.dark .topology-node-card.audit-reject {
  border-color: rgb(248 113 113 / 0.38);
  background: rgb(127 29 29 / 0.22);
}

html.dark .topology-node-card.audit-warning {
  border-color: rgb(251 191 36 / 0.38);
  background: rgb(113 63 18 / 0.2);
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

  .governance-grid {
    grid-template-columns: 1fr;
  }

  .topology-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .topology-grid {
    grid-template-columns: 1fr;
  }

  .audit-result-grid {
    grid-template-columns: 1fr;
  }

  .training-quiz-config {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .training-deck-config {
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
  .governance-item-main,
  .preview-grid {
    grid-template-columns: 1fr;
  }

  .governance-item-meta,
  .governance-actions {
    flex-wrap: wrap;
  }

  .training-quiz-config,
  .training-deck-config,
  .training-quiz-summary {
    grid-template-columns: 1fr;
  }
}

:deep() {
  .n-progress-icon.n-progress-icon--as-text {
    white-space: nowrap;
  }
}
</style>
