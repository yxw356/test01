<script setup lang="ts">
import type { UploadFileInfo } from 'naive-ui';
import type { KnowledgeSpace } from '../utils/knowledge-space';
import { defaultMaxUploadFileSize, uploadAccept } from '@/constants/common';

defineOptions({
  name: 'UploadDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });
const props = defineProps<{
  initialSpace?: KnowledgeSpace | null;
}>();

const authStore = useAuthStore();

const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

type KnowledgeScope = Api.KnowledgeBase.Form['knowledgeScope'];

const canUploadPublic = computed(() => authStore.isSuperAdmin || authStore.userInfo.role === 'KNOWLEDGE_ADMIN');
const canUploadDepartment = computed(() => authStore.isSuperAdmin || authStore.isDeptLead);
const canUploadPrivate = computed(() => Boolean(authStore.userInfo.id || authStore.userInfo.username));

const knowledgeScopeOptions = computed<{ label: string; value: KnowledgeScope; description: string }[]>(() => {
  const options: { label: string; value: KnowledgeScope; description: string }[] = [];
  if (canUploadPublic.value) {
    options.push({
      label: '公共知识',
      value: 'PUBLIC',
      description: '对所有用户可见，适合制度、公告、通用资料'
    });
  }
  if (canUploadDepartment.value) {
    options.push({
      label: '部门专有知识',
      value: 'DEPARTMENT',
      description: '仅所属部门成员、部门负责人和超级管理员可见'
    });
  }
  if (canUploadPrivate.value) {
    options.push({
      label: '个人知识',
      value: 'PRIVATE',
      description: '仅本人可见，适合个人笔记与草稿资料'
    });
  }
  return options;
});

const defaultKnowledgeScope = computed<KnowledgeScope>(() => knowledgeScopeOptions.value[0]?.value ?? 'DEPARTMENT');

const model = ref<Api.KnowledgeBase.Form>(createDefaultModel());

function initialKnowledgeScope(): KnowledgeScope {
  if (props.initialSpace?.type === 'PUBLIC' && canUploadPublic.value) return 'PUBLIC';
  if (props.initialSpace?.type === 'DEPARTMENT' && canUploadDepartment.value) return 'DEPARTMENT';
  if (props.initialSpace?.type === 'PRIVATE' && canUploadPrivate.value) return 'PRIVATE';
  return defaultKnowledgeScope.value;
}

function createDefaultModel(): Api.KnowledgeBase.Form {
  const knowledgeScope = initialKnowledgeScope();
  const departmentId = knowledgeScope === 'DEPARTMENT' ? props.initialSpace?.departmentId || null : null;
  return {
    orgTag: departmentId,
    orgTagName: '',
    knowledgeScope,
    departmentId,
    categoryId: null,
    categoryName: null,
    cleaningRuleSetId: null,
    isPublic: knowledgeScope === 'PUBLIC',
    fileList: []
  };
}

const rules = ref<FormRules>({
  knowledgeScope: defaultRequiredRule,
  departmentId: {
    trigger: ['change', 'blur'],
    validator: (_rule, value) => {
      if (model.value.knowledgeScope === 'DEPARTMENT' && !value) {
        return new Error('请选择所属部门');
      }
      return true;
    }
  },
  fileList: defaultRequiredRule
});

function close() {
  visible.value = false;
}

const store = useKnowledgeBaseStore();
const { categories, categoryLoading, uploadPreflight, cleaningRuleSets, cleaningRuleSetLoading } = storeToRefs(store);
const categoryCreateVisible = ref(false);
const categoryCreateLoading = ref(false);
const categoryCreateModel = ref({ name: '', description: '' });

const maxUploadFileSize = computed(() => uploadPreflight.value?.uploadLimits?.maxFileSize ?? defaultMaxUploadFileSize);
const maxUploadFileSizeLabel = computed(() => uploadPreflight.value?.uploadLimits?.maxFileSizeLabel ?? formatBytes(defaultMaxUploadFileSize));

const categoryOptions = computed(() => {
  return categories.value
    .filter(item => {
      if (item.knowledgeScope !== model.value.knowledgeScope) return false;
      if (item.knowledgeScope === 'DEPARTMENT') return item.departmentId === model.value.departmentId;
      return true;
    })
    .map(item => ({ label: item.name, value: item.id }));
});

const cleaningRuleOptions = computed(() => {
  return cleaningRuleSets.value
    .filter(item => {
      if (model.value.knowledgeScope === 'PUBLIC') return item.knowledgeScope === 'PUBLIC';
      if (model.value.knowledgeScope === 'PRIVATE') {
        return item.knowledgeScope === 'PRIVATE' || item.knowledgeScope === 'PUBLIC';
      }
      if (model.value.knowledgeScope === 'DEPARTMENT') {
        return item.knowledgeScope === 'PUBLIC' || item.departmentId === model.value.departmentId;
      }
      return false;
    })
    .map(item => ({
      label: `${item.name}${item.knowledgeScope === 'PUBLIC' ? ' · 公共' : ' · 部门'}`,
      value: item.id
    }));
});

function syncCleaningRuleSelection() {
  if (!model.value.cleaningRuleSetId) return;
  const exists = cleaningRuleOptions.value.some(item => item.value === model.value.cleaningRuleSetId);
  if (!exists) model.value.cleaningRuleSetId = null;
}

function syncCategoryName() {
  const category = categories.value.find(item => item.id === model.value.categoryId);
  model.value.categoryName = category?.name ?? null;
}

function openCategoryCreate() {
  if (model.value.knowledgeScope === 'DEPARTMENT' && !model.value.departmentId) {
    window.$message?.warning('请先选择所属部门');
    return;
  }
  categoryCreateModel.value = { name: '', description: '' };
  categoryCreateVisible.value = true;
}

async function handleCreateCategory() {
  const name = categoryCreateModel.value.name.trim();
  if (!name) {
    window.$message?.warning('请输入分类名称');
    return;
  }

  categoryCreateLoading.value = true;
  const ok = await store.createCategory({
    name,
    description: categoryCreateModel.value.description.trim(),
    knowledgeScope: model.value.knowledgeScope,
    departmentId: model.value.knowledgeScope === 'DEPARTMENT' ? model.value.departmentId : null,
    sortOrder: 100
  });
  categoryCreateLoading.value = false;

  if (ok) {
    const created = categories.value.find(
      item =>
        item.name === name &&
        item.knowledgeScope === model.value.knowledgeScope &&
        (item.knowledgeScope !== 'DEPARTMENT' || item.departmentId === model.value.departmentId)
    );
    model.value.categoryId = created?.id ?? null;
    model.value.categoryName = created?.name ?? null;
    categoryCreateVisible.value = false;
    window.$message?.success('分类已创建');
  }
}

async function handleSubmit() {
  await validate();
  loading.value = true;
  const ready = await store.checkUploadPreflight();
  if (!ready) {
    loading.value = false;
    return;
  }
  await store.enqueueUpload(model.value);
  loading.value = false;
  close();
}

function beforeUpload(options: { file: UploadFileInfo }) {
  const file = options.file.file;
  if (!file) return false;

  if (file.size > maxUploadFileSize.value) {
    window.$message?.error(`文件大小超过限制，最大支持 ${maxUploadFileSizeLabel.value}`);
    return false;
  }

  return true;
}

function formatBytes(bytes: number) {
  const mb = bytes / 1024 / 1024;
  if (mb >= 1) return `${mb.toFixed(2)}MB`;
  return `${(bytes / 1024).toFixed(2)}KB`;
}

watch(visible, () => {
  if (visible.value) {
    model.value = createDefaultModel();
    restoreValidation();
    store.refreshCategories();
    store.refreshCleaningRuleSets();
    if (knowledgeScopeOptions.value.length === 0) {
      window.$message?.warning('当前角色暂无上传公共或部门知识的权限');
      close();
    }
  }
});

function onUpdate(option: unknown) {
  if (option) model.value.orgTagName = (option as Api.OrgTag.Item).name;
}

watch(
  () => model.value.knowledgeScope,
  scope => {
    model.value.isPublic = scope === 'PUBLIC';
    if (scope === 'PUBLIC') {
      model.value.departmentId = null;
      model.value.orgTag = null;
      model.value.orgTagName = '';
    }
    model.value.categoryId = null;
    model.value.categoryName = null;
    syncCleaningRuleSelection();
  }
);

watch(
  () => model.value.departmentId,
  value => {
    model.value.orgTag = value;
    model.value.categoryId = null;
    model.value.categoryName = null;
    syncCleaningRuleSelection();
  }
);
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="上传知识文件"
    :show-icon="false"
    :mask-closable="false"
    class="paper-modal w-500px!"
    @positive-click="handleSubmit"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <NFormItem label="知识类型" path="knowledgeScope">
        <NRadioGroup v-model:value="model.knowledgeScope" name="knowledgeScope">
          <NSpace vertical :size="8">
            <NRadio v-for="option in knowledgeScopeOptions" :key="option.value" :value="option.value">
              <div class="scope-radio">
                <span>{{ option.label }}</span>
                <small>{{ option.description }}</small>
              </div>
            </NRadio>
          </NSpace>
        </NRadioGroup>
      </NFormItem>

      <NFormItem v-if="model.knowledgeScope === 'DEPARTMENT' && authStore.isSuperAdmin" label="所属部门" path="departmentId">
        <OrgTagCascader v-model:value="model.departmentId" @change="onUpdate" />
      </NFormItem>
      <NFormItem v-else-if="model.knowledgeScope === 'DEPARTMENT'" label="所属部门" path="departmentId">
        <TheSelect
          v-model:value="model.departmentId"
          url="/users/org-tags"
          key-field="orgTagDetails"
          label-field="name"
          value-field="tagId"
          @change="onUpdate"
        />
      </NFormItem>

      <NAlert v-if="model.knowledgeScope === 'PUBLIC'" type="success" :bordered="false" class="mb-4">
        公共知识上传后对所有用户可见。
      </NAlert>
      <NAlert v-else type="info" :bordered="false" class="mb-4">
        部门专有知识仅所属部门成员、部门负责人和超级管理员可见。
      </NAlert>

      <NFormItem label="知识分类" path="categoryId">
        <NSpace :wrap="false" class="w-full">
          <NSelect
            v-model:value="model.categoryId"
            :options="categoryOptions"
            :loading="categoryLoading"
            clearable
            placeholder="未分类"
            class="flex-1"
            @update:value="syncCategoryName"
          />
          <NButton ghost type="primary" @click="openCategoryCreate">新增</NButton>
        </NSpace>
      </NFormItem>

      <NFormItem label="清洗规则" path="cleaningRuleSetId">
        <NSelect
          v-model:value="model.cleaningRuleSetId"
          :options="cleaningRuleOptions"
          :loading="cleaningRuleSetLoading"
          clearable
          placeholder="默认清洗规则"
        />
      </NFormItem>

      <NFormItem label="上传文件" path="fileList">
        <NUpload
          v-model:file-list="model.fileList"
          :accept="uploadAccept"
          :max="1"
          :multiple="false"
          :default-upload="false"
          :on-before-upload="beforeUpload"
        >
          <NButton type="primary" ghost>选择文件</NButton>
        </NUpload>
        <template #feedback>
          <span class="upload-limit-tip">单个文件最大 {{ maxUploadFileSizeLabel }}</span>
        </template>
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" @click="handleSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>

  <NModal v-model:show="categoryCreateVisible" preset="dialog" title="新增知识分类" :show-icon="false" class="paper-modal w-420px!">
    <NForm label-placement="left" :label-width="80">
      <NFormItem label="分类名称">
        <NInput v-model:value="categoryCreateModel.name" placeholder="例如：制度规范、销售培训、产品资料" />
      </NFormItem>
      <NFormItem label="说明">
        <NInput v-model:value="categoryCreateModel.description" type="textarea" placeholder="可选" />
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace>
        <NButton @click="categoryCreateVisible = false">取消</NButton>
        <NButton type="primary" :loading="categoryCreateLoading" @click="handleCreateCategory">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.scope-radio {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.scope-radio small {
  color: rgb(var(--base-text-color) / 0.55);
}

.upload-limit-tip {
  color: rgb(var(--base-text-color) / 0.55);
  font-size: 12px;
}
</style>
