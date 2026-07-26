<template>
  <view class="product-card" @click="$emit('click', item)">
    <view class="pc-cover">
      <image v-if="coverUrl" :src="coverUrl" mode="aspectFill" class="pc-img" />
      <view v-else class="pc-img-fallback"><text>暂无图</text></view>
      <view v-if="item.status" class="pc-status" :class="item.status.toLowerCase()">{{ statusLabel }}</view>
      <view v-if="item.policeCount > 0" class="pc-flag">擦 {{ item.policeCount }}</view>
    </view>
    <view class="pc-body">
      <view class="pc-title">{{ item.itemTitle || item.title || '未命名商品' }}</view>
      <view class="pc-bottom">
        <view class="pc-price">¥{{ formatPrice(item.price) }}</view>
        <view class="pc-meta">
          <text class="pc-stock">库存 {{ item.stock ?? 0 }}</text>
          <text class="pc-view">看 {{ item.viewCount ?? 0 }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProductItem } from '@/types/product'

const props = defineProps<{ item: Partial<ProductItem> }>()
defineEmits<{ (e: 'click', item: Partial<ProductItem>): void }>()

const coverUrl = computed(() => Array.isArray(props.item.images) ? props.item.images[0] : '')

const statusLabel = computed(() => {
  const m: Record<string, string> = { DRAFT: '草稿', ON_SALE: '在售', OFF_SALE: '下架', SOLD: '已售' }
  return m[props.item.status || ''] || props.item.status || ''
})

function formatPrice(n: any) {
  const v = Number(n)
  return isNaN(v) ? '0.00' : v.toFixed(2)
}
</script>

<style scoped lang="scss">
.product-card { background: #fff; border-radius: 20rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.05); transition: transform .15s; }
.product-card:active { transform: scale(.97); }
.pc-cover { position: relative; width: 100%; height: 280rpx; background: #f3f4f6; }
.pc-img { width: 100%; height: 100%; }
.pc-img-fallback { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #9ca3af; font-size: 24rpx; background: #f9fafb; }
.pc-status { position: absolute; top: 12rpx; left: 12rpx; padding: 6rpx 16rpx; border-radius: 10rpx; font-size: 20rpx; font-weight: 600; background: rgba(0,0,0,.5); color: #fff; }
.pc-status.on_sale { background: rgba(16,185,129,.85); }
.pc-status.off_sale { background: rgba(239,68,68,.85); }
.pc-status.sold { background: rgba(107,114,128,.85); }
.pc-status.draft { background: rgba(245,158,11,.85); }
.pc-flag { position: absolute; top: 12rpx; right: 12rpx; padding: 4rpx 12rpx; border-radius: 10rpx; font-size: 18rpx; background: rgba(79,70,229,.85); color: #fff; }
.pc-body { padding: 20rpx 24rpx 24rpx; }
.pc-title { font-size: 26rpx; font-weight: 600; color: #111827; line-height: 1.4; height: 72rpx; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.pc-bottom { display: flex; align-items: baseline; justify-content: space-between; margin-top: 16rpx; }
.pc-price { font-size: 32rpx; font-weight: 700; color: #ef4444; }
.pc-meta { display: flex; gap: 16rpx; }
.pc-stock, .pc-view { font-size: 20rpx; color: #9ca3af; }
</style>
