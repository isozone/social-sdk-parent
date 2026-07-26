<template>
  <view class="order-status-tag" :class="cls">{{ label }}</view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
  customLabels?: Record<string, string>
}>()

const labelMap: Record<string, string> = {
  CREATED: '已创建', PAID: '已付款', SHIPPED: '已发货', RECEIVED: '已收货',
  CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款', CANCELLED: '已取消',
  PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败',
}

const label = computed(() => (props.customLabels && props.customLabels[props.status]) || labelMap[props.status] || props.status)
const cls = computed(() => props.status.toLowerCase())
</script>

<style scoped lang="scss">
.order-status-tag { display: inline-flex; align-items: center; padding: 6rpx 16rpx; border-radius: 10rpx; font-size: 20rpx; font-weight: 600; background: #f3f4f6; color: #6b7280; }
.order-status-tag.created, .order-status-tag.pending { background: rgba(107,114,128,.12); color: #4b5563; }
.order-status-tag.paid, .order-status-tag.processing { background: rgba(79,70,229,.12); color: #4f46e5; }
.order-status-tag.shipped { background: rgba(6,182,212,.12); color: #0891b2; }
.order-status-tag.received, .order-status-tag.completed { background: rgba(16,185,129,.12); color: #059669; }
.order-status-tag.closed, .order-status-tag.cancelled { background: rgba(107,114,128,.12); color: #6b7280; }
.order-status-tag.refunding { background: rgba(245,158,11,.12); color: #d97706; }
.order-status-tag.refunded, .order-status-tag.failed { background: rgba(239,68,68,.12); color: #dc2626; }
</style>
