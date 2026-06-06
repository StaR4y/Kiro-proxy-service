<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { ElMessage } from 'element-plus';
  import PageSection from '@/components/PageSection.vue';
  import { systemApi } from '@/api';
  import type { ModelInfo } from '@/types/api';

  interface FamilyGroup {
    name: string;
    models: ModelInfo[];
  }

  interface ProviderGroup {
    provider: string;
    families: FamilyGroup[];
    models: ModelInfo[];
  }

  const loading = ref(false);
  const models = ref<ModelInfo[]>([]);
  const expanded = reactive<Record<string, boolean>>({});

  const providerOrder = ['Anthropic', 'OpenAI'];
  const providerMeta: Record<string, { label: string; mark: string; tone: string }> = {
    Anthropic: { label: 'Anthropic', mark: 'A', tone: 'anthropic' },
    OpenAI: { label: 'OpenAI', mark: 'O', tone: 'openai' },
  };

  const groupedModels = computed<ProviderGroup[]>(() => {
    const providerMap = new Map<string, ModelInfo[]>();
    for (const model of models.value) {
      const provider = model.provider || model.owned_by || 'Other';
      providerMap.set(provider, [...(providerMap.get(provider) ?? []), model]);
    }

    return [...providerMap.entries()]
      .sort(([a], [b]) => providerRank(a) - providerRank(b) || a.localeCompare(b))
      .map(([provider, providerModels]) => ({
        provider,
        models: providerModels,
        families: buildFamilies(providerModels),
      }));
  });

  function providerRank(provider: string) {
    const idx = providerOrder.indexOf(provider);
    return idx === -1 ? 99 : idx;
  }

  function buildFamilies(items: ModelInfo[]) {
    const familyMap = new Map<string, ModelInfo[]>();
    for (const item of items) {
      const family = item.family || 'Other';
      familyMap.set(family, [...(familyMap.get(family) ?? []), item]);
    }
    return [...familyMap.entries()].map(([name, familyModels]) => ({ name, models: familyModels }));
  }

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

  function toggleProvider(provider: string) {
    expanded[provider] = !expanded[provider];
  }

  function isExpanded(provider: string) {
    return expanded[provider] === true;
  }

  function metaFor(provider: string) {
    return providerMeta[provider] ?? { label: provider, mark: provider.charAt(0).toUpperCase(), tone: 'other' };
  }

  onMounted(loadModels);
</script>

<template>
  <PageSection title="模型 ID" subtitle="按厂商和系列查看当前可用模型">
    <template #actions>
      <el-button :loading="loading" @click="loadModels">刷新</el-button>
    </template>

    <div v-loading="loading" class="model-catalog">
      <section v-for="group in groupedModels" :key="group.provider" class="provider-panel">
        <button class="provider-head" type="button" @click="toggleProvider(group.provider)">
          <span class="provider-head__brand">
            <span class="provider-logo" :class="`provider-logo--${metaFor(group.provider).tone}`">
              {{ metaFor(group.provider).mark }}
            </span>
            <span class="provider-head__name">{{ metaFor(group.provider).label }}</span>
          </span>
          <span class="provider-head__right">
            <span class="provider-head__count">{{ group.models.length }} models</span>
            <span class="provider-head__chevron" :class="{ 'is-open': isExpanded(group.provider) }">⌄</span>
          </span>
        </button>

        <div v-if="isExpanded(group.provider)" class="provider-body">
          <section v-for="family in group.families" :key="family.name" class="family-block">
            <header class="family-block__head">
              <span>{{ family.name }}</span>
              <span>{{ family.models.length }}</span>
            </header>
            <div class="model-grid">
              <article v-for="model in family.models" :key="model.id" class="model-card">
                <div class="model-card__id mono">{{ model.id }}</div>
                <div class="model-card__meta">
                  <span>{{ model.targetModelId || model.id }}</span>
                  <span>{{ model.maxInputTokens || model.context_length || '-' }}</span>
                </div>
              </article>
            </div>
          </section>
        </div>
      </section>

      <el-empty v-if="!loading && !groupedModels.length" description="暂无模型" />
    </div>
  </PageSection>
</template>

<style scoped lang="scss">
  .model-catalog {
    display: grid;
    gap: 14px;
    min-height: 160px;
  }

  .provider-panel {
    overflow: hidden;
    border: 1px solid rgba(91, 141, 239, 0.16);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.56);
  }

  .provider-head {
    width: 100%;
    min-height: 64px;
    padding: 14px 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    border: 0;
    background: transparent;
    color: var(--kp-text);
    cursor: pointer;
    text-align: left;

    &:hover {
      background: rgba(91, 141, 239, 0.06);
    }

    &__brand,
    &__right {
      display: inline-flex;
      align-items: center;
      gap: 12px;
    }

    &__name {
      font-size: 16px;
      font-weight: 700;
    }

    &__count {
      color: var(--kp-text-secondary);
      font-size: 13px;
      white-space: nowrap;
    }

    &__chevron {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 8px;
      background: rgba(15, 23, 42, 0.06);
      transition: transform 0.18s ease;

      &.is-open {
        transform: rotate(180deg);
      }
    }
  }

  .provider-logo {
    display: inline-flex;
    width: 36px;
    height: 36px;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    color: #fff;
    font-weight: 800;
    letter-spacing: 0;

    &--anthropic {
      background: #191919;
    }

    &--openai {
      background: #0f7f68;
    }

    &--other {
      background: var(--kp-primary);
    }
  }

  .provider-body {
    display: grid;
    gap: 14px;
    padding: 0 16px 16px;
  }

  .family-block {
    display: grid;
    gap: 10px;

    &__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      color: var(--kp-text-secondary);
      font-size: 13px;
      font-weight: 600;
    }
  }

  .model-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 10px;
  }

  .model-card {
    min-height: 72px;
    padding: 12px;
    border: 1px solid rgba(15, 23, 42, 0.08);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.72);

    &__id {
      color: var(--kp-text);
      font-size: 13px;
      font-weight: 700;
      overflow-wrap: anywhere;
    }

    &__meta {
      margin-top: 8px;
      display: flex;
      justify-content: space-between;
      gap: 10px;
      color: var(--kp-text-muted);
      font-size: 12px;

      span {
        min-width: 0;
        overflow-wrap: anywhere;
      }
    }
  }

  @media (max-width: 720px) {
    .provider-head {
      align-items: flex-start;
      flex-direction: column;
    }

    .model-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
