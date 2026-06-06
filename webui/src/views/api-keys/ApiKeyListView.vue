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
  type SetupClient = 'all' | 'claude' | 'opencode';
  type SetupPlatform = 'macos' | 'linux' | 'windows';
  type SetupOption<T extends string> = {
    value: T;
    label: string;
    description: string;
  };
  type SetupScriptBlock = {
    client: SetupClient;
    platform: SetupPlatform;
    clientLabel: string;
    platformLabel: string;
    clientDescription: string;
    platformDescription: string;
    script: string;
  };

  const setupClients: Array<SetupOption<SetupClient>> = [
    { value: 'all', label: '通用', description: '同时写入通用 API 变量、Claude Code 与 OpenCode 配置' },
    { value: 'claude', label: 'Claude Code', description: '适用于 Anthropic/Claude Code 兼容客户端' },
    { value: 'opencode', label: 'OpenCode', description: '生成项目级 opencode.json，并写入所需环境变量' },
  ];
  const setupPlatforms: Array<SetupOption<SetupPlatform>> = [
    { value: 'macos', label: 'macOS', description: '写入当前用户 zsh profile' },
    { value: 'linux', label: 'Linux', description: '自动适配 bash / zsh profile' },
    { value: 'windows', label: 'Windows PowerShell', description: '写入当前用户 PowerShell profile' },
  ];
  const newSetupClient = ref<SetupClient>('all');
  const newSetupPlatform = ref<SetupPlatform>('macos');
  const configSetupClient = ref<SetupClient>('all');
  const configSetupPlatform = ref<SetupPlatform>('macos');

  function shQuote(value: string) {
    return `'${value.replace(/'/g, `'"'"'`)}'`;
  }

  function psQuote(value: string) {
    return `'${value.replace(/'/g, `''`)}'`;
  }

  function jsonString(value: string) {
    return JSON.stringify(value);
  }

  type UnixSetupPlatform = 'macos' | 'linux';

  function normalizedApiKey(apiKey?: string | null) {
    return apiKey?.trim() || 'sk-REPLACE_ME';
  }

  function clientOption(client: SetupClient) {
    return setupClients.find((item) => item.value === client) ?? setupClients[0];
  }

  function platformOption(platform: SetupPlatform) {
    return setupPlatforms.find((item) => item.value === platform) ?? setupPlatforms[0];
  }

  function includeOpenAiEnv(client: SetupClient) {
    return client === 'all';
  }

  function includeClaudeEnv(client: SetupClient) {
    return client === 'all' || client === 'claude' || client === 'opencode';
  }

  function includeOpenCodeConfig(client: SetupClient) {
    return client === 'all' || client === 'opencode';
  }

  function unixEnvLines(client: SetupClient, apiKeyValue = newKey.value?.key) {
    const rootUrl = shQuote(apiOrigin.value);
    const baseUrl = shQuote(apiBaseUrl.value);
    const messagesUrl = shQuote(`${apiOrigin.value}/v1/messages`);
    const apiKey = shQuote(normalizedApiKey(apiKeyValue));
    const port = shQuote(apiPort.value);
    const lines: string[] = [];

    if (includeOpenAiEnv(client)) {
      lines.push(
        `export OPENAI_BASE_URL=${baseUrl}`,
        `export OPENAI_API_KEY=${apiKey}`,
      );
    }
    if (includeClaudeEnv(client)) {
      lines.push(
        `export ANTHROPIC_BASE_URL=${rootUrl}`,
        `export ANTHROPIC_API_KEY=${apiKey}`,
        `export ANTHROPIC_AUTH_TOKEN=${apiKey}`,
        `export ANTHROPIC_MODEL='auto'`,
        `export ANTHROPIC_SMALL_FAST_MODEL='auto'`,
        `export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC='1'`,
      );
    }
    if (includeOpenCodeConfig(client)) {
      lines.push(
        `export OPENCODE_KIRO_PROXY_BASE_URL=${messagesUrl}`,
        `export OPENCODE_KIRO_PROXY_API_KEY=${apiKey}`,
        `export OPENCODE_MODEL='claude-sonnet-4.5'`,
        `export OPENCODE_SMALL_MODEL='claude-haiku-4.5'`,
      );
    }
    lines.push(
      `export KIRO_PROXY_BASE_URL=${baseUrl}`,
      `export KIRO_PROXY_API_KEY=${apiKey}`,
      `export KIRO_PROXY_PORT=${port}`,
    );
    return lines;
  }

  function powershellEnvLines(client: SetupClient, apiKeyValue = newKey.value?.key) {
    const rootUrl = psQuote(apiOrigin.value);
    const baseUrl = psQuote(apiBaseUrl.value);
    const messagesUrl = psQuote(`${apiOrigin.value}/v1/messages`);
    const apiKey = psQuote(normalizedApiKey(apiKeyValue));
    const port = psQuote(apiPort.value);
    const lines: string[] = [];

    if (includeOpenAiEnv(client)) {
      lines.push(
        `$env:OPENAI_BASE_URL = ${baseUrl}`,
        `$env:OPENAI_API_KEY = ${apiKey}`,
      );
    }
    if (includeClaudeEnv(client)) {
      lines.push(
        `$env:ANTHROPIC_BASE_URL = ${rootUrl}`,
        `$env:ANTHROPIC_API_KEY = ${apiKey}`,
        `$env:ANTHROPIC_AUTH_TOKEN = ${apiKey}`,
        `$env:ANTHROPIC_MODEL = 'auto'`,
        `$env:ANTHROPIC_SMALL_FAST_MODEL = 'auto'`,
        `$env:CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC = '1'`,
      );
    }
    if (includeOpenCodeConfig(client)) {
      lines.push(
        `$env:OPENCODE_KIRO_PROXY_BASE_URL = ${messagesUrl}`,
        `$env:OPENCODE_KIRO_PROXY_API_KEY = ${apiKey}`,
        `$env:OPENCODE_MODEL = 'claude-sonnet-4.5'`,
        `$env:OPENCODE_SMALL_MODEL = 'claude-haiku-4.5'`,
      );
    }
    lines.push(
      `$env:KIRO_PROXY_BASE_URL = ${baseUrl}`,
      `$env:KIRO_PROXY_API_KEY = ${apiKey}`,
      `$env:KIRO_PROXY_PORT = ${port}`,
    );
    return lines;
  }

  function opencodeJson() {
    return [
      `{`,
      `  "$schema": "https://opencode.ai/config.json",`,
      `  "provider": {`,
      `    "anthropic": {`,
      `      "options": {`,
      `        "baseURL": ${jsonString(`${apiOrigin.value}/v1/messages`)}`,
      `      }`,
      `    }`,
      `  }`,
      `}`,
    ].join('\n');
  }

  function opencodeUnixLines() {
    return [
      `cat > ./opencode.json <<'EOF'`,
      opencodeJson(),
      `EOF`,
      `echo "saved: $(pwd)/opencode.json"`,
    ];
  }

  function opencodeWindowsLines() {
    return [
      `$opencodeConfig = Join-Path (Get-Location) 'opencode.json'`,
      `@'`,
      opencodeJson(),
      `'@ | Set-Content -Path $opencodeConfig -Encoding UTF8`,
      `Write-Host "saved: $opencodeConfig"`,
    ];
  }

  function buildUnixSetupScript(client: SetupClient, platform: UnixSetupPlatform, apiKeyValue = newKey.value?.key) {
    const envLines = unixEnvLines(client, apiKeyValue);
    const profileLine =
      platform === 'macos'
        ? `PROFILE="\${ZDOTDIR:-$HOME}/.zshrc"`
        : `PROFILE="$HOME/.bashrc"; [ -n "$ZSH_VERSION" ] && PROFILE="\${ZDOTDIR:-$HOME}/.zshrc"`;
    const lines = [
      profileLine,
      `BEGIN="# >>> kiro-proxy env >>>"`,
      `END="# <<< kiro-proxy env <<<"`,
      `touch "$PROFILE"`,
      `sed -i.bak "/$BEGIN/,/$END/d" "$PROFILE"`,
      `cat >> "$PROFILE" <<'EOF'`,
      `# >>> kiro-proxy env >>>`,
      ...envLines,
      `# <<< kiro-proxy env <<<`,
      `EOF`,
      ...envLines,
    ];

    if (includeOpenCodeConfig(client)) {
      lines.push(...opencodeUnixLines());
    }
    lines.push(`echo "saved: $PROFILE"`);
    return lines.join('\n');
  }

  function buildWindowsSetupScript(client: SetupClient, apiKeyValue = newKey.value?.key) {
    const envLines = powershellEnvLines(client, apiKeyValue);
    const lines = [
      `New-Item -ItemType Directory -Force (Split-Path $PROFILE) | Out-Null`,
      `$begin = '# >>> kiro-proxy env >>>'`,
      `$end = '# <<< kiro-proxy env <<<'`,
      `$content = if (Test-Path $PROFILE) { Get-Content $PROFILE -Raw } else { '' }`,
      `$pattern = '(?s)\\r?\\n?# >>> kiro-proxy env >>>.*?# <<< kiro-proxy env <<<\\r?\\n?'`,
      `[regex]::Replace($content, $pattern, '') | Set-Content -Path $PROFILE -Encoding UTF8`,
      `@'`,
      `# >>> kiro-proxy env >>>`,
      ...envLines,
      `# <<< kiro-proxy env <<<`,
      `'@ | Add-Content -Path $PROFILE -Encoding UTF8`,
      ...envLines,
    ];

    if (includeOpenCodeConfig(client)) {
      lines.push(...opencodeWindowsLines());
    }
    lines.push(`Write-Host "saved: $PROFILE"`);
    return lines.join('\n');
  }

  function buildSetupScript(client: SetupClient, platform: SetupPlatform, apiKeyValue = newKey.value?.key) {
    if (platform === 'windows') {
      return buildWindowsSetupScript(client, apiKeyValue);
    }
    return buildUnixSetupScript(client, platform, apiKeyValue);
  }

  function scriptBlock(client: SetupClient, platform: SetupPlatform, apiKeyValue = newKey.value?.key): SetupScriptBlock {
    const clientMeta = clientOption(client);
    const platformMeta = platformOption(platform);
    return {
      client,
      platform,
      clientLabel: clientMeta.label,
      platformLabel: platformMeta.label,
      clientDescription: clientMeta.description,
      platformDescription: platformMeta.description,
      script: buildSetupScript(client, platform, apiKeyValue),
    };
  }

  const setupScriptBlock = computed(() =>
    scriptBlock(newSetupClient.value, newSetupPlatform.value, newKey.value?.key),
  );
  const connectionText = computed(() =>
    [
      `Base URL: ${apiBaseUrl.value}`,
      `Claude Code URL: ${apiOrigin.value}`,
      `OpenCode URL: ${apiOrigin.value}/v1/messages`,
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

  function scriptCopyMessage(block: SetupScriptBlock) {
    return `已复制 ${block.clientLabel} / ${block.platformLabel} 脚本`;
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
      `OpenCode URL: ${apiOrigin.value}/v1/messages`,
      `API Key: ${configApiKey.value}`,
      `Port: ${apiPort.value}`,
    ].join('\n'),
  );
  const configScriptBlock = computed(() =>
    scriptBlock(configSetupClient.value, configSetupPlatform.value, configApiKey.value),
  );

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
            <span class="secret-field__label">OpenCode</span>
            <code class="secret-field__value mono" :title="`${apiOrigin}/v1/messages`">
              {{ apiOrigin }}/v1/messages
            </code>
            <el-button size="small" @click="copy(`${apiOrigin}/v1/messages`)">复制</el-button>
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
            <h3>配置脚本</h3>
            <p>先选客户端，再选系统平台；OpenCode 会额外生成当前目录的 opencode.json。</p>
          </div>
          <el-button
            size="small"
            type="primary"
            @click="copy(configScriptBlock.script, scriptCopyMessage(configScriptBlock))"
          >
            复制当前脚本
          </el-button>
        </div>

        <div class="setup-controls">
          <div class="setup-control">
            <span class="setup-control__label">客户端</span>
            <el-radio-group v-model="configSetupClient" size="small">
              <el-radio-button v-for="item in setupClients" :key="item.value" :label="item.value">
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
          <div class="setup-control">
            <span class="setup-control__label">平台</span>
            <el-radio-group v-model="configSetupPlatform" size="small">
              <el-radio-button v-for="item in setupPlatforms" :key="item.value" :label="item.value">
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="script-preview">
          <div>
            <h4>{{ configScriptBlock.clientLabel }} / {{ configScriptBlock.platformLabel }}</h4>
            <p>{{ configScriptBlock.clientDescription }}；{{ configScriptBlock.platformDescription }}。</p>
          </div>
          <pre class="setup-scripts__code"><code>{{ configScriptBlock.script }}</code></pre>
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
            <span class="secret-field__label">OpenCode</span>
            <code class="secret-field__value mono" :title="`${apiOrigin}/v1/messages`">
              {{ apiOrigin }}/v1/messages
            </code>
            <el-button size="small" @click="copy(`${apiOrigin}/v1/messages`)">复制</el-button>
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
            <p>先选客户端，再选系统平台；新密钥会直接写入脚本。</p>
          </div>
          <el-button
            size="small"
            type="primary"
            @click="copy(setupScriptBlock.script, scriptCopyMessage(setupScriptBlock))"
          >
            复制当前脚本
          </el-button>
        </div>

        <div class="setup-controls">
          <div class="setup-control">
            <span class="setup-control__label">客户端</span>
            <el-radio-group v-model="newSetupClient" size="small">
              <el-radio-button v-for="item in setupClients" :key="item.value" :label="item.value">
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
          <div class="setup-control">
            <span class="setup-control__label">平台</span>
            <el-radio-group v-model="newSetupPlatform" size="small">
              <el-radio-button v-for="item in setupPlatforms" :key="item.value" :label="item.value">
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="script-preview">
          <div>
            <h4>{{ setupScriptBlock.clientLabel }} / {{ setupScriptBlock.platformLabel }}</h4>
            <p>{{ setupScriptBlock.clientDescription }}；{{ setupScriptBlock.platformDescription }}。</p>
          </div>
          <pre class="setup-scripts__code"><code>{{ setupScriptBlock.script }}</code></pre>
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
      max-height: 280px;
      overflow: auto;
      margin: 0;
      padding: 12px 14px;
      border: 1px solid rgba(148, 163, 184, 0.2);
      border-radius: 8px;
      background: rgba(15, 23, 42, 0.86);
      color: #e5edff;
      font-size: 12px;
      line-height: 1.6;
      white-space: pre;
    }
  }

  .setup-controls {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    margin-bottom: 12px;
  }

  .setup-control {
    display: grid;
    grid-template-columns: 68px minmax(0, 1fr);
    align-items: center;
    gap: 10px;

    &__label {
      color: var(--kp-text-secondary);
      font-size: 12px;
      font-weight: 700;
    }
  }

  .script-preview {
    display: grid;
    gap: 10px;

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

  @media (max-width: 720px) {
    .secret-panel__head {
      flex-direction: column;
    }

    .secret-field,
    .secret-field--compact,
    .setup-control {
      grid-template-columns: 1fr;
      gap: 6px;
      align-items: start;
    }

    .secret-field .el-button {
      justify-self: start;
    }
  }
</style>
