<script setup lang="tsx">
import { NButton, NTag } from 'naive-ui';
import UserSearch from './modules/user-search.vue';
import OrgTagSettingDialog from './modules/org-tag-setting-dialog.vue';
import UserCreateDialog from './modules/user-create-dialog.vue';

const appStore = useAppStore();
const authStore = useAuthStore();

function apiFn(params: Api.User.SearchParams) {
  return request<Api.User.List>({ url: '/admin/users/list', params });
}

const roleMeta: Record<Api.Auth.UserInfo['role'], { label: string; type: 'default' | 'success' | 'warning' | 'error' | 'info' }> = {
  USER: { label: '普通用户', type: 'default' },
  DEPT_MEMBER: { label: '部门成员', type: 'info' },
  DEPT_LEAD: { label: '部门负责人', type: 'success' },
  KNOWLEDGE_ADMIN: { label: '知识管理员', type: 'warning' },
  SUPER_ADMIN: { label: '超级管理员', type: 'error' },
  ADMIN: { label: '超级管理员(兼容)', type: 'error' }
};

const { columns, columnChecks, data, getData, loading, mobilePagination, searchParams, resetSearchParams } = useTable({
  apiFn,
  apiParams: {
    keyword: null,
    orgTag: null,
    role: null,
    status: null
  },
  columns: () => [
    {
      key: 'index',
      title: '序号',
      width: 64
    },
    {
      key: 'username',
      title: '用户名',
      minWidth: 100
    },
    {
      key: 'role',
      title: '角色',
      width: 150,
      render: row => {
        const meta = roleMeta[row.role] || roleMeta.USER;
        return <NTag type={meta.type}>{meta.label}</NTag>;
      }
    },
    {
      key: 'orgTags',
      title: '所属部门',
      render: row => (
        <div class="flex flex-wrap gap-2">
          {row.orgTags.map(tag => (
            <NTag key={tag.tagId} type={tag.tagId === row.primaryOrg ? 'primary' : 'default'}>
              {tag.name}
            </NTag>
          ))}
        </div>
      )
    },
    {
      key: 'email',
      title: '邮箱',
      width: 200
    },
    {
      key: 'status',
      title: '是否启用',
      width: 100,
      render: row => <NTag type={row.status ? 'success' : 'warning'}>{row.status ? '已启用' : '已禁用'}</NTag>
    },
    {
      key: 'createTime',
      title: '创建时间',
      width: 200,
      render: row => (row.createTime || row.createdAt ? dayjs(row.createTime || row.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-')
    },
    {
      key: 'lastLoginTime',
      title: '最后登录时间',
      width: 200,
      render: row => dayjs(row.lastLoginTime).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      key: 'operate',
      title: '操作',
      width: 110,
      render: row =>
        authStore.isSuperAdmin ? (
          <NButton type="primary" ghost size="small" onClick={() => handlePermission(row)}>
            权限设置
          </NButton>
        ) : (
          <NTag bordered={false}>-</NTag>
        )
    }
  ]
});

const visible = ref(false);
const createVisible = ref(false);
const editingData = ref<Api.User.Item | null>(null);
function handlePermission(row: Api.User.Item) {
  editingData.value = row;
  visible.value = true;
}

// async function setPrimaryOrgTag(userId: string, primaryOrg: string) {
//   loading.value = true;
//   const { error } = await request({ url: 'users/primary-org', method: 'PUT', data: { primaryOrg, userId } });
//   if (!error) {
//     window.$message?.success('操作成功');
//     await getData();
//   }
//   loading.value = false;
// }
</script>

<template>
  <div class="paper-page min-h-500px flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <Teleport defer to="#header-extra">
      <UserSearch v-model:model="searchParams" @reset="resetSearchParams" @search="getData" />
    </Teleport>
    <NCard title="用户列表" :bordered="false" size="small" class="paper-card sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <TableHeaderOperation
          v-model:columns="columnChecks"
          :addable="authStore.isSuperAdmin || authStore.isDeptLead"
          :loading="loading"
          @add="createVisible = true"
          @refresh="getData"
        />
      </template>
      <NDataTable
        :columns="columns"
        :data="data"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="962"
        :loading="loading"
        remote
        :row-key="row => row.userId"
        :pagination="mobilePagination"
        class="sm:h-full"
      />
    </NCard>
    <OrgTagSettingDialog v-model:visible="visible" :row-data="editingData!" @submitted="getData" />
    <UserCreateDialog v-model:visible="createVisible" @submitted="getData" />
  </div>
</template>

<style scoped></style>
