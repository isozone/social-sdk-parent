<template>
  <view class="empty-state">
    <text class="empty-icon">{{ icon || '📭' }}</text>
    <text class="empty-text">{{ text || '暂无数据' }}</text>
    <button v-if="actionText" class="empty-action" @click="$emit('action')">{{ actionText }}</button>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  text?: string
  actionText?: string
  icon?: string
}>(), {
  text: '暂无数据',
  actionText: '',
  icon: ''
})

defineEmits<{
  action: []
}>()
</script>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 48rpx;
}

.empty-icon {
  font-size: 96rpx;
  margin-bottom: 24rpx;
  opacity: 0.5;
}

.empty-text {
  font-size: 28rpx;
  color: var(--text-tertiary);
  line-height: 1.6;
  text-align: center;
}

.empty-action {
  margin-top: 32rpx;
  background: var(--brand-gradient);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: 0 48rpx;
  height: 72rpx;
  font-size: 26rpx;
  font-weight: 500;
}
</style>
