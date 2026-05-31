<script setup lang="ts">
  /**
   * 管理员账号管理。
   *
   * 关键点：
   * - 创建/重置密码后返回的随机密码只展示一次，单独弹窗保留。
   * - PATCH /admin/users/{id} 仅更新 displayName / role / enabled，禁止改用户名。
   */
  import { onMounted, reactive, ref } from 'vue';
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
  import PageSection from '@/components/PageSection.vue';
  import { usersApi } from '@/api';
  import type { AdminUser, CreateAdminUserRequest } from '@/types/api';
  import { formatDateTime } from '@/utils/format';

  const loading = ref(false);
  const users = ref<AdminUser[]>([]);

  async function loadUsers() {
    loading.value = true;
    try {
      users.value = await usersApi.list();
    } finally {
      loading.value = false;
    }
  }

  /* ============= 新建 / 编辑 ============= */

  const formVisible = ref(false);
  const formMode = ref<'create' | 'edit'>('create');
  const formRef = ref<FormInstance>();
  const formSubmitting = ref(false);
  const editingId = ref<string | null>(null);

  const form = reactive({
    username: '',
    displayName: '',
    role: 'ADMIN',
    password: '',
    enabled: true,
  });

  const rules: FormRules = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  };

  function resetForm() {
    Object.assign(form, {
      username: '',
      displayName: '',
      role: 'ADMIN',
      password: '',
      enabled: true,
    });
  }

  function openCreate() {
    formMode.value = 'create';
    editingId.value = null;
    resetForm();
    formVisible.value = true;
  }

  function openEdit(row: AdminUser) {
    formMode.value = 'edit';
    editingId.value = row.userId;
    Object.assign(form, {
      username: row.username,
      displayName: row.displayName ?? '',
      role: row.role,
      password: '',
      enabled: row.enabled,
    });
    formVisible.value = true;
  }

  /* 一次性密码弹窗 */
  const credVisible = ref(false);
  const credInfo = ref<{ username: string; password: string } | null>(null);

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success('已复制');
    } catch {
      ElMessage.warning('请手动复制');
    }
  }

  async function submitForm() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;

    formSubmitting.value = true;
    try {
      if (formMode.value === 'create') {
        const payload: CreateAdminUserRequest = {
          username: form.username,
          displayName: form.displayName || undefined,
          role: form.role || undefined,
          password: form.password || undefined,
        };
        const result = await usersApi.create(payload);
        formVisible.value = false;
        // password 仅在后端自动生成时返回
        if (result.password) {
          credInfo.value = { username: result.user.username, password: result.password };
          credVisible.value = true;
        } else {
          ElMessage.success('已创建');
        }
      } else if (editingId.value) {
        await usersApi.update(editingId.value, {
          displayName: form.displayName || undefined,
          role: form.role || undefined,
          enabled: form.enabled,
        });
        ElMessage.success('已更新');
        formVisible.value = false;
      }
      await loadUsers();
    } finally {
      formSubmitting.value = false;
    }
  }

  async function toggleEnabled(row: AdminUser) {
    await usersApi.update(row.userId, { enabled: !row.enabled });
    ElMessage.success(row.enabled ? '已停用' : '已启用');
    await loadUsers();
  }

  async function resetPassword(row: AdminUser) {
    await ElMessageBox.confirm(
      `确认重置 ${row.username} 的密码？将生成新随机密码。`,
      '重置密码',
      { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' },
    ).catch(() => Promise.reject(new Error('cancel')));
    const result = await usersApi.resetPassword(row.userId);
    credInfo.value = { username: result.username, password: result.password };
    credVisible.value = true;
    await loadUsers();
  }

  onMounted(loadUsers);
</script>

<template>
  <PageSection title="管理员" subtitle="维护可登录控制台的管理账号">
    <template #actions>
      <el-button type="primary" @click="openCreate">新建管理员</el-button>
    </template>

    <el-table v-loading="loading" :data="users" stripe>
      <el-table-column prop="username" label="用户名" min-width="160" />
      <el-table-column prop="displayName" label="显示名" min-width="160">
        <template #default="{ row }">{{ row.displayName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="role" label="角色" width="120" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="light" round>
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="首次登录" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.firstLogin" type="warning" effect="light" round>需改密</el-tag>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="180">
        <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="toggleEnabled(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button link type="warning" @click="resetPassword(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>
  </PageSection>

  <el-dialog
    v-model="formVisible"
    :title="formMode === 'create' ? '新建管理员' : '编辑管理员'"
    width="480px"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :disabled="formMode === 'edit'" />
      </el-form-item>
      <el-form-item label="显示名">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item label="角色">
        <el-input v-model="form.role" />
      </el-form-item>
      <el-form-item v-if="formMode === 'create'" label="初始密码（留空则后端生成）">
        <el-input v-model="form.password" show-password />
      </el-form-item>
      <el-form-item v-if="formMode === 'edit'" label="启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="formSubmitting" @click="submitForm">提交</el-button>
    </template>
  </el-dialog>

  <!-- 一次性密码弹窗 -->
  <el-dialog v-model="credVisible" title="保存随机密码" width="480px" :close-on-click-modal="false">
    <el-alert type="warning" :closable="false" show-icon>
      此密码只会显示一次，请发给对应管理员后立即关闭窗口。
    </el-alert>
    <div v-if="credInfo" class="cred-block glass-panel mt-16">
      <div>
        <div class="text-muted">用户名</div>
        <code class="mono">{{ credInfo.username }}</code>
      </div>
      <div>
        <div class="text-muted">密码</div>
        <code class="mono">{{ credInfo.password }}</code>
      </div>
      <el-button size="small" type="primary" @click="copy(credInfo.password)">复制密码</el-button>
    </div>
    <template #footer>
      <el-button type="primary" @click="credVisible = false">已记录</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
  .cred-block {
    padding: 12px 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;

    code {
      display: inline-block;
      margin-top: 4px;
      word-break: break-all;
    }
  }
</style>
