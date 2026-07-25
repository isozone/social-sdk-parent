<template>
  <view class="page-header" :style="{ backgroundImage: headerGradient }">
    <view class="hero-row">
      <view class="avatar" :style="{ background: avatarGradient }">
        <text>{{ initial }}</text>
      </view>
      <view class="greet-wrap">
        <text v-if="greeting" class="greet">{{ greeting }}</text>
        <text class="name">{{ name }}</text>
        <text v-if="subTitle" class="sub-title">{{ subTitle }}</text>
      </view>
      <view class="header-actions">
        <slot name="actions"></slot>
      </view>
    </view>
    <view class="header-decoration"></view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  title?: string
  greeting?: string
  subtitle?: string
  name?: string
  avatar?: string
}

const props = withDefaults(defineProps<Props>(), {
  greeting: '',
  subtitle: '',
  name: 'AI 鱼多宝',
  avatar: ''
})

const initial = computed(() => {
  return props.avatar || '🐟'
})

const headerGradient = computed(() => {
  return 'linear-gradient(135deg, #4f46e5, #7c3aed)'
})

const avatarGradient = computed(() => {
  return 'rgba(255, 255, 255, 0.18)'
})
</script>

<style scoped lang="scss">
.page-header {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  padding: 44rpx 40rpx 64rpx;
  border-radius: 0 0 56rpx 56rpx;
  position: relative;
  overflow: hidden;
  color: #fff;
}

.hero-row {
  display: flex;
  align-items: center;
  gap: 28rpx;
  position: relative;
  z-index: 1;
}

.avatar {
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  flex-shrink: 0;
}

.greet-wrap {
  flex: 1;
  min-width: 0;
}

.greet {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
  display: block;
}

.name {
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
  margin-top: 4rpx;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
  display: block;
}

.sub-title {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.65);
  margin-top: 8rpx;
  display: block;
}

.header-actions {
  display: flex;
  gap: 16rpx;
}

.header-decoration {
  position: absolute;
  top: -40%;
  right: -15%;
  width: 440rpx;
  height: 440rpx;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.2), transparent 70%);
  border-radius: 50%;
  z-index: 0;
}
</style>
