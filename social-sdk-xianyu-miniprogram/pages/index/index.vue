<template>
  <view class="page-dashboard">
    <view class="hero-section">
      <view class="hero-content">
        <view class="avatar-circle">🐟</view>
        <view class="greet-text">下午好，{{ auth.profile?.displayName || '用户' }}</view>
        <view class="name-text">AI 鱼多宝</view>
        <view class="msg-text">今日经营数据持续向好 · {{ dashboard.accountStats?.length || 0 }} 个账号运行中</view>
      </view>
    </view>

    <scroll-view scroll-y class="main-scroll" @scrolltolower="onLoadMore" @refresherpulling="onPullRefresh" :refresher-enabled="true" :refresher-triggered="loading">
      <view class="kpi-grid">
        <view class="kpi-card" v-for="(kpi, idx) in dashboard.kpis" :key="idx">
          <view class="kpi-icon" :class="'icon-' + idx">
            <text>{{ kpi.icon || '📊' }}</text>
          </view>
          <view class="kpi-value">{{ kpi.value || 0 }}</view>
          <view class="kpi-unit">{{ kpi.unit || '' }}</view>
          <view class="kpi-trend" :class="kpi.trendDirection === 'up' ? 'trend-up' : 'trend-down'">
            {{ kpi.trend > 0 ? '↑' : '↓' }}{{ Math.abs(kpi.trend) || 0 }}%
          </view>
        </view>
      </view>

      <view class="section-header">
        <view>
          <text class="section-title">近 7 日成交趋势</text>
          <text class="section-subtitle">单位：元</text>
        </view>
        <view class="tabs-row">
          <view class="tab-item" :class="{ active: currentTab === 'week' }" @click="currentTab = 'week'">周</view>
          <view class="tab-item" :class="{ active: currentTab === 'month' }" @click="currentTab = 'month'">月</view>
          <view class="tab-item" :class="{ active: currentTab === 'year' }" @click="currentTab = 'year'">年</view>
        </view>
      </view>

      <view class="trend-chart">
        <view class="chart-bar-wrap">
          <view class="chart-bar" v-for="(item, idx) in dashboard.orderTrend" :key="idx" :style="{ height: Math.min(100, (item.value / maxTrend) * 100) + '%' }"></view>
        </view>
        <view class="chart-labels">
          <text v-for="(item, idx) in dashboard.orderTrend" :key="idx" class="chart-label">{{ item.date }}</text>
        </view>
      </view>

      <view class="section-header">
        <view>
          <text class="section-title">账号健康</text>
          <text class="section-subtitle">当前 {{ dashboard.accountStats?.length || 0 }} 个账号运行中</text>
        </view>
        <navigator url="/pages/accounts/list" class="section-link">管理 ></navigator>
      </view>

      <view class="account-list">
        <view class="account-card" v-for="acc in dashboard.accountStats" :key="acc.accountId">
          <view class="account-icon" :style="{ background: acc.status === 'ACTIVE' ? 'linear-gradient(135deg,#4f46e5,#7c3aed)' : 'linear-gradient(135deg,#f59e0b,#ef4444)' }">
            <text>{{ acc.status === 'ACTIVE' ? '🚀' : '⚡' }}</text>
          </view>
          <view class="account-info">
            <text class="account-name">{{ acc.displayName }}</text>
            <text class="account-desc">{{ acc.status === 'ACTIVE' ? '在线 · 自动回复已开启' : '状态异常' }}</text>
          </view>
          <view class="account-status">
            <text class="score">{{ acc.orderCount }} 单</text>
            <text class="tag" :class="acc.status === 'ACTIVE' ? 'tag-success' : 'tag-warning'">{{ acc.status === 'ACTIVE' ? '健康' : '注意' }}</text>
          </view>
        </view>
      </view>

      <view class="section-header">
        <text class="section-title">快捷操作</text>
      </view>

      <view class="quick-actions">
        <navigator url="/pages/products/list" class="action-item">
          <view class="action-icon icon-purple">🔄</view>
          <text class="action-label">同步商品</text>
        </navigator>
        <navigator url="/pages/products/list" class="action-item">
          <view class="action-icon icon-green">✨</view>
          <text class="action-label">批量擦亮</text>
        </navigator>
        <navigator url="/packages/products/publish/index" class="action-item">
          <view class="action-icon icon-amber">🛒</view>
          <text class="action-label">发新商品</text>
        </navigator>
        <navigator url="/pages/messages/list" class="action-item">
          <view class="action-icon icon-cyan">🔔</view>
          <text class="action-label">同步消息</text>
        </navigator>
      </view>

      <view style="height: 40rpx;"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/store/modules/auth'
import { useDashboardStore } from '@/store/modules/dashboard'

const auth = useAuthStore()
const dashboard = useDashboardStore()
const currentTab = ref('month')
const loading = ref(false)

function onPullRefresh() {
  dashboard.refresh().then(() => {})
}

function onLoadMore() {}

const maxTrend = computed(() => {
  const vals = dashboard.orderTrend.map(t => t.value)
  return Math.max(...vals, 1)
})
</script>

<style scoped lang="scss">
.page-dashboard { min-height: 100vh; background: var(--bg-page); }

.hero-section {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  padding: 60rpx 40rpx 80rpx;
  border-radius: 0 0 56rpx 56rpx;
  position: relative;
  overflow: hidden;
}

.hero-section::before {
  content: '';
  position: absolute;
  top: -40%;
  right: -15%;
  width: 440rpx;
  height: 440rpx;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.2), transparent 70%);
  border-radius: 50%;
}

.hero-content { position: relative; z-index: 1; }

.avatar-circle {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  margin-bottom: 24rpx;
}

.greet-text { font-size: 26rpx; color: rgba(255,255,255,0.8); }
.name-text { font-size: 40rpx; font-weight: 700; color: #fff; text-shadow: 0 2rpx 8rpx rgba(0,0,0,0.15); }
.msg-text { font-size: 24rpx; color: rgba(255,255,255,0.65); margin-top: 8rpx; }

.main-scroll { height: calc(100vh - 280rpx); padding: 24rpx; }

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.kpi-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 28rpx 20rpx;
  border: 1rpx solid var(--border-color);
  box-shadow: var(--shadow-sm);
  text-align: center;
}

.kpi-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  margin: 0 auto 12rpx;
}

.kpi-icon-0 { background: rgba(79,70,229,0.1); color: #4f46e5; }
.kpi-icon-1 { background: rgba(34,211,238,0.1); color: #0891b2; }
.kpi-icon-2 { background: rgba(245,158,11,0.1); color: #d97706; }
.kpi-icon-3 { background: rgba(16,185,129,0.1); color: #10b981; }

.kpi-value { font-size: 32rpx; font-weight: 800; color: var(--text-primary); line-height: 1.3; }
.kpi-unit { font-size: 22rpx; color: var(--text-tertiary); }
.kpi-trend { font-size: 20rpx; margin-top: 8rpx; font-weight: 600; }
.trend-up { color: #10b981; }
.trend-down { color: #ef4444; }

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0 12rpx;
}

.section-title { font-size: 32rpx; font-weight: 700; color: var(--text-primary); }
.section-subtitle { font-size: 22rpx; color: var(--text-tertiary); margin-top: 4rpx; display: block; }
.section-link { font-size: 24rpx; color: var(--brand); font-weight: 600; }

.tabs-row {
  display: flex;
  gap: 8rpx;
  background: var(--bg-input);
  border-radius: 16rpx;
  padding: 4rpx;
}

.tab-item {
  padding: 8rpx 24rpx;
  border-radius: 12rpx;
  font-size: 22rpx;
  color: var(--text-secondary);
  transition: all 0.2s;
  &.active { background: #fff; color: var(--brand); font-weight: 600; box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.08); }
}

.trend-chart {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  border: 1rpx solid var(--border-color);
  margin-bottom: 24rpx;
}

.chart-bar-wrap {
  display: flex;
  align-items: flex-end;
  gap: 12rpx;
  height: 200rpx;
}

.chart-bar {
  flex: 1;
  min-height: 8rpx;
  background: linear-gradient(to top, #4f46e5, #7c3aed);
  border-radius: 6rpx;
  transition: height 0.6s cubic-bezier(0.16,1,0.3,1);
}

.chart-labels {
  display: flex;
  gap: 12rpx;
  margin-top: 12rpx;
}

.chart-label { flex: 1; text-align: center; font-size: 20rpx; color: var(--text-tertiary); }

.account-list { display: flex; flex-direction: column; gap: 16rpx; margin-bottom: 24rpx; }

.account-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  border: 1rpx solid var(--border-color);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.account-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 6rpx 20rpx rgba(0,0,0,0.1);
}

.account-info { flex: 1; min-width: 0; }
.account-name { font-size: 28rpx; font-weight: 700; color: var(--text-primary); display: block; }
.account-desc { font-size: 22rpx; color: var(--text-tertiary); margin-top: 4rpx; display: block; }

.account-status { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; }
.score { font-size: 28rpx; font-weight: 800; color: var(--success); }
.tag { padding: 4rpx 12rpx; border-radius: 8rpx; font-size: 20rpx; font-weight: 700; }
.tag-success { background: rgba(16,185,129,0.1); color: #10b981; }
.tag-warning { background: rgba(245,158,11,0.1); color: #f59e0b; }

.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  &:active .action-icon { transform: scale(0.9); }
}

.action-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.06);
  transition: transform 0.2s;
  &.icon-purple { background: linear-gradient(135deg, rgba(79,70,229,0.12), rgba(124,58,237,0.06)); }
  &.icon-green { background: linear-gradient(135deg, rgba(16,185,129,0.12), rgba(16,185,129,0.06)); }
  &.icon-amber { background: linear-gradient(135deg, rgba(245,158,11,0.12), rgba(245,158,11,0.06)); }
  &.icon-cyan { background: linear-gradient(135deg, rgba(34,211,238,0.12), rgba(34,211,238,0.06)); }
}

.action-label { font-size: 22rpx; color: var(--text-secondary); font-weight: 500; }
</style>
