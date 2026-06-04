<script setup lang="ts">
import type { FormRules } from 'naive-ui';

defineOptions({
  name: 'UserCreateDialog'
});

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });
const authStore = useAuthStore();
const loading = ref(false);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const model = reactive({
  username: '',
  password: '',
  role: 'DEPT_MEMBER' as Api.Auth.UserInfo['role'],
  orgTags: [] as string[],
  primaryOrg: ''
});

const roleOptions = computed(() => {
  if (authStore.isDeptLead && !authStore.isSuperAdmin) {
    return [{ label: '部门成员', value: 'DEPT_MEMBER' as const }];
  }
  return [
    { label: '普通用户', value: 'USER' as const },
    { label: '部门成员', value: 'DEPT_MEMBER' as const },
    { label: '部门负责人', value: 'DEPT_LEAD' as const },
    { label: '知识管理员', value: 'KNOWLEDGE_ADMIN' as const },
    { label: '超级管理员', value: 'SUPER_ADMIN' as const }
  ];
});

const rules: FormRules = {
  username: defaultRequiredRule,
  password: [
    defaultRequiredRule,
    {
      validator: (_rule, value: string) => !value || value.length >= 6,
      message: '密码至少 6 位',
      trigger: 'input'
    }
  ],
  role: defaultRequiredRule,
  orgTags: defaultRequiredRule
};

function reset() {
  model.username = '';
  model.password = '';
  model.role = authStore.isDeptLead && !authStore.isSuperAdmin ? 'DEPT_MEMBER' : 'USER';
  model.orgTags = authStore.isDeptLead && authStore.userInfo.primaryOrg ? [authStore.userInfo.primaryOrg] : [];
  model.primaryOrg = model.orgTags[0] || '';
  restoreValidation();
}

function close() {
  visible.value = false;
}

async function submit() {
  await validate();
  loading.value = true;
  const orgTags = Array.from(new Set(model.orgTags));
  const { error } = await request({
    url: '/admin/users',
    method: 'POST',
    data: {
      username: model.username,
      password: model.password,
      role: model.role,
      orgTags,
      primaryOrg: model.primaryOrg || orgTags[0]
    }
  });
  if (!error) {
    window.$message?.success('账号创建成功');
    close();
    emit('submitted');
  }
  loading.value = false;
}

watch(visible, () => {
  if (visible.value) {
    reset();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="新增账号"
    :show-icon="false"
    :mask-closable="false"
    class="w-520px!"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="92" class="mt-10">
      <NFormItem label="用户名" path="username">
        <NInput v-model:value="model.username" placeholder="请输入用户名" />
      </NFormItem>
      <NFormItem label="初始密码" path="password">
        <NInput v-model:value="model.password" type="password" show-password-on="click" placeholder="至少 6 位" />
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
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" :loading="loading" @click="submit">创建</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
