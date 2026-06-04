<script setup lang="tsx">
import { NButton, NEllipsis, NPopconfirm, NTag } from 'naive-ui';
import OrgTagOperateDialog from './modules/org-tag-operate-dialog.vue';

const appStore = useAppStore();
const deptLeadUsers = ref<Api.User.Item[]>([]);
const allUsers = ref<Api.User.Item[]>([]);
const leaderVisible = ref(false);
const leaderLoading = ref(false);
const currentDept = ref<Api.OrgTag.Item | null>(null);
const leaderUserIds = ref<string[]>([]);

const userOptions = computed(() =>
  allUsers.value.map(user => ({
    label: `${user.username}（${getRoleLabel(user.role)}）`,
    value: String(user.userId)
  }))
);

function getRoleLabel(role: Api.Auth.UserInfo['role']) {
  const roleMap: Record<Api.Auth.UserInfo['role'], string> = {
    USER: '普通用户',
    DEPT_MEMBER: '部门成员',
    DEPT_LEAD: '部门负责人',
    KNOWLEDGE_ADMIN: '知识管理员',
    SUPER_ADMIN: '超级管理员',
    ADMIN: '超级管理员'
  };
  return roleMap[role] || role;
}

async function loadAllUsers() {
  const { error, data } = await request<Api.User.List>({
    url: '/admin/users/list',
    params: {
      page: 1,
      size: 999
    }
  });

  if (!error) {
    allUsers.value = data.content;
  }
}

async function loadDeptLeads() {
  const { error, data } = await request<Api.User.List>({
    url: '/admin/users/list',
    params: {
      page: 1,
      size: 999,
      role: 'DEPT_LEAD'
    }
  });

  if (!error) {
    deptLeadUsers.value = data.content;
  }
}

const deptLeadMap = computed(() => {
  const map = new Map<string, Api.User.Item[]>();
  deptLeadUsers.value.forEach(user => {
    user.orgTags.forEach(tag => {
      if (!tag.tagId) return;
      const users = map.get(tag.tagId) || [];
      users.push(user);
      map.set(tag.tagId, users);
    });
  });
  return map;
});

async function refreshAll() {
  await Promise.all([getData(), loadDeptLeads(), loadAllUsers()]);
}

const { columns, columnChecks, data, loading, getData } = useTable({
  apiFn: fetchGetOrgTagList,
  columns: () => [
    {
      key: 'name',
      title: '部门名称',
      width: 300,
      ellipsis: {
        tooltip: true
      }
    },
    {
      key: 'deptLeads',
      title: '部门负责人',
      width: 220,
      render: row => {
        const users = deptLeadMap.value.get(row.tagId!) || [];
        if (users.length === 0) return <NTag bordered={false}>未设置</NTag>;
        return (
          <div class="flex flex-wrap gap-2">
            {users.map(user => (
              <NTag key={user.userId} type="success" size="small">
                <NEllipsis style={{ maxWidth: '96px' }}>{user.username}</NEllipsis>
              </NTag>
            ))}
          </div>
        );
      }
    },
    {
      key: 'description',
      title: '描述',
      minWidth: 200,
      ellipsis: {
        tooltip: true
      }
    },
    {
      key: 'operate',
      title: '操作',
      width: 240,
      render: row => (
        <div class="flex gap-2">
          <NButton type="success" ghost size="small" onClick={() => addChild(row)}>
            新增下级部门
          </NButton>
          <NButton type="warning" ghost size="small" onClick={() => openLeaderDialog(row)}>
            设置负责人
          </NButton>
          <NButton type="primary" ghost size="small" onClick={() => edit(row)}>
            编辑
          </NButton>
          <NPopconfirm onPositiveClick={() => handleDelete(row.tagId!)}>
            {{
              default: () => '确认删除当前部门吗？',
              trigger: () => (
                <NButton type="error" ghost size="small">
                  删除
                </NButton>
              )
            }}
          </NPopconfirm>
        </div>
      )
    }
  ]
});

const {
  dialogVisible,
  operateType,
  editingData,
  handleAdd,
  handleAddChild,
  handleEdit,
  onDeleted
  // closeDrawer
} = useTableOperate<Api.OrgTag.Item>(getData);

function addChild(row: Api.OrgTag.Item) {
  handleAddChild(row);
}

/** the editing row data */
function edit(row: Api.OrgTag.Item) {
  handleEdit(row);
}

async function openLeaderDialog(row: Api.OrgTag.Item) {
  currentDept.value = row;
  if (allUsers.value.length === 0) {
    await loadAllUsers();
  }
  const currentLeads = deptLeadMap.value.get(row.tagId!) || [];
  leaderUserIds.value = currentLeads.map(user => String(user.userId));
  leaderVisible.value = true;
}

async function handleDelete(tagId: string) {
  const { error } = await request({ url: `/admin/org-tags/${tagId}`, method: 'DELETE' });
  if (!error) {
    onDeleted();
  }
}

async function saveDeptLeaders() {
  if (!currentDept.value?.tagId) return;

  leaderLoading.value = true;
  const deptId = currentDept.value.tagId;
  const selectedUserIdSet = new Set(leaderUserIds.value);
  const currentLeadUsers = deptLeadMap.value.get(deptId) || [];
  const selectedUsers = allUsers.value.filter(user => leaderUserIds.value.includes(String(user.userId)));
  const removedUsers = currentLeadUsers.filter(user => !selectedUserIdSet.has(String(user.userId)));

  try {
    for (const user of removedUsers) {
      const nextOrgTags = user.orgTags.map(tag => tag.tagId!).filter(tagId => Boolean(tagId) && tagId !== deptId);
      const orgTagRes = await request({
        method: 'PUT',
        url: `/admin/users/${user.userId}/org-tags`,
        data: { orgTags: nextOrgTags }
      });
      if (orgTagRes.error) return;

      const stillLeadOtherDept = deptLeadUsers.value.some(
        leadUser =>
          String(leadUser.userId) === String(user.userId) &&
          leadUser.orgTags.some(tag => tag.tagId && tag.tagId !== deptId)
      );

      if (!stillLeadOtherDept) {
        const roleRes = await request({
          method: 'PUT',
          url: `/admin/users/${user.userId}/role`,
          data: { role: 'DEPT_MEMBER' }
        });
        if (roleRes.error) return;
      }
    }

    for (const user of selectedUsers) {
      if (user.role !== 'DEPT_LEAD') {
        const roleRes = await request({
          method: 'PUT',
          url: `/admin/users/${user.userId}/role`,
          data: { role: 'DEPT_LEAD' }
        });
        if (roleRes.error) return;
      }

      const currentOrgTags = user.orgTags.map(tag => tag.tagId!).filter(Boolean);
      if (!currentOrgTags.includes(deptId)) {
        const orgTagRes = await request({
          method: 'PUT',
          url: `/admin/users/${user.userId}/org-tags`,
          data: { orgTags: Array.from(new Set([...currentOrgTags, deptId])) }
        });
        if (orgTagRes.error) return;
      }
    }

    window.$message?.success('部门负责人已更新');
    leaderVisible.value = false;
    await refreshAll();
  } finally {
    leaderLoading.value = false;
  }
}

onMounted(() => {
  refreshAll();
});
</script>

<template>
  <div class="paper-page flex-col-stretch gap-16px overflow-hidden <sm:overflow-auto">
    <NCard title="部门管理" :bordered="false" size="small" class="paper-card sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <TableHeaderOperation v-model:columns="columnChecks" :loading="loading" @add="handleAdd" @refresh="refreshAll" />
      </template>
      <NDataTable
        remote
        :columns="columns"
        :data="data"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="962"
        :loading="loading"
        :pagination="false"
        :row-key="item => item.tagId"
        class="sm:h-full"
      />
      <OrgTagOperateDialog
        v-model:visible="dialogVisible"
        :operate-type="operateType"
        :row-data="editingData!"
        :data="data"
        @submitted="refreshAll"
      />
      <NModal
        v-model:show="leaderVisible"
        preset="dialog"
        title="设置部门负责人"
        :show-icon="false"
        :mask-closable="false"
        class="w-520px!"
      >
        <NSpace vertical :size="14">
          <NAlert type="info" :bordered="false">
            保存后将以当前选择结果为准：新增的用户会设为“部门负责人”并加入当前部门，取消选择的用户会从当前部门负责人中移除。
          </NAlert>
          <NForm label-placement="left" :label-width="90" :show-feedback="false">
            <NFormItem label="当前部门">
              <NInput :value="currentDept?.name || '-'" readonly />
            </NFormItem>
            <NFormItem label="负责人">
              <NSelect
                v-model:value="leaderUserIds"
                multiple
                filterable
                :options="userOptions"
                placeholder="请选择部门负责人"
              />
            </NFormItem>
          </NForm>
        </NSpace>
        <template #action>
          <NSpace :size="16">
            <NButton @click="leaderVisible = false">取消</NButton>
            <NButton type="primary" :loading="leaderLoading" @click="saveDeptLeaders">保存</NButton>
          </NSpace>
        </template>
      </NModal>
    </NCard>
  </div>
</template>

<style scoped></style>
