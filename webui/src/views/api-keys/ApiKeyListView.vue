<script setup lang="ts">
  /**
   * API Key 管理。
   *
   * 关键点：创建后返回的完整密钥仅显示一次，使用独立对话框保留并提供复制操作。
   */
  import { computed, onMounted, reactive, ref } from 'vue';
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
  import PageSection from '@/components/PageSection.vue';
  import { apiKeysApi } from '@/api';
  import type { ApiKey, CreateApiKeyRequest, CreatedApiKey } from '@/types/api';
  import { formatDateTime, formatNumber } from '@/utils/format';

  const loading = ref(false);
  const apiKeys = ref<ApiKey[]>([]);
  const apiOrigin = computed(() => {
    const configured = import.meta.env.VITE_API_BASE;
    if (configured) {
      return String(configured).replace(/\/$/, '');
    }
    if (import.meta.env.DEV) {
      return 'http://127.0.0.1:8080';
    }
    return window.location.origin.replace(/\/$/, '');
  });
  const apiBaseUrl = computed(() => {
    return `${apiOrigin.value}/v1`;
  });
  const apiPort = computed(() => {
    try {
      const url = new URL(apiBaseUrl.value);
      return url.port || (url.protocol === 'https:' ? '443' : '80');
    } catch {
      return '';
    }
  });

  async function loadKeys() {
    loading.value = true;
    try {
      apiKeys.value = await apiKeysApi.list();
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
  const editingKeyId = ref<string | null>(null);

  const form = reactive({
    name: '',
    creditsLimit: undefined as number | undefined,
    enabled: true,
  });

  const rules: FormRules = {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  };

  function resetForm() {
    Object.assign(form, { name: '', creditsLimit: undefined, enabled: true });
  }

  function openCreate() {
    formMode.value = 'create';
    editingKeyId.value = null;
    resetForm();
    formVisible.value = true;
  }

  function openEdit(row: ApiKey) {
    formMode.value = 'edit';
    editingKeyId.value = row.keyId;
    Object.assign(form, {
      name: row.name,
      creditsLimit: row.creditsLimit ?? undefined,
      enabled: row.enabled,
    });
    formVisible.value = true;
  }

  /* 创建后一次性密钥展示 */
  const newKeyVisible = ref(false);
  const newKey = ref<CreatedApiKey | null>(null);
  type SetupPlatform = 'macos' | 'linux' | 'windows';
  const setupPlatforms: Array<{
    platform: SetupPlatform;
    label: string;
    description: string;
  }> = [
    { platform: 'macos', label: 'macOS', description: '写入当前用户 zsh profile' },
    { platform: 'linux', label: 'Linux', description: '自动适配 bash / zsh profile' },
    { platform: 'windows', label: 'Windows PowerShell', description: '写入当前用户 PowerShell profile' },
  ];

  function shQuote(value: string) {
    return `'${value.replace(/'/g, `'\"'\"'`)}'`;
  }

  function psQuote(value: string) {
    return `'${value.replace(/'/g, `''`)}'`;
  }

  type UnixSetupPlatform = 'macos' | 'linux';

  function normalizedApiKey(apiKey?: string | null) {
    return apiKey?.trim() || 'sk-REPLACE_ME';
  }

  function buildUnixSetupScript(platform: UnixSetupPlatform, apiKeyValue = newKey.value?.key) {
    const rootUrl = shQuote(apiOrigin.value);
    const baseUrl = shQuote(apiBaseUrl.value);
    const apiKey = shQuote(normalizedApiKey(apiKeyValue));
    const port = shQuote(apiPort.value);
    const profileLine =
      platform === 'macos'
        ? `PROFILE="\${ZDOTDIR:-$HOME}/.zshrc"`
        : `PROFILE="$HOME/.bashrc"; [ -n "$ZSH_VERSION" ] && PROFILE="\${ZDOTDIR:-$HOME}/.zshrc"`;

    return [
      profileLine,
      `BEGIN="# >>> kiro-proxy env >>>"`,
      `END="# <<< kiro-proxy env <<<"`,
      `touch "$PROFILE"`,
      `sed -i.bak "/$BEGIN/,/$END/d" "$PROFILE"`,
      `cat >> "$PROFILE" <<'EOF'`,
      `# >>> kiro-proxy env >>>`,
      `export OPENAI_BASE_URL=${baseUrl}`,
      `export OPENAI_API_KEY=${apiKey}`,
      `export ANTHROPIC_BASE_URL=${rootUrl}`,
      `export ANTHROPIC_API_KEY=${apiKey}`,
      `export ANTHROPIC_AUTH_TOKEN=${apiKey}`,
      `export ANTHROPIC_MODEL='auto'`,
      `export ANTHROPIC_SMALL_FAST_MODEL='auto'`,
      `export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC='1'`,
      `export KIRO_PROXY_BASE_URL=${baseUrl}`,
      `export KIRO_PROXY_API_KEY=${apiKey}`,
      `export KIRO_PROXY_PORT=${port}`,
      `# <<< kiro-proxy env <<<`,
      `EOF`,
      `export OPENAI_BASE_URL=${baseUrl}`,
      `export OPENAI_API_KEY=${apiKey}`,
      `export ANTHROPIC_BASE_URL=${rootUrl}`,
      `export ANTHROPIC_API_KEY=${apiKey}`,
      `export ANTHROPIC_AUTH_TOKEN=${apiKey}`,
      `export ANTHROPIC_MODEL='auto'`,
      `export ANTHROPIC_SMALL_FAST_MODEL='auto'`,
      `export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC='1'`,
      `export KIRO_PROXY_BASE_URL=${baseUrl}`,
      `export KIRO_PROXY_API_KEY=${apiKey}`,
      `export KIRO_PROXY_PORT=${port}`,
      `echo "saved: $PROFILE"`,
    ].join('\n');
  }

  function buildWindowsSetupScript(apiKeyValue = newKey.value?.key) {
    const rootUrl = psQuote(apiOrigin.value);
    const baseUrl = psQuote(apiBaseUrl.value);
    const apiKey = psQuote(normalizedApiKey(apiKeyValue));
    const port = psQuote(apiPort.value);

    return [
      `New-Item -ItemType Directory -Force (Split-Path $PROFILE) | Out-Null`,
      `$begin = '# >>> kiro-proxy env >>>'`,
      `$end = '# <<< kiro-proxy env <<<'`,
      `$content = if (Test-Path $PROFILE) { Get-Content $PROFILE -Raw } else { '' }`,
      `$pattern = '(?s)\\r?\\n?# >>> kiro-proxy env >>>.*?# <<< kiro-proxy env <<<\\r?\\n?'`,
      `[regex]::Replace($content, $pattern, '') | Set-Content -Path $PROFILE -Encoding UTF8`,
      `@'`,
      `# >>> kiro-proxy env >>>`,
      `$env:OPENAI_BASE_URL = ${baseUrl}`,
      `$env:OPENAI_API_KEY = ${apiKey}`,
      `$env:ANTHROPIC_BASE_URL = ${rootUrl}`,
      `$env:ANTHROPIC_API_KEY = ${apiKey}`,
      `$env:ANTHROPIC_AUTH_TOKEN = ${apiKey}`,
      `$env:ANTHROPIC_MODEL = 'auto'`,
      `$env:ANTHROPIC_SMALL_FAST_MODEL = 'auto'`,
      `$env:CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC = '1'`,
      `$env:KIRO_PROXY_BASE_URL = ${baseUrl}`,
      `$env:KIRO_PROXY_API_KEY = ${apiKey}`,
      `$env:KIRO_PROXY_PORT = ${port}`,
      `# <<< kiro-proxy env <<<`,
      `'@ | Add-Content -Path $PROFILE -Encoding UTF8`,
      `$env:OPENAI_BASE_URL = ${baseUrl}`,
      `$env:OPENAI_API_KEY = ${apiKey}`,
      `$env:ANTHROPIC_BASE_URL = ${rootUrl}`,
      `$env:ANTHROPIC_API_KEY = ${apiKey}`,
      `$env:ANTHROPIC_AUTH_TOKEN = ${apiKey}`,
      `$env:ANTHROPIC_MODEL = 'auto'`,
      `$env:ANTHROPIC_SMALL_FAST_MODEL = 'auto'`,
      `$env:CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC = '1'`,
      `$env:KIRO_PROXY_BASE_URL = ${baseUrl}`,
      `$env:KIRO_PROXY_API_KEY = ${apiKey}`,
      `$env:KIRO_PROXY_PORT = ${port}`,
      `Write-Host "saved: $PROFILE"`,
    ].join('\n');
  }

  function buildSetupScripts(apiKeyValue = newKey.value?.key): Record<SetupPlatform, string> {
    return {
      macos: buildUnixSetupScript('macos', apiKeyValue),
      linux: buildUnixSetupScript('linux', apiKeyValue),
      windows: buildWindowsSetupScript(apiKeyValue),
    };
  }

  function scriptBlocks(scripts: Record<SetupPlatform, string>) {
    return setupPlatforms.map((item) => ({
      ...item,
      script: scripts[item.platform],
    }));
  }

  const setupScripts = computed<Record<SetupPlatform, string>>(() => buildSetupScripts(newKey.value?.key));
  const setupScriptBlocks = computed(() => scriptBlocks(setupScripts.value));
  const connectionText = computed(() =>
    [
      `Base URL: ${apiBaseUrl.value}`,
      `Claude Code URL: ${apiOrigin.value}`,
      `API Key: ${newKey.value?.key ?? ''}`,
      `Port: ${apiPort.value}`,
    ].join('\n'),
  );

  async function copy(text: string, successMessage = '已复制') {
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success(successMessage);
    } catch {
      ElMessage.warning('请手动复制');
    }
  }

  function scriptCopyMessage(label: string) {
    return `已复制 ${label} 脚本`;
  }

  function rowApiKeyPlaceholder(row: ApiKey) {
    return row.keyPrefix ? `${row.keyPrefix}REPLACE_REST_OF_KEY` : 'sk-REPLACE_ME';
  }

  /* 已有密钥只保存前缀，配置面板中使用占位符提醒替换为完整密钥。 */
  const configVisible = ref(false);
  const configKey = ref<ApiKey | null>(null);
  const configDialogTitle = computed(() =>
    configKey.value ? `${configKey.value.name} 配置脚本` : '配置脚本',
  );
  const configApiKey = computed(() =>
    configKey.value ? rowApiKeyPlaceholder(configKey.value) : 'sk-REPLACE_ME',
  );
  const configConnectionText = computed(() =>
    [
      `Base URL: ${apiBaseUrl.value}`,
      `Claude Code URL: ${apiOrigin.value}`,
      `API Key: ${configApiKey.value}`,
      `Port: ${apiPort.value}`,
    ].join('\n'),
  );
  const configScriptBlocks = computed(() => scriptBlocks(buildSetupScripts(configApiKey.value)));

  function openShellConfig(row: ApiKey) {
    configKey.value = row;
    configVisible.value = true;
  }

  async function submitForm() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;

    formSubmitting.value = true;
    try {
      if (formMode.value === 'create') {
        const payload: CreateApiKeyRequest = {
          name: form.name,
          creditsLimit: form.creditsLimit,
        };
        const created = await apiKeysApi.create(payload);
        formVisible.value = false;
        newKey.value = created;
        newKeyVisible.value = true;
      } else if (editingKeyId.value) {
        await apiKeysApi.update(editingKeyId.value, {
          name: form.name,
          creditsLimit: form.creditsLimit,
          enabled: form.enabled,
        });
        ElMessage.success('已更新');
        formVisible.value = false;
      }
      await loadKeys();
    } finally {
      formSubmitting.value = false;
    }
  }

  async function toggleEnabled(row: ApiKey) {
    await apiKeysApi.update(row.keyId, { enabled: !row.enabled });
    ElMessage.success(row.enabled ? '已停用' : '已启用');
    await loadKeys();
  }

  async function removeKey(row: ApiKey) {
    await ElMessageBox.confirm(`确认删除 ${row.name}？`, '危险操作', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'error',
    }).catch(() => Promise.reject(new Error('cancel')));
    await apiKeysApi.remove(row.keyId);
    ElMessage.success('已删除');
    await loadKeys();
  }

  onMounted(loadKeys);
</script>

<template>
  <PageSection title="API Key" subtitle="向客户端下发的代理密钥（sk-...）">
    <template #actions>
      <el-button @click="copy(apiBaseUrl)">复制 API 地址</el-button>
      <el-button type="primary" @click="openCreate">新建 API Key</el-button>
    </template>

    <el-table v-loading="loading" :data="apiKeys" stripe>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="前缀" width="200">
        <template #default="{ row }">
          <span class="mono">{{ row.keyPrefix }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="light" round>
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="积分上限" width="120">
        <template #default="{ row }">{{ formatNumber(row.creditsLimit) }}</template>
      </el-table-column>
      <el-table-column label="累计请求" width="120">
        <template #default="{ row }">{{ formatNumber(row.totalRequests) }}</template>
      </el-table-column>
      <el-table-column label="累计积分" width="120">
        <template #default="{ row }">{{ formatNumber(row.totalCredits) }}</template>
      </el-table-column>
      <el-table-column label="最近使用" width="180">
        <template #default="{ row }">{{ formatDateTime(row.lastUsedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="toggleEnabled(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="openShellConfig(row)">配置脚本</el-button>
          <el-button link type="danger" @click="removeKey(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </PageSection>

  <el-dialog
    v-model="formVisible"
    :title="formMode === 'create' ? '新建 API Key' : '编辑 API Key'"
    width="480px"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="积分上限（可选）">
        <el-input-number v-model="form.creditsLimit" :min="0" :step="100" style="width: 100%" />
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

  <el-dialog v-model="configVisible" :title="configDialogTitle" width="900px">
    <div class="config-dialog-body">
      <el-alert
        class="secret-alert"
        title="已有 API Key 只保留前缀。脚本中的 OPENAI_API_KEY 带占位符，需要替换为创建时保存的完整密钥。"
        type="warning"
        :closable="false"
        show-icon
      />

      <section v-if="configKey" class="secret-panel">
        <div class="secret-panel__head">
          <div>
            <h3>连接信息</h3>
            <p>复制脚本前先确认 API 地址和密钥前缀。</p>
          </div>
          <el-button size="small" @click="copy(configConnectionText, '已复制连接信息')">
            复制连接信息
          </el-button>
        </div>

        <div class="secret-fields">
          <div class="secret-field">
            <span class="secret-field__label">名称</span>
            <code class="secret-field__value mono" :title="configKey.name">{{ configKey.name }}</code>
          </div>
          <div class="secret-field">
            <span class="secret-field__label">Base URL</span>
            <code class="secret-field__value mono" :title="apiBaseUrl">{{ apiBaseUrl }}</code>
            <el-button size="small" @click="copy(apiBaseUrl)">复制</el-button>
          </div>
          <div class="secret-field">
            <span class="secret-field__label">Claude</span>
            <code class="secret-field__value mono" :title="apiOrigin">{{ apiOrigin }}</code>
            <el-button size="small" @click="copy(apiOrigin)">复制</el-button>
          </div>
          <div class="secret-field">
            <span class="secret-field__label">API Key</span>
            <code class="secret-field__value mono" :title="configApiKey">{{ configApiKey }}</code>
            <el-button size="small" @click="copy(configApiKey)">复制</el-button>
          </div>
          <div class="secret-field secret-field--compact">
            <span class="secret-field__label">端口</span>
            <code class="secret-field__value mono">{{ apiPort }}</code>
            <el-button size="small" @click="copy(apiPort)">复制</el-button>
          </div>
        </div>
      </section>

      <section class="secret-panel setup-scripts">
        <div class="secret-panel__head">
          <div>
            <h3>三大平台配置脚本</h3>
            <p>选择对应平台复制到终端执行，会写入当前用户 profile 并立即生效。</p>
          </div>
        </div>

        <div class="setup-script-list">
          <article v-for="item in configScriptBlocks" :key="item.platform" class="script-card">
            <div class="script-card__head">
              <div>
                <h4>{{ item.label }}</h4>
                <p>{{ item.description }}</p>
              </div>
              <el-button size="small" type="primary" @click="copy(item.script, scriptCopyMessage(item.label))">
                复制脚本
              </el-button>
            </div>
            <pre class="setup-scripts__code"><code>{{ item.script }}</code></pre>
          </article>
        </div>
      </section>
    </div>

    <template #footer>
      <el-button type="primary" @click="configVisible = false">完成</el-button>
    </template>
  </el-dialog>

  <!-- 创建后一次性密钥展示 -->
  <el-dialog
    v-model="newKeyVisible"
    title="保存你的新密钥"
    width="760px"
    :close-on-click-modal="false"
  >
    <div class="secret-dialog-body">
      <el-alert
        class="secret-alert"
        title="此密钥只显示一次，关闭后无法找回。请先复制 API Key 或一键配置脚本。"
        type="warning"
        :closable="false"
        show-icon
      />

      <section v-if="newKey" class="secret-panel">
        <div class="secret-panel__head">
          <div>
            <h3>连接信息</h3>
            <p>客户端使用下面的 Base URL 和 API Key 调用代理服务。</p>
          </div>
          <el-button size="small" @click="copy(connectionText)">复制全部</el-button>
        </div>

        <div class="secret-fields">
          <div class="secret-field">
            <span class="secret-field__label">Base URL</span>
            <code class="secret-field__value mono" :title="apiBaseUrl">{{ apiBaseUrl }}</code>
            <el-button size="small" @click="copy(apiBaseUrl)">复制</el-button>
          </div>
          <div class="secret-field">
            <span class="secret-field__label">Claude</span>
            <code class="secret-field__value mono" :title="apiOrigin">{{ apiOrigin }}</code>
            <el-button size="small" @click="copy(apiOrigin)">复制</el-button>
          </div>
          <div class="secret-field">
            <span class="secret-field__label">API Key</span>
            <code class="secret-field__value mono" :title="newKey.key">{{ newKey.key }}</code>
            <el-button size="small" type="primary" @click="copy(newKey.key)">复制</el-button>
          </div>
          <div class="secret-field secret-field--compact">
            <span class="secret-field__label">端口</span>
            <code class="secret-field__value mono">{{ apiPort }}</code>
            <el-button size="small" @click="copy(apiPort)">复制</el-button>
          </div>
        </div>
      </section>

      <section v-if="newKey" class="secret-panel setup-scripts">
        <div class="secret-panel__head">
          <div>
            <h3>控制台一键配置</h3>
            <p>按客户端所在系统复制脚本到终端执行，会写入当前用户 profile 并立即生效。</p>
          </div>
        </div>

        <div class="setup-script-list">
          <article v-for="item in setupScriptBlocks" :key="item.platform" class="script-card">
            <div class="script-card__head">
              <div>
                <h4>{{ item.label }}</h4>
                <p>{{ item.description }}</p>
              </div>
              <el-button size="small" type="primary" @click="copy(item.script, scriptCopyMessage(item.label))">
                复制脚本
              </el-button>
            </div>
            <pre class="setup-scripts__code"><code>{{ item.script }}</code></pre>
          </article>
        </div>
      </section>
    </div>
    <template #footer>
      <el-button type="primary" @click="newKeyVisible = false">已妥善保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
  .secret-dialog-body,
  .config-dialog-body {
    display: grid;
    gap: 14px;
  }

  .secret-alert {
    --el-alert-padding: 10px 12px;
  }

  .secret-panel {
    padding: 14px;
    border: 1px solid rgba(91, 141, 239, 0.16);
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.52);

    &__head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 12px;

      h3 {
        margin: 0;
        font-size: 15px;
        font-weight: 700;
      }

      p {
        margin: 2px 0 0;
        color: var(--kp-text-secondary);
        font-size: 12px;
      }
    }
  }

  .secret-fields {
    display: grid;
    gap: 8px;
  }

  .secret-field {
    min-height: 46px;
    display: grid;
    grid-template-columns: 82px minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px;
    padding: 8px 10px;
    border: 1px solid rgba(15, 23, 42, 0.08);
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.7);

    &__label {
      color: var(--kp-text-secondary);
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
    }

    &__value {
      min-width: 0;
      overflow: hidden;
      color: var(--kp-text);
      font-size: 13px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &--compact {
      grid-template-columns: 82px 120px auto;
      justify-content: start;
    }
  }

  .setup-scripts {
    &__code {
      max-height: 220px;
      overflow: auto;
      margin: 0;
      padding: 12px 14px;
      border: 1px solid rgba(148, 163, 184, 0.2);
      border-radius: 10px;
      background: rgba(15, 23, 42, 0.86);
      color: #e5edff;
      font-size: 12px;
      line-height: 1.6;
      white-space: pre;
    }
  }

  .setup-script-list {
    display: grid;
    gap: 12px;
  }

  .script-card {
    padding: 12px;
    border: 1px solid rgba(148, 163, 184, 0.22);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.68);

    &__head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 10px;

      h4 {
        margin: 0;
        color: var(--kp-text);
        font-size: 14px;
        font-weight: 700;
      }

      p {
        margin: 2px 0 0;
        color: var(--kp-text-secondary);
        font-size: 12px;
      }
    }
  }

  @media (max-width: 720px) {
    .secret-panel__head,
    .script-card__head {
      flex-direction: column;
    }

    .secret-field,
    .secret-field--compact {
      grid-template-columns: 1fr;
      gap: 6px;
      align-items: start;
    }

    .secret-field .el-button {
      justify-self: start;
    }
  }
</style>
