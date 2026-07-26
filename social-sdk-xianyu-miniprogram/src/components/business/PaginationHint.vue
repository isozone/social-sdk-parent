<template>
  <view class="pagination-hint" v-if="show">
    <view class="ph-divider" />
    <text class="ph-text">{{ hintText }}</text>
    <view class="ph-divider" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  loading?: boolean
  noMore?: boolean
  total?: number
  current?: number
}>(), {
  loading: false,
  noMore: false,
  total: 0,
  current: 0,
})

const show = computed(() => props.loading || props.noMore || props.total > 0)
const hintText = computed(() => {
  if (props.loading) return '加载中...'
  if (props.noMore) return '— 已到底 —'
  if (props.total > 0 && props.current > 0) return `已加载 ${props.current} / ${props.total} 条`
  return ''
})
</script>

<style scoped lang="scss">
.pagination-hint { display: flex; align-items: center; justify-content: center; gap: 16rpx; padding: 30rpx 0; }
.ph-divider { width: 80rpx; height: 1rpx; background: #e5e7eb; }
.ph-text { font-size: 22rpx; color: #9ca3af; }
</style>
