<script setup lang="ts">
import { uploadAccept } from '@/constants/common';

defineOptions({
  name: 'UploadDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const authStore = useAuthStore();

const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

type KnowledgeScope = Api.KnowledgeBase.Form['knowledgeScope'];

const canUploadPublic = computed(() => authStore.isSuperAdmin || authStore.userInfo.role === 'KNOWLEDGE_ADMIN');
const canUploadDepartment = computed(() => authStore.isSuperAdmin || authStore.isDeptLead);

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
  return options;
});

const defaultKnowledgeScope = computed<KnowledgeScope>(() => knowledgeScopeOptions.value[0]?.value ?? 'DEPARTMENT');

const model = ref<Api.KnowledgeBase.Form>(createDefaultModel());

function createDefaultModel(): Api.KnowledgeBase.Form {
  const knowledgeScope = defaultKnowledgeScope.value;
  return {
    orgTag: null,
    orgTagName: '',
    knowledgeScope,
    departmentId: null,
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

watch(visible, () => {
  if (visible.value) {
    model.value = createDefaultModel();
    restoreValidation();
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
  }
);

watch(
  () => model.value.departmentId,
  value => {
    model.value.orgTag = value;
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

      <NFormItem label="上传文件" path="fileList">
        <NUpload
          v-model:file-list="model.fileList"
          :accept="uploadAccept"
          :max="1"
          :multiple="false"
          :default-upload="false"
        >
          <NButton type="primary" ghost>选择文件</NButton>
        </NUpload>
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" @click="handleSubmit">保存</NButton>
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
</style>
