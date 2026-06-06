<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import { ElMessage } from 'element-plus';
  import PageSection from '@/components/PageSection.vue';
  import { systemApi } from '@/api';
  import type { ModelInfo } from '@/types/api';

  interface ModelCatalogItem extends ModelInfo {
    provider: string;
    family: string;
    displayName: string;
    contextTokens: number | null;
    outputTokens: number | null;
  }

  interface FamilyGroup {
    name: string;
    models: ModelCatalogItem[];
  }

  interface ProviderGroup {
    provider: string;
    count: number;
    families: FamilyGroup[];
  }

  const loading = ref(false);
  const models = ref<ModelInfo[]>([]);
  const activeProvider = ref<string | null>(null);

  const keyword = ref('');
  const familyFilter = ref('all');
  const ownerFilter = ref('all');

  async function loadModels() {
    loading.value = true;
    try {
      models.value = await systemApi.models();
    } catch {
      ElMessage.error('模型列表加载失败');
    } finally {
      loading.value = false;
    }
  }

  async function copy(text: string, message = '已复制') {
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success(message);
    } catch {
      ElMessage.warning('请手动复制');
    }
  }

  const parsedModels = computed<ModelCatalogItem[]>(() => {
    return models.value.map((model) => {
      const contextTokens = model.maxInputTokens ?? model.context_length ?? null;
      const outputTokens = model.maxOutputTokens ?? model.max_output_tokens ?? null;
      
      // Determine provider
      let provider = model.provider;
      if (!provider) {
        const lowerId = model.id.toLowerCase();
        if (model.owned_by === 'kiro-proxy' || lowerId.startsWith('gpt-') || lowerId.startsWith('o')) {
          provider = 'OpenAI';
        } else if (model.owned_by === 'kiro-api' || lowerId.startsWith('claude')) {
          provider = 'Anthropic';
        } else {
          provider = 'Other';
        }
      }
      
      // Determine family
      let family = model.family;
      if (!family) {
        const lowerId = model.id.toLowerCase();
        if (lowerId.startsWith('gpt-5')) family = 'GPT-5';
        else if (lowerId.startsWith('gpt-4') || lowerId.startsWith('chatgpt-4')) family = 'GPT-4';
        else if (lowerId.startsWith('gpt-3.5')) family = 'GPT-3.5';
        else if (/^o\d/.test(lowerId)) family = 'OpenAI Reasoning';
        else if (model.id.startsWith('CLAUDE_')) family = 'CodeWhisperer Internal';
        else if (lowerId === 'auto' || lowerId === 'simple-task') family = 'Kiro Preset';
        else if (lowerId.includes('3.')) family = 'Claude 3';
        else if (lowerId.startsWith('claude')) family = 'Claude 4';
        else family = 'Other';
      }

      return {
        ...model,
        provider,
        family,
        displayName: model.name || model.modelName || model.id,
        contextTokens,
        outputTokens,
      };
    });
  });

  const summary = computed(() => {
    const items = parsedModels.value;
    return {
      total: items.length,
      openAi: items.filter(item => item.provider === 'OpenAI').length,
      anthropic: items.filter(item => item.provider === 'Anthropic').length,
      internalIds: items.filter(item => item.family === 'CodeWhisperer Internal').length,
      textOnly: items.filter(item => item.supportedInputTypes?.length === 1 && item.supportedInputTypes[0] === 'TEXT').length
    };
  });

  const allFamilies = computed(() => {
    return Array.from(new Set(parsedModels.value.map(m => m.family))).filter(Boolean).sort();
  });

  const allOwners = computed(() => {
    return Array.from(new Set(parsedModels.value.map(m => m.owned_by))).filter(Boolean).sort();
  });

  const filteredModels = computed(() => {
    const q = keyword.value.trim().toLowerCase();
    return parsedModels.value.filter((model) => {
      if (familyFilter.value !== 'all' && model.family !== familyFilter.value) return false;
      if (ownerFilter.value !== 'all' && model.owned_by !== ownerFilter.value) return false;
      
      if (!q) return true;
      return [
        model.id,
        model.displayName,
        model.provider,
        model.family,
        model.targetModelId ?? '',
        model.owned_by,
        model.description ?? '',
      ].some((value) => value.toLowerCase().includes(q));
    });
  });

  const groupedModels = computed<ProviderGroup[]>(() => {
    const items = filteredModels.value;
    const providerMap = new Map<string, Map<string, ModelCatalogItem[]>>();
    
    items.forEach((item) => {
      if (!providerMap.has(item.provider)) {
        providerMap.set(item.provider, new Map());
      }
      const families = providerMap.get(item.provider)!;
      if (!families.has(item.family)) {
        families.set(item.family, []);
      }
      families.get(item.family)!.push(item);
    });

    const providerOrder = ['OpenAI', 'Anthropic', 'Other'];
    const familyOrder = [
      'GPT-5',
      'GPT-4',
      'GPT-3.5',
      'OpenAI Reasoning',
      'Claude 4',
      'Claude 3',
      'Kiro Preset',
      'CodeWhisperer Internal',
      'Other',
    ];

    function orderOf<T>(order: T[], value: T) {
      const index = order.indexOf(value);
      return index === -1 ? order.length : index;
    }

    return Array.from(providerMap.entries())
      .map(([provider, families]) => {
        const familyGroups = Array.from(families.entries())
          .map(([name, familyModels]) => ({ name, models: familyModels }))
          .sort((a, b) => orderOf(familyOrder, a.name) - orderOf(familyOrder, b.name));
        return {
          provider,
          count: familyGroups.reduce((sum, f) => sum + f.models.length, 0),
          families: familyGroups,
        };
      })
      .sort((a, b) => orderOf(providerOrder, a.provider) - orderOf(providerOrder, b.provider));
  });

  watch(groupedModels, (newGroups) => {
    if (newGroups && newGroups.length) {
      if (!activeProvider.value || !newGroups.some(g => g.provider === activeProvider.value)) {
        activeProvider.value = newGroups[0].provider;
      }
    } else {
      activeProvider.value = null;
    }
  }, { immediate: true });

  const activeGroup = computed(() => {
    if (!activeProvider.value) return null;
    return groupedModels.value.find(g => g.provider === activeProvider.value) || null;
  });

  const allModelIds = computed(() => models.value.map((model) => model.id).join('\n'));

  function selectProvider(provider: string) {
    if (activeProvider.value === provider) {
      activeProvider.value = null;
    } else {
      activeProvider.value = provider;
    }
  }

  function formatTokens(value: number | null | undefined) {
    if (value == null) return '-';
    if (value >= 1000000) {
      return (value / 1000000).toFixed(0) + 'M';
    }
    if (value >= 1000) {
      return (value / 1000).toFixed(0) + 'K';
    }
    return value.toLocaleString();
  }

  function getOwnerTagStyle(owner: string) {
    const o = owner.toLowerCase();
    if (o.includes('proxy')) {
      return { background: 'rgba(239, 91, 107, 0.08)', color: '#ef5b6b', border: '1px solid rgba(239, 91, 107, 0.2)' };
    }
    if (o.includes('api') || o.includes('kiro')) {
      return { background: 'rgba(179, 135, 255, 0.08)', color: '#b387ff', border: '1px solid rgba(179, 135, 255, 0.2)' };
    }
    if (o === 'openai' || o === 'system') {
      return { background: 'rgba(91, 141, 239, 0.08)', color: '#5b8def', border: '1px solid rgba(91, 141, 239, 0.2)' };
    }
    return { background: 'rgba(15, 23, 42, 0.05)', color: 'var(--kp-text-secondary)', border: '1px solid rgba(15, 23, 42, 0.1)' };
  }

  function getInputTagStyle(type: string) {
    const t = type.toUpperCase();
    if (t === 'TEXT') {
      return { background: 'rgba(54, 192, 138, 0.1)', color: '#36c08a' };
    }
    if (t === 'IMAGE') {
      return { background: 'rgba(179, 135, 255, 0.1)', color: '#b387ff' };
    }
    if (t === 'AUDIO') {
      return { background: 'rgba(91, 141, 239, 0.1)', color: '#5b8def' };
    }
    return { background: 'rgba(15, 23, 42, 0.06)', color: 'var(--kp-text-secondary)' };
  }

  onMounted(loadModels);
</script>

<template>
  <PageSection title="模型 ID" subtitle="查看与映射系统支持的 LLM 模型 ID 列表">
    <template #actions>
      <div class="model-page__buttons">
        <el-button class="action-btn" @click="copy(allModelIds, '已复制全部模型 ID')">
          <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="btn-icon">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
          </svg>
          复制全部 ID
        </el-button>
        <el-button class="action-btn" :loading="loading" type="primary" @click="loadModels">
          <template #loading>
            <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="btn-icon spin-icon">
              <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path>
            </svg>
          </template>
          <svg v-if="!loading" xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="btn-icon">
            <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path>
          </svg>
          刷新
        </el-button>
      </div>
    </template>

    <!-- 1. Stats Summary Cards Deck -->
    <div class="model-summary-deck">
      <div class="model-card-summary glass-panel card-total">
        <div class="model-card-summary__content">
          <span class="model-card-summary__label">总模型数</span>
          <strong class="model-card-summary__value">{{ summary.total }}</strong>
        </div>
        <div class="model-card-summary__icon">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="12 2 2 7 12 12 22 7 12 2" />
            <polyline points="2 17 12 22 22 17" />
            <polyline points="2 12 12 17 22 12" />
          </svg>
        </div>
      </div>

      <div class="model-card-summary glass-panel card-openai">
        <div class="model-card-summary__content">
          <span class="model-card-summary__label">OpenAI</span>
          <strong class="model-card-summary__value">{{ summary.openAi }}</strong>
        </div>
        <div class="model-card-summary__icon">
          <svg viewBox="0 0 24 24" width="36" height="36" fill="currentColor" style="color: #10a37f;">
            <path d="M22.2819 9.8211a5.9847 5.9847 0 0 0-.5157-4.9108 6.0462 6.0462 0 0 0-6.5098-2.9A6.0651 6.0651 0 0 0 4.9807 4.1818a5.9847 5.9847 0 0 0-3.9977 2.9 6.0462 6.0462 0 0 0 .7427 7.0966 5.98 5.98 0 0 0 .511 4.9107 6.051 6.051 0 0 0 6.5146 2.9001A5.9847 5.9847 0 0 0 13.2599 24a6.0557 6.0557 0 0 0 5.7718-4.2058 5.9894 5.9894 0 0 0 3.9977-2.9001 6.0557 6.0557 0 0 0-.7475-7.0729zm-9.022 12.6081a4.4755 4.4755 0 0 1-2.8764-1.0408l.1419-.0804 4.7783-2.7582a.7948.7948 0 0 0 .3927-.6813v-6.7369l2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.504 4.504 0 0 1-4.4945 4.4944zm-9.6607-4.1254a4.4708 4.4708 0 0 1-.5346-3.0137l.142.0852 4.783 2.7582a.7712.7712 0 0 0 .7806 0l5.8428-3.3685v2.3324a.0804.0804 0 0 1-.0332.0615L9.74 19.9502a4.4992 4.4992 0 0 1-6.1408-1.6464zM2.3408 7.8956a4.485 4.485 0 0 1 2.3655-1.9728V11.6a.7664.7664 0 0 0 .3879.6765l5.8144 3.3543-2.0201 1.1685a.0757.0757 0 0 1-.071 0l-4.8303-2.7865A4.504 4.504 0 0 1 2.3408 7.872zm16.5963 3.8558L13.1038 8.364 15.1192 7.2a.0757.0757 0 0 1 .071 0l4.8303 2.7913a4.4944 4.4944 0 0 1-.6765 8.1042v-5.6772a.79.79 0 0 0-.407-.667zm2.0107-3.0231l-.142-.0852-4.7735-2.7818a.7759.7759 0 0 0-.7854 0L9.409 9.2297V6.8974a.0662.0662 0 0 1 .0284-.0615l4.8303-2.7866a4.4992 4.4992 0 0 1 6.6802 4.66zM8.3065 12.863l-2.02-1.1638a.0804.0804 0 0 1-.038-.0567V6.0742a4.4992 4.4992 0 0 1 7.3757-3.4537l-.142.0805L8.704 5.459a.7948.7948 0 0 0-.3927.6813zm1.0976-2.3654l2.602-1.4998 2.6069 1.4998v2.9994l-2.5974 1.4997-2.6067-1.4997Z"/>
          </svg>
        </div>
      </div>

      <div class="model-card-summary glass-panel card-anthropic">
        <div class="model-card-summary__content">
          <span class="model-card-summary__label">Anthropic</span>
          <strong class="model-card-summary__value">{{ summary.anthropic }}</strong>
        </div>
        <div class="model-card-summary__icon">
          <svg viewBox="0 0 24 24" width="36" height="36" fill="currentColor" style="color: #d97757;">
            <path d="M12.5 3L4.2 19h4l2.1-4.2h5.4l2.1 4.2h4L13.5 3h-1zM11.3 12l1.7-3.4 1.7 3.4h-3.4z"/>
          </svg>
        </div>
      </div>

      <div class="model-card-summary glass-panel card-internal">
        <div class="model-card-summary__content">
          <span class="model-card-summary__label">内部 ID</span>
          <strong class="model-card-summary__value">{{ summary.internalIds }}</strong>
        </div>
        <div class="model-card-summary__icon">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="16 18 22 12 16 6" />
            <polyline points="8 6 2 12 8 18" />
          </svg>
        </div>
      </div>

      <div class="model-card-summary glass-panel card-textonly">
        <div class="model-card-summary__content">
          <span class="model-card-summary__label">仅限文本</span>
          <strong class="model-card-summary__value">{{ summary.textOnly }}</strong>
        </div>
        <div class="model-card-summary__icon">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="16" y1="13" x2="8" y2="13" />
            <line x1="16" y1="17" x2="8" y2="17" />
            <polyline points="10 9 9 9 8 9" />
          </svg>
        </div>
      </div>
    </div>

    <!-- 2. Filter Bar -->
    <div class="model-filter glass-panel">
      <div class="model-filter__search-wrapper">
        <el-input
          v-model="keyword"
          class="model-filter__search"
          placeholder="搜索模型 ID / 名称 / 描述..."
          clearable
        >
          <template #prefix>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="search-icon">
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
          </template>
        </el-input>
      </div>
      
      <div class="model-filter__selects">
        <div class="filter-group">
          <span class="filter-group__label">分类</span>
          <el-select v-model="familyFilter" class="model-filter__select" placeholder="选择分类">
            <el-option label="全部分类" value="all" />
            <el-option v-for="item in allFamilies" :key="item" :label="item" :value="item" />
          </el-select>
        </div>

        <div class="filter-group">
          <span class="filter-group__label">来源</span>
          <el-select v-model="ownerFilter" class="model-filter__select" placeholder="选择来源">
            <el-option label="全部来源" value="all" />
            <el-option v-for="item in allOwners" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- 3. Provider Selector Cards (small rectangles side-by-side) -->
    <div v-if="groupedModels.length" class="provider-tabs">
      <div
        v-for="group in groupedModels"
        :key="group.provider"
        class="provider-tab-card glass-panel"
        :class="[
          `provider-tab-card--${group.provider.toLowerCase()}`,
          { 'is-active': activeProvider === group.provider }
        ]"
        @click="selectProvider(group.provider)"
      >
        <div class="provider-tab-card__left">
          <span class="provider-logo-badge" :class="`provider-logo-badge--${group.provider.toLowerCase()}`">
            <!-- OpenAI Inline SVG -->
            <svg v-if="group.provider === 'OpenAI'" viewBox="0 0 24 24" width="22" height="22" fill="currentColor" class="logo-svg" style="color: #10a37f;">
              <path d="M22.2819 9.8211a5.9847 5.9847 0 0 0-.5157-4.9108 6.0462 6.0462 0 0 0-6.5098-2.9A6.0651 6.0651 0 0 0 4.9807 4.1818a5.9847 5.9847 0 0 0-3.9977 2.9 6.0462 6.0462 0 0 0 .7427 7.0966 5.98 5.98 0 0 0 .511 4.9107 6.051 6.051 0 0 0 6.5146 2.9001A5.9847 5.9847 0 0 0 13.2599 24a6.0557 6.0557 0 0 0 5.7718-4.2058 5.9894 5.9894 0 0 0 3.9977-2.9001 6.0557 6.0557 0 0 0-.7475-7.0729zm-9.022 12.6081a4.4755 4.4755 0 0 1-2.8764-1.0408l.1419-.0804 4.7783-2.7582a.7948.7948 0 0 0 .3927-.6813v-6.7369l2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.504 4.504 0 0 1-4.4945 4.4944zm-9.6607-4.1254a4.4708 4.4708 0 0 1-.5346-3.0137l.142.0852 4.783 2.7582a.7712.7712 0 0 0 .7806 0l5.8428-3.3685v2.3324a.0804.0804 0 0 1-.0332.0615L9.74 19.9502a4.4992 4.4992 0 0 1-6.1408-1.6464zM2.3408 7.8956a4.485 4.485 0 0 1 2.3655-1.9728V11.6a.7664.7664 0 0 0 .3879.6765l5.8144 3.3543-2.0201 1.1685a.0757.0757 0 0 1-.071 0l-4.8303-2.7865A4.504 4.504 0 0 1 2.3408 7.872zm16.5963 3.8558L13.1038 8.364 15.1192 7.2a.0757.0757 0 0 1 .071 0l4.8303 2.7913a4.4944 4.4944 0 0 1-.6765 8.1042v-5.6772a.79.79 0 0 0-.407-.667zm2.0107-3.0231l-.142-.0852-4.7735-2.7818a.7759.7759 0 0 0-.7854 0L9.409 9.2297V6.8974a.0662.0662 0 0 1 .0284-.0615l4.8303-2.7866a4.4992 4.4992 0 0 1 6.6802 4.66zM8.3065 12.863l-2.02-1.1638a.0804.0804 0 0 1-.038-.0567V6.0742a4.4992 4.4992 0 0 1 7.3757-3.4537l-.142.0805L8.704 5.459a.7948.7948 0 0 0-.3927.6813zm1.0976-2.3654l2.602-1.4998 2.6069 1.4998v2.9994l-2.5974 1.4997-2.6067-1.4997Z"/>
            </svg>
            <!-- Anthropic Inline SVG -->
            <svg v-else-if="group.provider === 'Anthropic'" viewBox="0 0 24 24" width="22" height="22" fill="currentColor" class="logo-svg" style="color: #d97757;">
              <path d="M12.5 3L4.2 19h4l2.1-4.2h5.4l2.1 4.2h4L13.5 3h-1zM11.3 12l1.7-3.4 1.7 3.4h-3.4z"/>
            </svg>
            <span v-else class="provider-logo-fallback">{{ group.provider.slice(0, 1) }}</span>
          </span>
          <div class="provider-tab-card__info">
            <h4 class="provider-tab-card__title">{{ group.provider }}</h4>
            <p class="provider-tab-card__sub">{{ group.count }} 个模型 ID</p>
          </div>
        </div>
        <div class="provider-tab-card__arrow">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="arrow-icon">
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </div>
      </div>
    </div>

    <!-- 4. Selected Provider content (rendered back as family tables stacked vertically) -->
    <transition name="fade-slide">
      <div
        v-if="activeGroup"
        :key="activeGroup.provider"
        class="active-provider-content glass-panel"
        :class="`active-provider-content--${activeGroup.provider.toLowerCase()}`"
      >
        <div class="active-provider-content__header">
          <div class="header-title-area">
            <span class="brand-indicator"></span>
            <h3 class="panel-title">{{ activeGroup.provider }} 官方模型明细</h3>
          </div>
          <span class="active-badge">{{ activeGroup.families.length }} 个分类</span>
        </div>

        <div v-loading="loading" class="active-provider-content__body">
          <div v-for="family in activeGroup.families" :key="family.name" class="model-family-section">
            <div class="model-family-section__header">
              <div class="model-family-section__title-row">
                <span class="bullet-dot"></span>
                <span class="family-name">{{ family.name }}</span>
              </div>
              <span class="family-badge">{{ family.models.length }} 个模型</span>
            </div>
            
            <div class="table-wrapper">
              <el-table :data="family.models" class="model-table" stripe>
                <!-- Model ID Column -->
                <el-table-column label="模型 ID" min-width="180">
                  <template #default="{ row }">
                    <div class="model-id-cell">
                      <el-tooltip :content="row.id" placement="top" :show-after="500">
                        <code class="mono-badge">{{ row.id }}</code>
                      </el-tooltip>
                      <el-button
                        size="small"
                        class="copy-btn"
                        link
                        type="primary"
                        @click.stop="copy(row.id, '已复制模型 ID')"
                        title="复制模型 ID"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                        </svg>
                      </el-button>
                    </div>
                  </template>
                </el-table-column>

                <!-- Display Name Column -->
                <el-table-column prop="displayName" label="显示名称" min-width="170" show-overflow-tooltip />

                <!-- Route To Column -->
                <el-table-column label="路由映射至" min-width="180">
                  <template #default="{ row }">
                    <div v-if="!row.targetModelId || row.targetModelId === row.id" class="direct-link">
                      <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="direct-icon">
                        <polyline points="20 6 9 17 4 12"></polyline>
                      </svg>
                      <span>直接连接</span>
                    </div>
                    <div v-else class="mapped-link">
                      <span class="map-arrow">➔</span>
                      <el-tooltip :content="row.targetModelId" placement="top" :show-after="500">
                        <code class="mono-badge mapped">{{ row.targetModelId }}</code>
                      </el-tooltip>
                    </div>
                  </template>
                </el-table-column>

                <!-- Source Column -->
                <el-table-column label="来源" width="120">
                  <template #default="{ row }">
                    <span class="custom-badge" :style="getOwnerTagStyle(row.owned_by)">
                      {{ row.owned_by }}
                    </span>
                  </template>
                </el-table-column>

                <!-- Inputs Column -->
                <el-table-column label="支持输入" width="130">
                  <template #default="{ row }">
                    <div class="tags-container">
                      <span
                        v-for="inputType in (row.supportedInputTypes || [])"
                        :key="inputType"
                        class="pill-tag"
                        :style="getInputTagStyle(inputType)"
                      >
                        {{ inputType }}
                      </span>
                      <span v-if="!row.supportedInputTypes || !row.supportedInputTypes.length" class="empty-placeholder">-</span>
                    </div>
                  </template>
                </el-table-column>

                <!-- Context Column -->
                <el-table-column label="上下文" width="110" align="center">
                  <template #default="{ row }">
                    <el-tooltip
                      v-if="row.contextTokens"
                      :content="row.contextTokens.toLocaleString() + ' tokens'"
                      placement="top"
                      effect="dark"
                    >
                      <span class="token-value font-mono">{{ formatTokens(row.contextTokens) }}</span>
                    </el-tooltip>
                    <span v-else class="empty-placeholder">-</span>
                  </template>
                </el-table-column>

                <!-- Output Column -->
                <el-table-column label="最大输出" width="100" align="center">
                  <template #default="{ row }">
                    <el-tooltip
                      v-if="row.outputTokens"
                      :content="row.outputTokens.toLocaleString() + ' tokens'"
                      placement="top"
                      effect="dark"
                    >
                      <span class="token-value font-mono">{{ formatTokens(row.outputTokens) }}</span>
                    </el-tooltip>
                    <span v-else class="empty-placeholder">-</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <el-empty v-if="!loading && !groupedModels.length" description="没有找到匹配的模型 ID" />
  </PageSection>
</template>

<style scoped lang="scss">
  /* Stats Cards Deck */
  .model-summary-deck {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 16px;
    margin-bottom: 20px;
    width: 100%;
  }

  .model-card-summary {
    position: relative;
    padding: 18px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    overflow: hidden;
    min-height: 84px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    cursor: default;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      width: 4px;
      height: 100%;
      background: transparent;
      transition: background-color 0.3s;
    }

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 16px 36px rgba(31, 45, 80, 0.16);
      background: var(--kp-surface-strong);

      .model-card-summary__icon {
        transform: scale(1.1) rotate(3deg);
        opacity: 0.25;
      }
    }

    &__content {
      display: flex;
      flex-direction: column;
      justify-content: center;
      z-index: 1;
    }

    &__label {
      color: var(--kp-text-secondary);
      font-size: 13px;
      font-weight: 500;
    }

    &__value {
      margin-top: 4px;
      color: var(--kp-text);
      font-size: 26px;
      font-weight: 800;
      line-height: 1.1;
    }

    &__icon {
      position: absolute;
      right: 16px;
      bottom: -6px;
      width: 54px;
      height: 54px;
      color: var(--kp-text-muted);
      opacity: 0.12;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      display: flex;
      align-items: center;
      justify-content: center;
      pointer-events: none;

      svg {
        width: 100%;
        height: 100%;
      }

      &.img-icon {
        bottom: 8px;
        width: 44px;
        height: 44px;

        img {
          width: 100%;
          height: 100%;
          object-fit: contain;
          filter: grayscale(80%);
          transition: filter 0.3s;
        }
      }
    }

    // Themes
    &.card-total {
      &::before { background: var(--kp-primary); }
      .model-card-summary__icon { color: var(--kp-primary); }
    }

    &.card-openai {
      &::before { background: #10a37f; }
      .model-card-summary__icon { color: #10a37f; }
      &:hover {
        .img-icon img { filter: grayscale(0%); }
      }
    }

    &.card-anthropic {
      &::before { background: #d97757; }
      .model-card-summary__icon { color: #d97757; }
      &:hover {
        .img-icon img { filter: grayscale(0%); }
      }
    }

    &.card-internal {
      &::before { background: var(--kp-accent); }
      .model-card-summary__icon { color: var(--kp-accent); }
    }

    &.card-textonly {
      &::before { background: var(--kp-success); }
      .model-card-summary__icon { color: var(--kp-success); }
    }
  }

  /* Filter Bar styles */
  .model-filter {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    align-items: center;
    padding: 12px 18px;
    margin-bottom: 20px;
    border-radius: var(--kp-radius-sm);
    background: rgba(255, 255, 255, 0.45);
    box-shadow: 0 4px 20px rgba(15, 23, 42, 0.02);

    &__search-wrapper {
      flex: 1;
      min-width: 240px;
    }

    &__search {
      width: 100%;
      
      .search-icon {
        width: 15px;
        height: 15px;
        color: var(--kp-text-muted);
        margin-right: 4px;
      }
    }

    &__selects {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      align-items: center;
    }

    &__select {
      width: 140px;
    }
  }

  .filter-group {
    display: flex;
    align-items: center;
    gap: 8px;

    &__label {
      font-size: 12px;
      color: var(--kp-text-secondary);
      font-weight: 600;
      white-space: nowrap;
    }
  }

  /* Provider Tabs (small rectangles side-by-side) */
  .provider-tabs {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
    width: 100%;
    margin-bottom: 20px;
  }

  .provider-tab-card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 18px;
    border-radius: var(--kp-radius-sm);
    background: rgba(255, 255, 255, 0.55);
    border: 1px solid rgba(15, 23, 42, 0.08);
    box-shadow: 0 4px 18px rgba(31, 45, 80, 0.03);
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    user-select: none;

    &:hover {
      transform: translateY(-2px);
      background: rgba(255, 255, 255, 0.88);
      border-color: rgba(91, 141, 239, 0.25);
      box-shadow: 0 8px 24px rgba(31, 45, 80, 0.08);
    }

    &__left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    &__info {
      text-align: left;
    }

    &__title {
      margin: 0;
      font-size: 15px;
      font-weight: 700;
      color: var(--kp-text);
      line-height: 1.2;
    }

    &__sub {
      margin: 3px 0 0;
      font-size: 11px;
      color: var(--kp-text-muted);
      font-weight: 500;
    }

    &__arrow {
      display: flex;
      align-items: center;
      justify-content: center;
      
      .arrow-icon {
        color: var(--kp-text-muted);
        transition: transform 0.3s, color 0.3s;
      }
    }

    &.is-active {
      transform: translateY(0);
      background: rgba(255, 255, 255, 0.88);

      .provider-tab-card__arrow .arrow-icon {
        transform: rotate(180deg);
      }
    }

    &--openai.is-active {
      border-color: #10a37f;
      box-shadow: 0 8px 24px rgba(16, 163, 127, 0.08);
      .provider-tab-card__arrow .arrow-icon { color: #10a37f; }
      .provider-tab-card__title { color: #10a37f; }
    }

    &--anthropic.is-active {
      border-color: #d97757;
      box-shadow: 0 8px 24px rgba(217, 119, 87, 0.08);
      .provider-tab-card__arrow .arrow-icon { color: #d97757; }
      .provider-tab-card__title { color: #d97757; }
    }

    &--other.is-active {
      border-color: var(--kp-primary);
      box-shadow: 0 8px 24px rgba(91, 141, 239, 0.08);
      .provider-tab-card__arrow .arrow-icon { color: var(--kp-primary); }
      .provider-tab-card__title { color: var(--kp-primary); }
    }
  }

  .provider-logo-badge {
    width: 36px;
    height: 36px;
    border: 1px solid rgba(15, 23, 42, 0.1);
    border-radius: 8px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    background: #fff;

    img {
      width: 22px;
      height: 22px;
      object-fit: contain;
    }

    &--openai {
      background: #fff;
    }

    &--anthropic {
      background: #faf9f5;
      img { width: 25px; height: 25px; }
    }

    &--other {
      color: var(--kp-primary);
      background: rgba(91, 141, 239, 0.08);
    }

    .provider-logo-fallback {
      font-weight: 800;
      font-size: 14px;
    }
  }

  /* Provider details container */
  .active-provider-content {
    border: 1px solid rgba(15, 23, 42, 0.08);
    border-radius: var(--kp-radius);
    background: rgba(255, 255, 255, 0.65);
    backdrop-filter: blur(10px);
    box-shadow: 0 8px 32px rgba(31, 45, 80, 0.06);
    overflow: hidden;
    margin-bottom: 20px;

    &__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 16px 24px;
      background: linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(248, 250, 252, 0.4));
      border-bottom: 1px solid rgba(15, 23, 42, 0.05);
    }

    .header-title-area {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .brand-indicator {
      width: 4px;
      height: 16px;
      border-radius: 99px;
      background: var(--kp-primary);
      display: inline-block;
    }

    .panel-title {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: var(--kp-text);
    }

    .active-badge {
      font-size: 11px;
      padding: 2px 10px;
      border-radius: 99px;
      background: rgba(15, 23, 42, 0.05);
      color: var(--kp-text-secondary);
      font-weight: 500;
    }

    &--openai {
      border-top: 4px solid #10a37f;
      .brand-indicator { background-color: #10a37f; }
    }
    &--anthropic {
      border-top: 4px solid #d97757;
      .brand-indicator { background-color: #d97757; }
    }
    &--other {
      border-top: 4px solid var(--kp-primary);
      .brand-indicator { background-color: var(--kp-primary); }
    }
  }

  .model-family-section {
    padding: 16px 24px 20px;
    
    &:not(:last-child) {
      border-bottom: 1px solid rgba(15, 23, 42, 0.05);
    }

    &__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 12px;
    }

    &__title-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .bullet-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--kp-primary);
      display: inline-block;
    }

    .family-name {
      color: var(--kp-text);
      font-size: 15px;
      font-weight: 700;
      letter-spacing: 0.2px;
    }

    .family-badge {
      font-size: 12px;
      padding: 2px 10px;
      border-radius: 99px;
      background: rgba(15, 23, 42, 0.05);
      color: var(--kp-text-secondary);
      font-weight: 500;
    }
  }

  .active-provider-content--openai .bullet-dot { background-color: #10a37f; }
  .active-provider-content--anthropic .bullet-dot { background-color: #d97757; }
  .active-provider-content--other .bullet-dot { background-color: var(--kp-primary); }

  .table-wrapper {
    box-shadow: 0 2px 12px rgba(15, 23, 42, 0.02);
    border-radius: 8px;
    overflow: hidden;
  }

  /* Table styling */
  .model-table {
    --el-table-header-bg-color: rgba(248, 250, 252, 0.75);
    --el-table-row-hover-bg-color: rgba(91, 141, 239, 0.05);
    border: 1px solid rgba(15, 23, 42, 0.06);
    border-radius: 8px;
    overflow: hidden;
    background: transparent !important;
  }

  .model-id-cell {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }

  .mono-badge {
    padding: 2px 6px;
    border-radius: 4px;
    background: rgba(15, 23, 42, 0.05);
    color: #334155;
    font-size: 11px;
    font-family: ui-monospace, 'JetBrains Mono', 'SF Mono', Consolas, monospace;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    border: 1px solid rgba(15, 23, 42, 0.02);
    max-width: 120px; /* Neat small rectangle */
    display: inline-block;
    vertical-align: middle;

    &.mapped {
      background: rgba(179, 135, 255, 0.06);
      color: #7c3aed;
      border: 1px solid rgba(179, 135, 255, 0.1);
      max-width: 120px; /* Match standard size */
    }
  }

  .copy-btn {
    padding: 4px;
    height: auto;
    color: var(--kp-text-muted);
    opacity: 0;
    transition: opacity 0.2s, color 0.2s;

    &:hover {
      color: var(--kp-primary);
    }
  }

  .model-id-cell:hover .copy-btn {
    opacity: 1;
  }

  .direct-link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #36c08a;
    background: rgba(54, 192, 138, 0.06);
    padding: 2px 8px;
    border-radius: 6px;
    border: 1px solid rgba(54, 192, 138, 0.12);

    .direct-icon {
      flex-shrink: 0;
    }
  }

  .mapped-link {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;

    .map-arrow {
      color: var(--kp-text-muted);
      font-size: 11px;
      flex-shrink: 0;
    }
  }

  .custom-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 600;
    white-space: nowrap;
  }

  .tags-container {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
  }

  .pill-tag {
    display: inline-block;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 0.3px;
  }

  .token-value {
    font-weight: 600;
    color: var(--kp-text);
    font-size: 13px;
  }

  .empty-placeholder {
    color: var(--kp-text-muted);
    font-size: 13px;
    font-style: italic;
  }

  :deep(.el-table__header th) {
    color: var(--kp-text-secondary);
    font-weight: 600;
    font-size: 13px;
    border-bottom: 1px solid rgba(15, 23, 42, 0.08) !important;
  }

  :deep(.el-table__cell) {
    padding: 10px 0;
  }

  /* Page actions */
  .model-page {
    &__buttons {
      display: flex;
      gap: 10px;
      align-items: center;
    }
  }

  .action-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-weight: 500;
    
    .btn-icon {
      flex-shrink: 0;
    }
  }

  .spin-icon {
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  /* Transitions */
  .fade-slide-enter-active,
  .fade-slide-leave-active {
    transition: opacity 0.25s, transform 0.25s;
  }
  .fade-slide-enter-from {
    opacity: 0;
    transform: translateY(10px);
  }
  .fade-slide-leave-to {
    opacity: 0;
    transform: translateY(-10px);
  }

  @media (max-width: 960px) {
    .model-filter {
      flex-direction: column;
      align-items: stretch;
      gap: 12px;

      &__search-wrapper { width: 100%; }
      &__selects { width: 100%; justify-content: space-between; }
      
      .filter-group {
        flex: 1;
        &__label { display: none; }
        .model-filter__select { width: 100%; }
      }
    }
  }

  @media (max-width: 768px) {
    .model-summary-deck { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .provider-tabs { grid-template-columns: 1fr; }
    .active-provider-content__header { padding: 14px 16px; }
    .model-family-section { padding: 12px 16px; }
  }

  @media (max-width: 480px) {
    .model-summary-deck { grid-template-columns: 1fr; }
  }
</style>
