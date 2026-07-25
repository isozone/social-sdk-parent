<template>
  <view class="page-order-detail">
    <view class="detail-header">
      <text class="order-id">订单号: {{ order.orderId || order.id }}</text>
      <text class="status-tag">{{ order.status }}</text>
    </view>
    <scroll-view scroll-y class="info-scroll">
      <view class="card">
        <text class="item-title">{{ order.itemTitle }}</text>
        <text class="price">¥ {{ order.amount }}</text>
        <view class="meta-row"><text>买家: {{ order.buyerName }}</text><text>卖家: {{ order.accountName }}</text></view>
        <view class="meta-row" v-if="order.trackingNo"><text>快递单号: {{ order.trackingNo }}</text></view>
        <view class="meta-row" v-if="order.deliveryType"><text>发货方式: {{ order.deliveryType }}</text></view>
      </view>
      <view class="btn-group" v-if="order.status === 'PENDING'">
        <button class="btn-primary" @click="goDeliver">去发货</button>
      </view>
      <view class="card" v-if="order.buyerMsg"><text class="label">买家留言</text><text class="content">{{ order.buyerMsg }}</text></view>
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted, onLoad } from '@dcloudio/uni-app'
import api from '@/api'
let oid = 0; const order = ref<any>({})
onLoad((opt: any) => { oid = Number(opt.id); load() })
async function load() { try { const r = await api.get(`/api/mini/orders/${oid}`); Object.assign(order.value, r) } catch {} }
function goDeliver() {
  uni.navigateTo({ url: `/pages/orders/list?deliverId=${oid}` })
}
</script>
<style scoped lang="scss">
.page-order-detail { min-height: 100vh; background: var(--bg-page); }
.detail-header { padding: 32rpx; background: #fff; display: flex; justify-content: space-between; align-items: center; border-bottom: 1rpx solid var(--border-color); }
.order-id { font-size: 24rpx; color: var(--text-tertiary); }
.status-tag { padding: 8rpx 20rpx; border-radius: 8rpx; background: rgba(59,130,246,0.1); color: #3b82f6; font-size: 22rpx; font-weight: 600; }
.info-scroll { height: calc(100vh - 120rpx); }
.card { background: #fff; margin: 24rpx; padding: 32rpx; border-radius: 24rpx; box-shadow: var(--shadow-sm); }
.item-title { font-size: 32rpx; font-weight: 700; color: var(--text-primary); display: block; }
.price { font-size: 40rpx; font-weight: 800; color: var(--danger); margin-top: 16rpx; display: block; }
.meta-row { display: flex; justify-content: space-between; padding: 16rpx 0; font-size: 26rpx; color: var(--text-secondary); border-bottom: 1rpx solid var(--border-light); }
.meta-row:last-child { border-bottom: none; }
.btn-group { display: flex; flex-direction: column; gap: 20rpx; margin: 24rpx; }
.btn-primary { height: 72rpx; background: var(--brand-gradient); color: #fff; border: none; border-radius: 16rpx; font-size: 28rpx; }
.label { font-size: 26rpx; color: var(--text-secondary); display: block; margin-top: 24rpx; }
.content { font-size: 26rpx; color: var(--text-primary); margin-top: 12rpx; display: block; line-height: 1.6; }
</style>
