<script setup lang="ts">
import type { FormRules } from 'naive-ui';

defineOptions({
  name: 'OrgTagSettingDialog'
});

const props = defineProps<{
  rowData: Api.User.Item;
}>();

const emit = defineEmits<{ submitted: [] }>();

const authStore = useAuthStore();
const visible = defineModel<boolean>('visible', { default: false });
const loading = ref(false);
const permissionLoading = ref(false);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

type Model = {
  username: string;
  role: Api.Auth.UserInfo['role'];
  orgTags: string[];
  primaryOrg: string;
};

const model = ref<Model>(createDefaultModel());

function createDefaultModel(): Model {
  return {
    username: '',
    role: 'USER',
    orgTags: [],
    primaryOrg: ''
  };
}

const allRoleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: '部门成员', value: 'DEPT_MEMBER' },
  { label: '部门负责人', value: 'DEPT_LEAD' },
  { label: '知识管理员', value: 'KNOWLEDGE_ADMIN' },
  { label: '超级管理员', value: 'SUPER_ADMIN' },
  { label: '超级管理员(兼容旧ADMIN)', value: 'ADMIN' }
] satisfies Array<{ label: string; value: Api.Auth.UserInfo['role'] }>;

const roleOptions = computed(() => {
  if (authStore.isDeptLead && !authStore.isSuperAdmin) {
    return allRoleOptions.filter(item => item.value === 'DEPT_MEMBER');
  }
  return allRoleOptions;
});

const filePermissionOptions = [
  { label: '查看列表', value: 'VIEW', description: '允许在可见范围内看到文件记录' },
  { label: '预览文件', value: 'PREVIEW', description: '允许打开文件预览' },
  { label: '下载文件', value: 'DOWNLOAD', description: '允许获取文件下载链接' },
  { label: '上传公共知识', value: 'UPLOAD_PUBLIC', description: '允许上传全员可见知识' },
  { label: '上传部门知识', value: 'UPLOAD_DEPARTMENT', description: '允许上传部门专有知识' },
  { label: '上传个人知识', value: 'UPLOAD_PRIVATE', description: '允许上传到个人知识库' },
  { label: '删除文件', value: 'DELETE', description: '允许删除有管理权的文件' },
  { label: '重新清洗', value: 'RECLEAN', description: '允许重新清洗并生成索引' },
  { label: '重建索引', value: 'REINDEX', description: '允许重新提交索引任务' },
  { label: '续传文件', value: 'RESUME_UPLOAD', description: '允许续传本人中断的上传' }
] satisfies Array<{ label: string; value: Api.User.FilePermissionAction; description: string }>;

const filePermissionValues = ref<Api.User.FilePermissionAction[]>([]);

const rules = ref<FormRules>({
  username: defaultRequiredRule,
  role: defaultRequiredRule,
  orgTags: defaultRequiredRule
});

async function handleUpdateModelWhenEdit() {
  model.value = createDefaultModel();
  model.value.username = props.rowData.username;
  model.value.role = props.rowData.role || 'USER';
  model.value.orgTags = props.rowData.orgTags.map(tag => tag.tagId!);
  model.value.primaryOrg = props.rowData.primaryOrg || model.value.orgTags[0] || '';
  await loadRoleFilePermissions(model.value.role);
}

function close() {
  visible.value = false;
}

async function loadRoleFilePermissions(role: Api.Auth.UserInfo['role']) {
  if (!authStore.isSuperAdmin) return;
  permissionLoading.value = true;
  const { error, data } = await request<Api.User.RoleFilePermission[]>({
    url: `/admin/role-file-permissions/${role}`
  });
  permissionLoading.value = false;
  if (!error) {
    filePermissionValues.value = data.filter(item => item.allowed).map(item => item.action);
  }
}

async function saveRoleFilePermissions() {
  if (!authStore.isSuperAdmin) return true;
  const selected = new Set(filePermissionValues.value);
  const { error } = await request({
    method: 'PUT',
    url: `/admin/role-file-permissions/${model.value.role}`,
    data: {
      permissions: filePermissionOptions.map(item => ({
        action: item.value,
        allowed: selected.has(item.value)
      }))
    }
  });
  return !error;
}

async function handleSubmit() {
  await validate();
  loading.value = true;
  const res = await request({
    method: 'PUT',
    url: `/admin/users/${props.rowData.userId}`,
    data: {
      username: model.value.username.trim(),
      role: model.value.role,
      orgTags: model.value.orgTags,
      primaryOrg: model.value.primaryOrg || model.value.orgTags[0]
    }
  });
  if (!res.error && (await saveRoleFilePermissions())) {
    window.$message?.success('操作成功');
    close();
    emit('submitted');
  }
  loading.value = false;
}

watch(visible, () => {
  if (visible.value) {
    handleUpdateModelWhenEdit();
    restoreValidation();
  }
});

watch(
  () => model.value.role,
  role => {
    if (visible.value) loadRoleFilePermissions(role);
  }
);
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="编辑账号"
    :show-icon="false"
    :mask-closable="false"
    class="w-500px!"
    @positive-click="handleSubmit"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <NFormItem label="用户名" path="username">
        <NInput v-model:value="model.username" placeholder="请输入用户名" />
      </NFormItem>
      <NFormItem label="角色" path="role">
        <NSelect v-model:value="model.role" :options="roleOptions" :disabled="authStore.isDeptLead && !authStore.isSuperAdmin" />
      </NFormItem>
      <NFormItem label="所属部门" path="orgTags">
        <OrgTagCascader v-model:value="model.orgTags" multiple exclude-private />
      </NFormItem>
      <NFormItem label="主部门" path="primaryOrg">
        <NSelect
          v-model:value="model.primaryOrg"
          :options="model.orgTags.map(tag => ({ label: tag, value: tag }))"
          placeholder="默认取第一个所属部门"
          clearable
        />
      </NFormItem>
      <NFormItem v-if="authStore.isSuperAdmin" label="文件权限">
        <NSpin :show="permissionLoading" class="w-full">
          <div class="permission-grid">
            <NCheckbox
              v-for="item in filePermissionOptions"
              :key="item.value"
              :checked="filePermissionValues.includes(item.value)"
              @update:checked="
                checked => {
                  filePermissionValues = checked
                    ? Array.from(new Set([...filePermissionValues, item.value]))
                    : filePermissionValues.filter(value => value !== item.value);
                }
              "
            >
              <div class="permission-option">
                <strong>{{ item.label }}</strong>
                <span>{{ item.description }}</span>
              </div>
            </NCheckbox>
          </div>
        </NSpin>
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
.permission-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
}

.permission-option {
  display: grid;
  gap: 2px;
}

.permission-option span {
  color: rgb(var(--base-text-color) / 0.56);
  font-size: 12px;
  line-height: 1.4;
}
</style>
