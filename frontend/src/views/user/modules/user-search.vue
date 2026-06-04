<script setup lang="ts">
defineOptions({
  name: 'UserSearch'
});

const emit = defineEmits<{
  search: [];
}>();

const { formRef } = useNaiveForm();

const model = defineModel<Api.User.SearchParams>('model', { required: true });

const roleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: '部门成员', value: 'DEPT_MEMBER' },
  { label: '部门负责人', value: 'DEPT_LEAD' },
  { label: '知识管理员', value: 'KNOWLEDGE_ADMIN' },
  { label: '超级管理员', value: 'SUPER_ADMIN' },
  { label: '超级管理员(兼容旧ADMIN)', value: 'ADMIN' }
] satisfies Array<{ label: string; value: Api.Auth.UserInfo['role'] }>;

watchEffect(() => {
  search();
});
async function search() {
  emit('search');
}
</script>

<template>
  <NCard :bordered="false" size="small" class="paper-filter px-4">
    <NForm ref="formRef" :model="model" label-placement="left" :show-feedback="false" inline>
      <NFormItem label="关键词" path="keyword">
        <NInput v-model:value="model.keyword" placeholder="请输入关键词" clearable />
      </NFormItem>
      <NFormItem label="部门" path="orgTag">
        <OrgTagCascader v-model:value="model.orgTag" clearable class="w-200px!" />
      </NFormItem>
      <NFormItem label="角色" path="role">
        <NSelect
          v-model:value="model.role"
          placeholder="请选择角色"
          :options="roleOptions"
          clearable
          class="w-200px!"
        />
      </NFormItem>
    </NForm>
  </NCard>
</template>

<style scoped></style>
