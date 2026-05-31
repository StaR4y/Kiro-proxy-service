<script setup lang="ts">
  /**
   * 登录页。
   *
   * 流程：
   * 1. 表单提交后调用 authApi.login，结果写入 auth store。
   * 2. 若返回 firstLogin=true，跳转 /change-password。
   * 3. 否则跳回 redirect query 或 dashboard。
   */
  import { reactive, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
  import { useAuthStore } from '@/stores/auth';

  const route = useRoute();
  const router = useRouter();
  const auth = useAuthStore();

  const formRef = ref<FormInstance>();
  const loading = ref(false);

  const form = reactive({
    username: 'admin',
    password: '',
  });

  const rules: FormRules = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  };

  async function submit() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;

    loading.value = true;
    try {
      const result = await auth.login(form);
      ElMessage.success('登录成功');

      if (result.user.firstLogin) {
        router.replace({ name: 'change-password' });
        return;
      }

      const redirect = (route.query.redirect as string) || '/';
      router.replace(redirect);
    } catch (err) {
      // 全局拦截器已对非静默接口提示，这里登录页 silent，所以补一次
      ElMessage.error((err as Error).message || '登录失败');
    } finally {
      loading.value = false;
    }
  }
</script>

<template>
  <div class="login">
    <div class="login__panel glass-panel glass-panel--strong">
      <div class="login__header">
        <span class="login__brand">⌁ Kiro Proxy</span>
        <h1 class="login__title">控制台登录</h1>
        <p class="login__hint text-secondary">
          首次部署后请使用 <span class="mono">admin</span> 和启动日志中的随机密码登录
        </p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="login__submit"
          :loading="loading"
          size="large"
          @click="submit"
        >
          登录
        </el-button>
      </el-form>
    </div>
    <div class="login__bg-orb login__bg-orb--a" />
    <div class="login__bg-orb login__bg-orb--b" />
  </div>
</template>

<style scoped lang="scss">
  .login {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    position: relative;
    overflow: hidden;

    &__panel {
      position: relative;
      width: 100%;
      max-width: 420px;
      padding: 40px 36px;
      z-index: 1;
    }

    &__header {
      margin-bottom: 24px;
    }

    &__brand {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 999px;
      font-weight: 600;
      background: rgba(91, 141, 239, 0.12);
      color: var(--kp-primary);
      font-size: 13px;
    }

    &__title {
      margin: 14px 0 6px;
      font-size: 26px;
      letter-spacing: 0.5px;
    }

    &__hint {
      margin: 0;
      font-size: 13px;
    }

    &__submit {
      width: 100%;
      margin-top: 6px;
    }

    /* 背景装饰圆，强化磨砂玻璃透光效果 */
    &__bg-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(60px);
      opacity: 0.7;
      pointer-events: none;

      &--a {
        width: 360px;
        height: 360px;
        background: radial-gradient(circle, #b9d0ff 0%, transparent 70%);
        top: 8%;
        left: 12%;
      }
      &--b {
        width: 420px;
        height: 420px;
        background: radial-gradient(circle, #e1c4ff 0%, transparent 70%);
        bottom: 6%;
        right: 8%;
      }
    }
  }
</style>
