<template>
  <view class="stat-card">
    <view class="stat-icon" :class="'icon-' + color">
      <text>{{ icon }}</text>
    </view>
    <view class="stat-value">{{ value }}</view>
    <view class="stat-unit">{{ unit }}</view>
    <view v-if="trend" class="stat-trend" :class="trendDir === 'up' ? 'trend-up' : 'trend-down'">
      {{ trend > 0 ? '↑' : '↓' }}{{ Math.abs(trend) }}%
    </view>
  </view>
</template>

<script setup lang="ts">
interface Props {
  icon: string
  value: number | string
  unit: string
  trend?: number
  trendDir?: 'up' | 'down'
  color?: 'purple' | 'cyan' | 'amber' | 'green'
}

const props = withDefaults(defineProps<Props>(), {
  trend: undefined,
  trendDir: 'up',
  color: 'purple'
})
</script>

<style scoped lang="scss">
.stat-card {
  background: #fff;
  border-radius: var(--card-radius);
  padding: 16rpx;
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: transform 0.2s;

  &:active {
    transform: scale(0.95);
  }
}

.stat-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  margin-bottom: 12rpx;

  &.icon-purple { background: rgba(79, 70, 229, 0.1); color: #4f46e5; }
  &.icon-cyan { background: rgba(34, 211, 238, 0.1); color: #0891b2; }
  &.icon-amber { background: rgba(245, 158, 11, 0.1); color: #d97706; }
  &.icon-green { background: rgba(16, 185, 129, 0.1); color: #10b981; }
}

.stat-value {
  font-size: 28rpx;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1.3;
}

.stat-unit {
  font-size: 20rpx;
  color: var(--text-tertiary);
  margin-top: 4rpx;
}

.stat-trend {
  font-size: 18rpx;
  margin-top: 8rpx;
  font-weight: 600;

  &.trend-up { color: #10b981; }
  &.trend-down { color: #ef4444; }
}
</style>
