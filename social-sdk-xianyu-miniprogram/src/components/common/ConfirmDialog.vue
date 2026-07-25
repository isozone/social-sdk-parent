<template>
  <view v-if="visible" class="confirm-mask" @click="$emit('cancel')">
    <view class="confirm-dialog" @click.stop>
      <text class="confirm-title">{{ title }}</text>
      <text class="confirm-content">{{ content }}</text>
      <view class="confirm-actions">
        <button class="btn-cancel" @click="$emit('cancel')">{{ cancelText }}</button>
        <button class="btn-confirm" :style="{ background: confirmColor }" @click="$emit('confirm')">{{ confirmText }}</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  visible: boolean
  title: string
  content: string
  confirmText?: string
  cancelText?: string
  confirmColor?: string
}>(), {
  confirmText: '确认',
  cancelText: '取消',
  confirmColor: '#4f46e5'
})

defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<style scoped lang="scss">
.confirm-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.confirm-dialog {
  width: 600rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx;
}

.confirm-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text-primary);
  display: block;
  margin-bottom: 16rpx;
}

.confirm-content {
  font-size: 28rpx;
  color: var(--text-secondary);
  display: block;
  margin-bottom: 32rpx;
  line-height: 1.6;
}

.confirm-actions {
  display: flex;
  gap: 24rpx;
}

.btn-cancel, .btn-confirm {
  flex: 1;
  height: 72rpx;
  border-radius: 16rpx;
  font-size: 28rpx;
  border: none;
}

.btn-cancel {
  background: var(--bg-input);
  color: var(--text-secondary);
}

.btn-confirm {
  color: #fff;
}
</style>
