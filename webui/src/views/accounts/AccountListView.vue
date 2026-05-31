<script setup lang="ts">
  /**
   * Kiro 账号管理。
   *
   * 覆盖 /admin/accounts 全部能力：
   * - 列表 / 新增 / 编辑 / 删除
   * - 启用/停用、挂起/恢复（reset）
   * - 批量 JSON 导入（支持 upsert）
   *
   * 表单与对话框由本组件直接管理，避免拆分过细造成的目录爆炸。
   * 后续表单复杂化时可再抽 AccountForm.vue。
   */
  import { computed, onMounted, reactive, ref } from 'vue';
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
  import PageSection from '@/components/PageSection.vue';
  import { accountsApi } from '@/api';
  import { ApiError } from '@/utils/http';
  import type {
    Account,
    CreateAccountRequest,
    ImportAccountsResult,
    TestAccountItem,
    TestAccountsResult,
  } from '@/types/api';
  import { formatDateTime, formatNumber, shorten } from '@/utils/format';

  /* ============= 列表 ============= */

  const loading = ref(false);
  const accounts = ref<Account[]>([]);
  const keyword = ref('');

  const visibleAccounts = computed(() => {
    const q = keyword.value.trim().toLowerCase();
    if (!q) return accounts.value;
    return accounts.value.filter(
      (a) =>
        a.accountId.toLowerCase().includes(q) ||
        (a.email ?? '').toLowerCase().includes(q) ||
        (a.region ?? '').toLowerCase().includes(q),
    );
  });

  function statusOf(account: Account) {
    if (!account.enabled) return { label: '已停用', tone: 'info' as const };
    if (account.suspendedAt) return { label: '挂起', tone: 'danger' as const };
    if (account.quotaExhaustedAt) return { label: '配额用尽', tone: 'warning' as const };
    return { label: '可用', tone: 'success' as const };
  }

  async function loadAccounts() {
    loading.value = true;
    try {
      accounts.value = await accountsApi.list();
    } finally {
      loading.value = false;
    }
  }

  /* ============= 新建 / 编辑 ============= */

  type FormMode = 'create' | 'edit';

  const formVisible = ref(false);
  const formMode = ref<FormMode>('create');
  const formRef = ref<FormInstance>();
  const formSubmitting = ref(false);
  const formAccountId = ref<string | null>(null);

  // 与后端 DTO 字段一致；可选字段使用空字符串占位，提交时再清洗
  const form = reactive({
    accountId: '',
    email: '',
    accessToken: '',
    refreshToken: '',
    clientId: '',
    clientSecret: '',
    region: 'us-east-1',
    authMethod: 'idc',
    provider: 'BuilderID',
    profileArn: '',
    machineId: '',
    proxyUrl: '',
    quotaLimit: undefined as number | undefined,
    enabled: true,
  });

  const rules: FormRules = {
    accessToken: [{ required: true, message: 'accessToken 必填', trigger: 'blur' }],
  };

  function resetForm() {
    Object.assign(form, {
      accountId: '',
      email: '',
      accessToken: '',
      refreshToken: '',
      clientId: '',
      clientSecret: '',
      region: 'us-east-1',
      authMethod: 'idc',
      provider: 'BuilderID',
      profileArn: '',
      machineId: '',
      proxyUrl: '',
      quotaLimit: undefined,
      enabled: true,
    });
  }

  function openCreate() {
    formMode.value = 'create';
    formAccountId.value = null;
    resetForm();
    formVisible.value = true;
  }

  function openEdit(row: Account) {
    formMode.value = 'edit';
    formAccountId.value = row.accountId;
    resetForm();
    Object.assign(form, {
      accountId: row.accountId,
      email: row.email ?? '',
      region: row.region ?? '',
      authMethod: row.authMethod ?? '',
      provider: row.provider ?? '',
      profileArn: row.profileArn ?? '',
      machineId: row.machineId ?? '',
      proxyUrl: row.proxyUrl ?? '',
      quotaLimit: row.quotaLimit ?? undefined,
      enabled: row.enabled,
    });
    formVisible.value = true;
  }

  /** 把空串字段从 payload 中剔除，避免覆盖后端为空 */
  function compact<T extends Record<string, unknown>>(obj: T): Partial<T> {
    return Object.fromEntries(
      Object.entries(obj).filter(([, v]) => v !== '' && v !== undefined && v !== null),
    ) as Partial<T>;
  }

  async function submitForm() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;

    formSubmitting.value = true;
    try {
      if (formMode.value === 'create') {
        const payload = compact({ ...form }) as CreateAccountRequest;
        await accountsApi.create(payload);
        ElMessage.success('账号已创建');
      } else if (formAccountId.value) {
        const { accessToken, refreshToken, clientId, clientSecret, ...rest } = form;
        // 编辑模式下 access/refresh token 仅在有值时提交
        const payload = compact({
          ...rest,
          accessToken: accessToken || undefined,
          refreshToken: refreshToken || undefined,
          clientId: clientId || undefined,
          clientSecret: clientSecret || undefined,
        });
        await accountsApi.update(formAccountId.value, payload);
        ElMessage.success('账号已更新');
      }
      formVisible.value = false;
      await loadAccounts();
    } finally {
      formSubmitting.value = false;
    }
  }

  /* ============= 启停 / 挂起 / 重置 / 删除 ============= */

  async function toggleEnabled(row: Account) {
    await accountsApi.update(row.accountId, { enabled: !row.enabled });
    ElMessage.success(row.enabled ? '已停用' : '已启用');
    await loadAccounts();
  }

  async function suspend(row: Account) {
    const result = await ElMessageBox.prompt('请输入挂起原因（如 RATE_LIMITED）', '挂起账号', {
      confirmButtonText: '挂起',
      cancelButtonText: '取消',
      inputPlaceholder: 'TEMPORARILY_SUSPENDED',
    }).catch(() => null);
    if (!result) return;
    await accountsApi.suspend(row.accountId, {
      reason: result.value || 'TEMPORARILY_SUSPENDED',
      message: '由控制台手动挂起',
    });
    ElMessage.success('已挂起');
    await loadAccounts();
  }

  async function resetAccount(row: Account) {
    await ElMessageBox.confirm(`确认重置账号 ${row.accountId} 状态？`, '确认操作', {
      confirmButtonText: '重置',
      cancelButtonText: '取消',
      type: 'warning',
    }).catch(() => Promise.reject(new Error('cancel')));
    await accountsApi.reset(row.accountId);
    ElMessage.success('已重置');
    await loadAccounts();
  }

  async function removeAccount(row: Account) {
    await ElMessageBox.confirm(
      `确认删除账号 ${row.accountId}？操作不可恢复。`,
      '危险操作',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' },
    ).catch(() => Promise.reject(new Error('cancel')));
    await accountsApi.remove(row.accountId);
    ElMessage.success('已删除');
    await loadAccounts();
  }

  /* ============= 账号连通测试 ============= */

  const testingAccountId = ref<string | null>(null);
  const testDetailVisible = ref(false);
  const testDetail = ref<TestAccountItem | null>(null);
  const latestTestResults = ref<Record<string, TestAccountItem>>({});
  const testOptions = reactive({
    model: 'auto',
    prompt: 'Reply OK in one short sentence.',
    onlyEnabled: true,
  });

  const testDetailJson = computed(() =>
    testDetail.value ? JSON.stringify(testDetail.value, null, 2) : '',
  );

  function testStatusType(status: TestAccountItem['status']) {
    if (status === 'SUCCESS') return 'success';
    if (status === 'SKIPPED') return 'info';
    return 'danger';
  }

  function latestTest(accountId: string) {
    return latestTestResults.value[accountId];
  }

  function openTestDetail(row: Account) {
    const item = latestTest(row.accountId);
    if (!item) {
      ElMessage.warning('该账号还没有测试结果');
      return;
    }
    testDetail.value = item;
    testDetailVisible.value = true;
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success('已复制');
    } catch {
      ElMessage.warning('请手动复制');
    }
  }

  async function testAccount(row: Account) {
    testingAccountId.value = row.accountId;
    try {
      const result: TestAccountsResult = await accountsApi.test({
        accountIds: [row.accountId],
        onlyEnabled: testOptions.onlyEnabled,
        model: testOptions.model,
        prompt: testOptions.prompt,
        maxConcurrency: 1,
      });
      const item = result.items[0];
      if (item) {
        latestTestResults.value = { ...latestTestResults.value, [row.accountId]: item };
        testDetail.value = item;
        testDetailVisible.value = true;
      }
      ElMessage.success(`测试完成：${item?.status ?? 'UNKNOWN'}`);
      await loadAccounts();
    } catch (err) {
      if (err instanceof ApiError) ElMessage.error(err.message);
    } finally {
      testingAccountId.value = null;
    }
  }

  /* ============= 批量导入 ============= */

  const importVisible = ref(false);
  const importContent = ref('');
  const importUpsert = ref(true);
  const importResult = ref<ImportAccountsResult | null>(null);
  const importSubmitting = ref(false);

  function openImport() {
    importContent.value = '';
    importResult.value = null;
    importUpsert.value = true;
    importVisible.value = true;
  }

  async function submitImport() {
    let parsed: unknown;
    try {
      parsed = JSON.parse(importContent.value);
    } catch {
      ElMessage.error('JSON 格式错误');
      return;
    }
    const payload =
      Array.isArray(parsed)
        ? { accounts: parsed, upsert: importUpsert.value }
        : parsed && typeof parsed === 'object'
          ? { ...(parsed as Record<string, unknown>), upsert: importUpsert.value }
          : null;
    const accounts = Array.isArray((payload as { accounts?: unknown } | null)?.accounts)
      ? (payload as { accounts: CreateAccountRequest[] }).accounts
      : null;
    if (!accounts || !accounts.length) {
      ElMessage.error('数据应为 Kiro Account Manager 导出的 JSON，或账号数组 / { accounts: [...] }');
      return;
    }

    const importPayload = payload as Record<string, unknown>;
    importSubmitting.value = true;
    try {
      importResult.value = await accountsApi.importBatch(importPayload);
      ElMessage.success(
        `导入完成：新建 ${importResult.value.created} / 更新 ${importResult.value.updated} / 跳过 ${importResult.value.skipped} / 失败 ${importResult.value.failed}`,
      );
      await loadAccounts();
    } catch (err) {
      if (err instanceof ApiError) ElMessage.error(err.message);
    } finally {
      importSubmitting.value = false;
    }
  }

  onMounted(loadAccounts);
</script>

<template>
  <PageSection title="账号管理" subtitle="维护 Kiro 账号池、配额与状态">
    <template #actions>
      <el-input
        v-model="keyword"
        placeholder="搜索账号 ID / 邮箱 / 区域"
        clearable
        style="width: 240px"
      />
      <el-button @click="openImport">批量导入</el-button>
      <el-button type="primary" @click="openCreate">新建账号</el-button>
    </template>

    <el-table v-loading="loading" :data="visibleAccounts" stripe>
      <el-table-column label="账号 ID" min-width="180">
        <template #default="{ row }">
          <span class="mono">{{ shorten(row.accountId, 10, 6) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column prop="region" label="区域" width="120" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusOf(row).tone" effect="light" round>{{ statusOf(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="请求 / 错误" width="140">
        <template #default="{ row }">
          {{ formatNumber(row.requestCount) }} / {{ formatNumber(row.errorCount) }}
        </template>
      </el-table-column>
      <el-table-column label="配额 (used/limit)" width="180">
        <template #default="{ row }">
          {{ formatNumber(row.quotaUsed) }} / {{ formatNumber(row.quotaLimit) }}
        </template>
      </el-table-column>
      <el-table-column label="最近使用" width="180">
        <template #default="{ row }">{{ formatDateTime(row.lastUsedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-popover placement="bottom-end" width="320" trigger="click">
            <template #reference>
              <el-button link type="primary">测试</el-button>
            </template>
            <div class="row-test-panel">
              <div class="row-test-panel__title">
                <span>账号测试</span>
                <el-tag
                  v-if="latestTest(row.accountId)"
                  :type="testStatusType(latestTest(row.accountId).status)"
                  effect="light"
                  round
                >
                  {{ latestTest(row.accountId).status }}
                </el-tag>
              </div>
              <el-form label-position="top" size="small">
                <el-form-item label="模型">
                  <el-select v-model="testOptions.model" style="width: 100%">
                    <el-option label="auto" value="auto" />
                    <el-option label="claude-sonnet-4.5" value="claude-sonnet-4.5" />
                    <el-option label="simple-task" value="simple-task" />
                    <el-option label="claude-haiku-4.5" value="claude-haiku-4.5" />
                  </el-select>
                </el-form-item>
                <el-form-item label="提示词">
                  <el-input v-model="testOptions.prompt" />
                </el-form-item>
                <el-checkbox v-model="testOptions.onlyEnabled">仅测试可用状态</el-checkbox>
              </el-form>
              <div class="row-test-panel__actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="testingAccountId === row.accountId"
                  @click="testAccount(row)"
                >
                  自动测试
                </el-button>
                <el-button
                  size="small"
                  :disabled="!latestTest(row.accountId)"
                  @click="openTestDetail(row)"
                >
                  查看结果
                </el-button>
              </div>
            </div>
          </el-popover>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="toggleEnabled(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button link type="warning" @click="suspend(row)">挂起</el-button>
          <el-button link type="success" @click="resetAccount(row)">重置</el-button>
          <el-button link type="danger" @click="removeAccount(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </PageSection>

  <!-- 新建 / 编辑 -->
  <el-dialog
    v-model="formVisible"
    :title="formMode === 'create' ? '新建账号' : '编辑账号'"
    width="640px"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item v-if="formMode === 'create'" label="账号 ID（可选）">
        <el-input v-model="form.accountId" placeholder="留空时自动生成" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item :label="formMode === 'edit' ? 'Access Token（留空则不修改）' : 'Access Token'" prop="accessToken">
        <el-input v-model="form.accessToken" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="Refresh Token">
        <el-input v-model="form.refreshToken" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="区域">
            <el-input v-model="form.region" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="认证方式">
            <el-input v-model="form.authMethod" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Provider">
            <el-input v-model="form.provider" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Machine ID">
            <el-input v-model="form.machineId" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Profile ARN">
        <el-input v-model="form.profileArn" />
      </el-form-item>
      <el-form-item label="出站代理 URL">
        <el-input v-model="form.proxyUrl" placeholder="http://user:pass@host:port" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="配额上限">
            <el-input-number v-model="form.quotaLimit" :min="0" :step="1000" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="formMode === 'edit'" label="启用">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="formSubmitting" @click="submitForm">提交</el-button>
    </template>
  </el-dialog>

  <!-- 批量导入 -->
  <el-dialog v-model="importVisible" title="批量导入账号" width="720px">
    <div class="text-secondary mt-8" style="margin-bottom: 8px">
      粘贴 Kiro Account Manager 导出的 JSON，或账号数组 / <span class="mono">{ "accounts": [...] }</span>
    </div>
    <el-input
      v-model="importContent"
      type="textarea"
      :rows="10"
      placeholder='{"accounts":[{"id":"kiro-main","email":"u@example.com","idp":"BuilderId","credentials":{"accessToken":"...","refreshToken":"...","region":"us-east-1"}}]}'
    />
    <el-checkbox v-model="importUpsert" class="mt-16">已存在 accountId 时执行更新（upsert）</el-checkbox>

    <div v-if="importResult" class="import-summary glass-panel mt-16">
      <div class="flex gap-16">
        <div>总计 {{ importResult.total }}</div>
        <div>新建 {{ importResult.created }}</div>
        <div>更新 {{ importResult.updated }}</div>
        <div>跳过 {{ importResult.skipped }}</div>
        <div>失败 {{ importResult.failed }}</div>
      </div>
      <el-table :data="importResult.items" size="small" class="mt-8" max-height="240">
        <el-table-column prop="index" label="#" width="60" />
        <el-table-column prop="accountId" label="账号 ID" min-width="180" />
        <el-table-column prop="status" label="结果" width="100" />
        <el-table-column prop="message" label="信息" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="importVisible = false">关闭</el-button>
      <el-button type="primary" :loading="importSubmitting" @click="submitImport">开始导入</el-button>
    </template>
  </el-dialog>

  <!-- 单账号测试结果 -->
  <el-dialog v-model="testDetailVisible" title="账号测试结果" width="760px">
    <div v-if="testDetail" class="test-detail">
      <div class="test-detail__head">
        <div class="test-detail__title">
          <el-tag :type="testStatusType(testDetail.status)" effect="light" round>
            {{ testDetail.status }}
          </el-tag>
          <span class="mono">{{ testDetail.accountId }}</span>
        </div>
        <div class="test-detail__actions">
          <el-button size="small" @click="copy(testDetailJson)">复制完整结果</el-button>
          <el-button
            size="small"
            type="primary"
            @click="copy(testDetail.detail || testDetail.message || '')"
          >
            {{ testDetail.status === 'FAILED' ? '复制报错信息' : '复制返回信息' }}
          </el-button>
        </div>
      </div>

      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="邮箱">{{ testDetail.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ testDetail.model || '—' }}</el-descriptions-item>
        <el-descriptions-item label="HTTP">{{ testDetail.statusCode ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ testDetail.latencyMs == null ? '—' : `${testDetail.latencyMs}ms` }}
        </el-descriptions-item>
        <el-descriptions-item label="测试时间" :span="2">
          {{ formatDateTime(testDetail.testedAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="test-detail__section">
        <div class="test-detail__section-head">
          <span>{{ testDetail.status === 'FAILED' ? '完整报错信息' : '完整返回信息' }}</span>
          <el-button
            link
            type="primary"
            @click="copy(testDetail.detail || testDetail.message || '')"
          >
            复制
          </el-button>
        </div>
        <pre class="test-detail__pre">{{ testDetail.detail || testDetail.message || '—' }}</pre>
      </div>

      <div class="test-detail__section">
        <div class="test-detail__section-head">
          <span>完整 JSON</span>
          <el-button link type="primary" @click="copy(testDetailJson)">复制</el-button>
        </div>
        <pre class="test-detail__pre">{{ testDetailJson }}</pre>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped lang="scss">
  .row-test-panel {
    display: grid;
    gap: 10px;

    &__title,
    &__actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
    }

    &__title {
      font-weight: 700;
    }
  }

  .test-detail {
    display: grid;
    gap: 14px;

    &__head,
    &__actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }

    &__title {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
    }

    &__section {
      display: grid;
      gap: 6px;
    }

    &__section-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      color: var(--kp-text-secondary);
      font-weight: 700;
    }

    &__pre {
      max-height: 220px;
      overflow: auto;
      margin: 0;
      padding: 12px;
      border: 1px solid rgba(15, 23, 42, 0.08);
      border-radius: 10px;
      background: rgba(15, 23, 42, 0.86);
      color: #e5edff;
      font-family: ui-monospace, 'JetBrains Mono', 'SF Mono', Consolas, monospace;
      font-size: 12px;
      line-height: 1.55;
      white-space: pre-wrap;
      word-break: break-word;
    }
  }

  .import-summary {
    padding: 12px 16px;
  }
</style>
