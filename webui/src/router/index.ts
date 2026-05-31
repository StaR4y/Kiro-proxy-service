/**
 * 路由配置。
 *
 * 模块化原则：
 * - 业务页面统一惰性加载（动态 import），首屏只装登录页与布局壳。
 * - 通过 meta.requiresAuth / meta.title 由全局守卫处理鉴权与文档标题。
 * - 路由名沿用 kebab 风格，便于在权限或菜单系统里二次扩展。
 */

import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', anonymous: true },
  },
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('@/views/auth/ChangePasswordView.vue'),
    meta: { title: '修改密码', requiresAuth: true, allowFirstLogin: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    meta: { requiresAuth: true },
    redirect: { name: 'dashboard' },
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'accounts',
        name: 'accounts',
        component: () => import('@/views/accounts/AccountListView.vue'),
        meta: { title: '账号管理' },
      },
      {
        path: 'api-keys',
        name: 'api-keys',
        component: () => import('@/views/api-keys/ApiKeyListView.vue'),
        meta: { title: 'API Key' },
      },
      {
        path: 'model-mappings',
        name: 'model-mappings',
        component: () => import('@/views/model-mappings/ModelMappingListView.vue'),
        meta: { title: '模型映射' },
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('@/views/logs/LogListView.vue'),
        meta: { title: '请求日志' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/users/UserListView.vue'),
        meta: { title: '管理员' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'dashboard' } },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth);
  const isAnonymous = to.meta.anonymous === true;

  if (requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }

  // 已登录用户访问登录页直接送回首页
  if (isAnonymous && auth.isAuthenticated && !auth.requirePasswordChange) {
    return { name: 'dashboard' };
  }

  // 首次登录强制改密
  if (
    auth.isAuthenticated &&
    auth.requirePasswordChange &&
    !to.meta.allowFirstLogin &&
    !isAnonymous
  ) {
    return { name: 'change-password' };
  }

  return true;
});

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) ?? '';
  document.title = title ? `${title} · Kiro Proxy` : 'Kiro Proxy 控制台';
});

export default router;
