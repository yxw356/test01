<script setup lang="ts">
import type { FormRules } from 'naive-ui';

const { userInfo } = storeToRefs(useAuthStore());
const { formRef: passwordFormRef, validate: validatePasswordForm, restoreValidation: restorePasswordValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const tags = ref<Api.OrgTag.Mine>({
  orgTags: [],
  primaryOrg: '',
  orgTagDetails: []
});

const loading = ref(false);
const getOrgTags = async () => {
  loading.value = true;
  const { error, data } = await request<Api.OrgTag.Mine>({
    url: '/users/org-tags'
  });
  if (!error) {
    tags.value = data;
  }
  loading.value = false;
};

onMounted(() => {
  getOrgTags();
});

const visible = ref(false);
const currentTagId = ref('');
const showModal = (tagId: string) => {
  if (tagId === tags.value.primaryOrg) return;
  visible.value = true;
  currentTagId.value = tagId;
};
const submitLoading = ref(false);
const setPrimaryOrg = async () => {
  submitLoading.value = true;
  const { error } = await request({
    url: '/users/primary-org',
    method: 'PUT',
    data: { primaryOrg: currentTagId.value, userId: userInfo.value.id }
  });
  if (!error) {
    visible.value = false;
    getOrgTags();
  }
  submitLoading.value = false;
};

const passwordVisible = ref(false);
const passwordLoading = ref(false);
const passwordModel = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const passwordRules: FormRules = {
  currentPassword: defaultRequiredRule,
  newPassword: [
    defaultRequiredRule,
    {
      validator: (_rule, value: string) => !value || value.length >= 6,
      message: '新密码至少 6 位',
      trigger: 'input'
    }
  ],
  confirmPassword: [
    defaultRequiredRule,
    {
      validator: (_rule, value: string) => value === passwordModel.newPassword,
      message: '两次输入的密码不一致',
      trigger: 'input'
    }
  ]
};

function openPasswordDialog() {
  passwordModel.currentPassword = '';
  passwordModel.newPassword = '';
  passwordModel.confirmPassword = '';
  restorePasswordValidation();
  passwordVisible.value = true;
}

async function changePassword() {
  await validatePasswordForm();
  passwordLoading.value = true;
  const { error } = await request({
    url: '/users/password',
    method: 'PUT',
    data: {
      currentPassword: passwordModel.currentPassword,
      newPassword: passwordModel.newPassword
    }
  });
  if (!error) {
    window.$message?.success('密码修改成功');
    passwordVisible.value = false;
  }
  passwordLoading.value = false;
}
</script>

<template>
  <NSpin :show="loading">
    <div class="paper-page flex-cc">
      <NCard
        class="paper-card min-h-400px min-w-600px w-50vw card-wrapper"
        :segmented="{ content: true, footer: 'soft' }"
      >
        <template #header>
          <div class="flex items-center gap-4">
            <NAvatar size="large">
              <icon-solar:user-circle-linear class="text-icon-large" />
            </NAvatar>
            <div>{{ userInfo.username }}</div>
            <NTag size="small" type="primary" :bordered="false">{{ userInfo.role }}</NTag>
          </div>
        </template>
        <template #header-extra>
          <NButton type="primary" ghost @click="openPasswordDialog">修改密码</NButton>
        </template>
        <NScrollbar class="max-h-60vh">
          <div class="flex flex-wrap gap-4 p-4">
            <NCard
              v-for="tag in tags.orgTagDetails"
              :key="tag.tagId"
              size="small"
              :bordered="false"
              class="paper-card w-[calc((100%-32px)/3)] cursor-pointer"
              :segmented="{ content: true, footer: 'soft' }"
              @click="showModal(tag.tagId)"
            >
              <div class="flex items-center justify-between">
                <div>{{ tag.name }}</div>
                <NTag v-if="tag.tagId === tags.primaryOrg" type="primary" size="small">
                  主部门
                  <template #icon>
                    <icon-solar:verified-check-bold-duotone class="text-icon" />
                  </template>
                </NTag>
              </div>
              <template #footer>
                <NEllipsis :line-clamp="3">{{ tag.description }}</NEllipsis>
              </template>
            </NCard>
          </div>
        </NScrollbar>
      </NCard>

      <NModal
        v-model:show="visible"
        :loading="submitLoading"
        preset="dialog"
        class="paper-modal"
        title="设置主部门"
        content="确定将当前部门设置为主部门吗？"
        positive-text="确认"
        negative-text="取消"
        @positive-click="setPrimaryOrg"
        @negative-click="visible = false"
      />

      <NModal
        v-model:show="passwordVisible"
        preset="dialog"
        title="修改密码"
        :show-icon="false"
        class="paper-modal w-460px!"
      >
        <NForm
          ref="passwordFormRef"
          :model="passwordModel"
          :rules="passwordRules"
          label-placement="left"
          :label-width="90"
          class="mt-10"
        >
          <NFormItem label="当前密码" path="currentPassword">
            <NInput v-model:value="passwordModel.currentPassword" type="password" show-password-on="click" />
          </NFormItem>
          <NFormItem label="新密码" path="newPassword">
            <NInput v-model:value="passwordModel.newPassword" type="password" show-password-on="click" />
          </NFormItem>
          <NFormItem label="确认密码" path="confirmPassword">
            <NInput v-model:value="passwordModel.confirmPassword" type="password" show-password-on="click" />
          </NFormItem>
        </NForm>
        <template #action>
          <NSpace :size="16">
            <NButton @click="passwordVisible = false">取消</NButton>
            <NButton type="primary" :loading="passwordLoading" @click="changePassword">保存</NButton>
          </NSpace>
        </template>
      </NModal>
    </div>
  </NSpin>
</template>

<style scoped lang="scss">
:deep(.n-card__content) {
  flex: none m !important;
  height: fit-content;
}
</style>
