<template>
  <view class="loading-spinner" :class="size" v-if="visible">
    <view class="ls-ring" />
    <text v-if="label" class="ls-label">{{ label }}</text>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  visible?: boolean
  size?: 'small' | 'medium' | 'large'
  label?: string
}>(), {
  visible: true,
  size: 'medium',
  label: '',
})
</script>

<style scoped lang="scss">
.loading-spinner { display: inline-flex; align-items: center; gap: 16rpx; }
.ls-ring { width: 48rpx; height: 48rpx; border-radius: 50%; border: 4rpx solid rgba(79,70,229,.2); border-top-color: #4f46e5; animation: ls-spin .8s linear infinite; }
.loading-spinner.small .ls-ring { width: 32rpx; height: 32rpx; border-width: 3rpx; }
.loading-spinner.large .ls-ring { width: 72rpx; height: 72rpx; border-width: 6rpx; }
.ls-label { font-size: 24rpx; color: #9ca3af; }

@keyframes ls-spin { to { transform: rotate(360deg); } }
</style>
