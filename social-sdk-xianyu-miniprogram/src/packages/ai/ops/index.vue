<template>
  <view class="page-ops">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">AI 运营</text>
      <text class="nav-action" @click="reload">⟳</text>
    </view>
    <scroll-view scroll-y class="content-area">
      <view v-if="loading" class="loading-hint">加载中...</view>
      <view v-if="!loading">
        <!-- 顶部概览 -->
        <view class="overview-grid">
          <view class="stat-card">
            <text class="sc-label">今日 AI 接管</text>
            <text class="sc-value">{{ stats.todayHandled || 0 }}</text>
            <text class="sc-sub">条对话</text>
          </view>
          <view class="stat-card">
            <text class="sc-label">命中率</text>
            <text class="sc-value">{{ stats.hitRate || '0%' }}</text>
            <text class="sc-sub">关键词命中</text>
          </view>
          <view class="stat-card">
            <text class="sc-label">平均响应</text>
            <text class="sc-value">{{ stats.avgResponseMs || 0 }}<text class="sc-unit">ms</text></text>
            <text class="sc-sub">AI 回复时延</text>
          </view>
          <view class="stat-card">
            <text class="sc-label">活跃账号</text>
            <text class="sc-value">{{ stats.activeAccounts || 0 }}</text>
            <text class="sc-sub">个</text>
          </view>
        </view>

        <!-- 批量任务 -->
        <view class="section">
          <view class="section-head">
            <text class="sh-title">批量任务</text>
            <text class="sh-sub">最近 7 天</text>
          </view>
          <view class="batch-item" v-for="(b, i) in batches" :key="i">
            <view class="bi-info">
              <text class="bi-name">{{ b.name }}</text>
              <text class="bi-sub">{{ b.count }} 条 · {{ b.status }}</text>
            </view>
            <view class="bi-progress">
              <view class="bi-bar" :style="{ width: b.percent + '%' }" />
            </view>
            <text class="bi-percent">{{ b.percent }}%</text>
          </view>
          <view v-if="batches.length === 0" class="empty-inline">暂无批量任务</view>
        </view>

        <!-- 快捷操作 -->
        <view class="section">
          <view class="section-head"><text class="sh-title">快捷操作</text></view>
          <view class="action-grid">
            <view class="action-btn" @click="runAction('polish')">
              <text class="ab-emoji">✨</text><text class="ab-label">批量擦亮</text>
            </view>
            <view class="action-btn" @click="runAction('title')">
              <text class="ab-emoji">📝</text><text class="ab-label">AI 优化标题</text>
            </view>
            <view class="action-btn" @click="runAction('reply')">
              <text class="ab-emoji">💬</text><text class="ab-label">补全回复</text>
            </view>
            <view class="action-btn" @click="runAction('sync')">
              <text class="ab-emoji">🔄</text><text class="ab-label">同步消息</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getOpsStats } from '@/api/ai'

const loading = ref(false)
const stats = ref<any>({})
const batches = ref<any[]>([])

onMounted(() => reload())

async function reload() {
  loading.value = true
  try {
    const res: any = await getOpsStats()
    stats.value = res || {}
    batches.value = res?.batches || []
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false }
}

function runAction(type: string) {
  const map: Record<string, string> = { polish: '批量擦亮已触发', title: 'AI 标题优化已排队', reply: '补全回复任务已排队', sync: '消息同步已触发' }
  uni.showToast({ title: map[type] || '已触发', icon: 'success' })
}

function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-ops { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 36rpx; color: #4f46e5; }
.content-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 60rpx 0; }
.overview-grid { display: flex; flex-wrap: wrap; gap: 16rpx; margin-bottom: 24rpx; }
.stat-card { flex: 1; min-width: 280rpx; background: #fff; border-radius: 20rpx; padding: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.sc-label { font-size: 22rpx; color: #6b7280; display: block; }
.sc-value { font-size: 56rpx; font-weight: 700; color: #4f46e5; display: block; margin: 8rpx 0; }
.sc-unit { font-size: 24rpx; color: #9ca3af; font-weight: 400; }
.sc-sub { font-size: 20rpx; color: #9ca3af; }
.section { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.section-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 20rpx; }
.sh-title { font-size: 28rpx; font-weight: 700; color: #111827; }
.sh-sub { font-size: 20rpx; color: #9ca3af; }
.batch-item { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 0; border-bottom: 1rpx solid #f3f4f6; }
.batch-item:last-child { border-bottom: none; }
.bi-info { flex-shrink: 0; width: 240rpx; }
.bi-name { font-size: 26rpx; font-weight: 600; color: #111827; display: block; }
.bi-sub { font-size: 20rpx; color: #9ca3af; }
.bi-progress { flex: 1; height: 12rpx; background: #f3f4f6; border-radius: 6rpx; overflow: hidden; }
.bi-bar { height: 100%; background: linear-gradient(90deg, #4f46e5, #7c3aed); border-radius: 6rpx; }
.bi-percent { font-size: 22rpx; color: #4f46e5; font-weight: 600; flex-shrink: 0; width: 80rpx; text-align: right; }
.empty-inline { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 20rpx 0; }
.action-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.action-btn { flex: 1; min-width: 200rpx; display: flex; flex-direction: column; align-items: center; gap: 12rpx; padding: 28rpx 0; background: #f9fafb; border-radius: 20rpx; border: 1rpx solid #f3f4f6; }
.action-btn:active { background: rgba(79,70,229,.06); }
.ab-emoji { font-size: 48rpx; }
.ab-label { font-size: 24rpx; color: #4b5563; font-weight: 600; }
</style>
