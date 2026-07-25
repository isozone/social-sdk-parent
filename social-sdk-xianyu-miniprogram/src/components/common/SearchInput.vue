<template>
  <view class="search-input">
    <text class="search-icon">🔍</text>
    <input v-model="localValue" :placeholder="placeholder" @confirm="$emit('search', localValue)" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{
  placeholder?: string
  modelValue?: string
}>(), {
  placeholder: '搜索...',
  modelValue: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  search: [value: string]
}>()

const localValue = ref('')
</script>

<style scoped lang="scss">
.search-input {
  position: relative;
  width: 100%;
}

.search-icon {
  position: absolute;
  left: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 32rpx;
  z-index: 1;
}

input {
  width: 100%;
  height: 72rpx;
  background: var(--bg-input);
  border: 2rpx solid var(--border-color);
  border-radius: var(--card-radius);
  padding: 0 32rpx;
  padding-left: 76rpx;
  font-size: 28rpx;
  color: var(--text-primary);
}
</style>
