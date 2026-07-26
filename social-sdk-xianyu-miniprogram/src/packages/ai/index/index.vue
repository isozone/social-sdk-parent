<template>
  <view class="page-ai-center">
    <view class="ai-hero">
      <view class="nav-row">
        <text class="nav-back" @click="goBack">‹</text>
        <text class="nav-title">AI 管理中心</text>
        <text class="nav-space" />
      </view>
      <view class="hero-icon">🤖</view>
      <text class="hero-title">AI 管理中心</text>
      <text class="hero-sub">智能助手 · 自动客服 · 运营加速</text>
    </view>

    <view class="stats-card">
      <view class="stat-cell">
        <text class="stat-val">{{ stats.providers }}</text>
        <text class="stat-label">AI 厂商</text>
      </view>
      <view class="stat-cell">
        <text class="stat-val">{{ stats.models }}</text>
        <text class="stat-label">可用模型</text>
      </view>
      <view class="stat-cell">
        <text class="stat-val">{{ stats.calls }}</text>
        <text class="stat-label">本月调用</text>
      </view>
    </view>

    <view class="section-head">
      <text class="section-title">AI 功能中心</text>
    </view>

    <view class="ai-grid">
      <view class="ai-card" v-for="item in entries" :key="item.path" @click="go(item.path)">
        <view class="ai-card-icon" :class="item.iconClass">{{ item.icon }}</view>
        <text class="ai-card-title">{{ item.title }}</text>
        <text class="ai-card-desc">{{ item.desc }}</text>
        <text class="ai-badge" :class="item.badgeClass">{{ item.badge }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getOpsStats, getProviders } from '@/api/ai'

const stats = ref({ providers: 0, models: 0, calls: '—' })

const entries = [
  { title: 'AI 对话测试', desc: '快速测试模型效果，调整回复策略', icon: '💬', iconClass: 'chat', badge: '推荐', badgeClass: 'new', path: '/packages/ai/chat/index' },
  { title: 'AI 客服', desc: '知识库管理、议价记录、会话追踪', icon: '🎧', iconClass: 'cs', badge: 'P2', badgeClass: 'default', path: '/packages/ai/cs/index' },
  { title: 'AI 运营', desc: '批量上品、运营周报、多账号同步', icon: '📈', iconClass: 'ops', badge: '热门', badgeClass: 'hot', path: '/packages/ai/ops/index' },
  { title: 'AI 厂商/模型', desc: '厂商接入、模型启用、默认模型', icon: '⚙️', iconClass: 'model', badge: '配置', badgeClass: 'default', path: '/packages/ai/providers/index' },
]

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/profile/index' }) })
}

function go(path: string) {
  uni.navigateTo({ url: path })
}

onMounted(async () => {
  try {
    const providers: any = await getProviders()
    const list = Array.isArray(providers) ? providers : (providers?.records || [])
    stats.value.providers = list.length
    stats.value.models = list.reduce((sum: number, p: any) => sum + (p.models?.length || p.modelCount || 0), 0)
  } catch {}
  try {
    const ops: any = await getOpsStats()
    if (ops?.monthCalls != null) stats.value.calls = String(ops.monthCalls)
    else if (ops?.total != null) stats.value.calls = String(ops.total)
  } catch {}
})
</script>

<style scoped lang="scss">
.page-ai-center { min-height: 100vh; background: #f5f5f7; }
.ai-hero {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 50%, #22d3ee 100%);
  padding: 24rpx 32rpx 48rpx;
  border-radius: 0 0 48rpx 48rpx;
  color: #fff;
  position: relative;
  overflow: hidden;
}
.nav-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.nav-back { font-size: 48rpx; line-height: 1; width: 48rpx; }
.nav-title { font-size: 32rpx; font-weight: 700; }
.nav-space { width: 48rpx; }
.hero-icon { font-size: 72rpx; text-align: center; margin-bottom: 12rpx; }
.hero-title { display: block; text-align: center; font-size: 40rpx; font-weight: 800; }
.hero-sub { display: block; text-align: center; font-size: 22rpx; opacity: .75; margin-top: 8rpx; }

.stats-card {
  margin: -28rpx 32rpx 24rpx;
  background: #fff;
  border-radius: 28rpx;
  border: 1rpx solid #e5e7eb;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,.04);
  display: flex;
  padding: 28rpx 0;
}
.stat-cell { flex: 1; text-align: center; }
.stat-val { display: block; font-size: 40rpx; font-weight: 800; color: #4f46e5; }
.stat-label { display: block; font-size: 20rpx; color: #9ca3af; margin-top: 6rpx; }

.section-head { padding: 8rpx 32rpx 16rpx; }
.section-title { font-size: 30rpx; font-weight: 700; color: #111827; }

.ai-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; padding: 0 32rpx 48rpx; }
.ai-card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx 20rpx;
  text-align: center;
  border: 1rpx solid #e5e7eb;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,.03);
}
.ai-card:active { transform: scale(.97); }
.ai-card-icon {
  width: 88rpx; height: 88rpx; border-radius: 28rpx; margin: 0 auto 16rpx;
  display: flex; align-items: center; justify-content: center; font-size: 40rpx; color: #fff;
}
.ai-card-icon.chat { background: linear-gradient(135deg, #4f46e5, #7c3aed); }
.ai-card-icon.cs { background: linear-gradient(135deg, #06b6d4, #22d3ee); }
.ai-card-icon.ops { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.ai-card-icon.model { background: linear-gradient(135deg, #10b981, #34d399); }
.ai-card-title { display: block; font-size: 28rpx; font-weight: 700; color: #111827; }
.ai-card-desc { display: block; font-size: 20rpx; color: #9ca3af; margin-top: 8rpx; line-height: 1.4; }
.ai-badge {
  display: inline-block; margin-top: 14rpx; padding: 4rpx 16rpx; border-radius: 16rpx;
  font-size: 18rpx; font-weight: 600;
}
.ai-badge.new { background: rgba(16,185,129,.1); color: #10b981; }
.ai-badge.hot { background: rgba(239,68,68,.1); color: #ef4444; }
.ai-badge.default { background: rgba(124,58,237,.08); color: #7c3aed; }
</style>
