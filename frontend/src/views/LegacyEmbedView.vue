<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import FpPageHeader from '@/components/ui/FpPageHeader.vue'

const route = useRoute()

const META: Record<string, { kicker: string; title: string; subtitle: string }> = {
  devices: {
    kicker: 'Fleet',
    title: '设备管理',
    subtitle: '温室节点编组 · 在线态势 · 部署与接入。',
  },
  'model-library': {
    kicker: 'Models',
    title: '模型库',
    subtitle: '版本链 · 训练血缘 · 可部署资产编目。',
  },
  'model-library-detail': {
    kicker: 'Models',
    title: '模型详情',
    subtitle: '文件结构 · 指标 · 来源数据集。',
  },
  datasets: {
    kicker: 'Datasets',
    title: '数据集',
    subtitle: '样本资产 · 类别分布 · 下游模型血缘。',
  },
  'dataset-detail': {
    kicker: 'Datasets',
    title: '数据集详情',
    subtitle: '文件树 · 预览 · 版本信息。',
  },
  'auth-manage': {
    kicker: 'Access',
    title: '权限管理',
    subtitle: '控制台账号 · 管理员开关 · 会话安全。',
  },
}

const pageMeta = computed(() => {
  const name = String(route.name || '')
  return (
    META[name] || {
      kicker: 'Resources',
      title: (route.meta.title as string) || '资源',
      subtitle: '系统资源工作区',
    }
  )
})

const frameSrc = computed(() => {
  const name = String(route.name || '')
  if (name === 'devices') return '/devices'
  if (name === 'model-library') return '/model_library'
  if (name === 'model-library-detail') return `/model_library/${route.params.id}`
  if (name === 'datasets') return '/datasets'
  if (name === 'dataset-detail') return `/datasets/${route.params.id}`
  if (name === 'auth-manage') return '/auth'
  return (route.meta.embedSrc as string) || '/devices'
})
</script>

<template>
  <div class="embed-page">
    <div class="embed-chrome">
      <FpPageHeader
        :kicker="pageMeta.kicker"
        :title="pageMeta.title"
        :subtitle="pageMeta.subtitle"
      />
    </div>
    <div class="embed-stage">
      <iframe
        :key="frameSrc"
        class="embed-frame"
        :src="frameSrc"
        title="workspace-content"
      />
    </div>
  </div>
</template>

<style scoped>
.embed-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--fp-top-h));
  min-height: 560px;
  padding: 0 28px 24px;
  box-sizing: border-box;
}
.embed-chrome {
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  flex-shrink: 0;
}
.embed-chrome :deep(.hdr) {
  padding-bottom: 16px;
  margin-bottom: 12px;
}
.embed-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  border: 1px solid var(--fp-line);
  border-radius: var(--fp-radius-xl);
  overflow: hidden;
  background: #fff;
  box-shadow: var(--fp-shadow);
}
.embed-frame {
  width: 100%;
  height: 100%;
  border: 0;
  background: #fff;
  display: block;
}
@media (max-width: 720px) {
  .embed-page { padding: 0 14px 16px; }
}
</style>
