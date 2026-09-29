import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory('/app/'),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppShell.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '运行看板' },
        },
        {
          path: 'control/led',
          name: 'control-led',
          component: () => import('@/views/control/LedView.vue'),
          meta: { title: 'LED 补光' },
        },
        {
          path: 'control/actuators',
          name: 'control-actuators',
          component: () => import('@/views/control/ActuatorsView.vue'),
          meta: { title: '通风供水与自动化' },
        },
        {
          path: 'control/camera',
          name: 'control-camera',
          component: () => import('@/views/control/CameraView.vue'),
          meta: { title: '云台相机' },
        },
        {
          path: 'control/logs',
          name: 'control-logs',
          component: () => import('@/views/control/LogsView.vue'),
          meta: { title: '指令日志' },
        },
        {
          path: 'phenotype/digital',
          name: 'phenotype-digital',
          component: () => import('@/views/phenotype/DigitalView.vue'),
          meta: { title: '数字化植株' },
        },
        {
          path: 'phenotype/metrics',
          name: 'phenotype-metrics',
          component: () => import('@/views/phenotype/MetricsView.vue'),
          meta: { title: '表型指标' },
        },
        {
          path: 'phenotype/media',
          name: 'phenotype-media',
          component: () => import('@/views/phenotype/MediaView.vue'),
          meta: { title: '多模态影像' },
        },
        {
          path: 'phenotype/jobs',
          name: 'phenotype-jobs',
          component: () => import('@/views/phenotype/JobsView.vue'),
          meta: { title: '分析任务' },
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('@/views/HistoryView.vue'),
          meta: { title: '环境历史' },
        },
        {
          path: 'data',
          name: 'data',
          component: () => import('@/views/DataView.vue'),
          meta: { title: '数据导出' },
        },
        {
          path: 'devices',
          name: 'devices',
          component: () => import('@/views/LegacyEmbedView.vue'),
          meta: { title: '设备管理' },
        },
        {
          path: 'model-library',
          name: 'model-library',
          component: () => import('@/views/LegacyEmbedView.vue'),
          meta: { title: '模型库' },
        },
        {
          path: 'model-library/:id',
          name: 'model-library-detail',
          component: () => import('@/views/LegacyEmbedView.vue'),
          meta: { title: '模型详情' },
        },
        {
          path: 'datasets',
          name: 'datasets',
          component: () => import('@/views/LegacyEmbedView.vue'),
          meta: { title: '数据集' },
        },
        {
          path: 'datasets/:id',
          name: 'dataset-detail',
          component: () => import('@/views/LegacyEmbedView.vue'),
          meta: { title: '数据集详情' },
        },
        {
          path: 'auth-manage',
          name: 'auth-manage',
          component: () => import('@/views/LegacyEmbedView.vue'),
          meta: { title: '权限管理' },
        },
      ],
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
