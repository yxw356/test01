<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { fetchChangePassword } from '@/service/api';
import { useAuthStore } from '@/store/modules/auth';
import { useFormRules, useNaiveForm } from '@/hooks/common/form';

const authStore = useAuthStore();
const { formRef, validate } = useNaiveForm();
const loading = ref(false);

interface FormModel {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const model = reactive<FormModel>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const rules = computed(() => {
  const { formRules, createConfirmPwdRule } = useFormRules();
  return {
    oldPassword: formRules.pwd,
    newPassword: formRules.pwd,
    confirmPassword: createConfirmPwdRule(model.newPassword)
  };
});

async function handleSubmit() {
  await validate();
  loading.value = true;
  const { error } = await fetchChangePassword(model.oldPassword, model.newPassword);
  loading.value = false;
  if (!error) {
    window.$message?.success('密码已修改，请使用新密码重新登录');
    await authStore.resetStore();
  }
}

function resetForm() {
  model.oldPassword = '';
  model.newPassword = '';
  model.confirmPassword = '';
}
</script>

<template>
  <NCard class="paper-card min-w-600px w-50vw card-wrapper" title="修改密码" :segmented="{ content: true }">
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" label-width="100">
      <NFormItem label="当前密码" path="oldPassword">
        <NInput
          v-model:value="model.oldPassword"
          type="password"
          show-password-on="click"
          autocomplete="current-password"
        />
      </NFormItem>
      <NFormItem label="新密码" path="newPassword">
        <NInput
          v-model:value="model.newPassword"
          type="password"
          show-password-on="click"
          autocomplete="new-password"
          placeholder="6-18 位字母或数字"
        />
      </NFormItem>
      <NFormItem label="确认新密码" path="confirmPassword">
        <NInput
          v-model:value="model.confirmPassword"
          type="password"
          show-password-on="click"
          autocomplete="new-password"
        />
      </NFormItem>
      <div class="flex justify-end gap-3">
        <NButton @click="resetForm">清空</NButton>
        <NButton type="primary" :loading="loading" @click="handleSubmit">保存</NButton>
      </div>
    </NForm>
  </NCard>
</template>
