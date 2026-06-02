<script setup lang="ts">
import type { FormRules } from 'naive-ui';

defineOptions({
  name: 'OrgTagSettingDialog'
});

const props = defineProps<{
  rowData: Api.User.Item;
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });
const loading = ref(false);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

type Model = {
  role: Api.Auth.UserInfo['role'];
  orgTags: string[];
};

const model = ref<Model>(createDefaultModel());

function createDefaultModel(): Model {
  return {
    role: 'USER',
    orgTags: []
  };
}

const roleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: '部门成员', value: 'DEPT_MEMBER' },
  { label: '部门负责人', value: 'DEPT_LEAD' },
  { label: '知识管理员', value: 'KNOWLEDGE_ADMIN' },
  { label: '超级管理员', value: 'SUPER_ADMIN' },
  { label: '超级管理员(兼容旧ADMIN)', value: 'ADMIN' }
] satisfies Array<{ label: string; value: Api.Auth.UserInfo['role'] }>;

const rules = ref<FormRules>({
  role: defaultRequiredRule,
  orgTags: defaultRequiredRule
});

const privateOrgTag = ref<string[]>([]);
async function handleUpdateModelWhenEdit() {
  model.value = createDefaultModel();
  model.value.role = props.rowData.role || 'USER';
  model.value.orgTags = props.rowData.orgTags.map(tag => tag.tagId!);
  // 备份默认的私人空间标签，防止被误删
  privateOrgTag.value = props.rowData.orgTags.filter(tag => tag.tagId!.startsWith('PRIVATE_')).map(tag => tag.tagId!);
}

function close() {
  visible.value = false;
}

async function handleSubmit() {
  await validate();
  loading.value = true;
  model.value.orgTags = Array.from(new Set([...model.value.orgTags, ...privateOrgTag.value]));

  if (model.value.role !== props.rowData.role) {
    const roleRes = await request({
      method: 'PUT',
      url: `/admin/users/${props.rowData.userId}/role`,
      data: { role: model.value.role }
    });

    if (roleRes.error) {
      loading.value = false;
      return;
    }
  }

  const res = await request({
    method: 'PUT',
    url: `/admin/users/${props.rowData.userId}/org-tags`,
    data: { orgTags: model.value.orgTags }
  });
  if (!res.error) {
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
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="权限设置"
    :show-icon="false"
    :mask-closable="false"
    class="w-500px!"
    @positive-click="handleSubmit"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <NFormItem label="用户名" path="username">
        <NInput :value="rowData.username" readonly />
      </NFormItem>
      <NFormItem label="角色" path="role">
        <NSelect v-model:value="model.role" :options="roleOptions" />
      </NFormItem>
      <NFormItem label="所属部门" path="orgTags">
        <OrgTagCascader v-model:value="model.orgTags" multiple exclude-private />
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

<style scoped></style>
