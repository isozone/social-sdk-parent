<template>
  <view class="page-orders">
    <view class="header-bar">
      <text class="title">订单管理</text>
      <button class="sync-btn" @click="syncOrders">同步</button>
    </view>
    
    <scroll-view scroll-y class="main-scroll" @refresherpulling="onPullRefresh" :refresher-enabled="true" :refresher-triggered="loading">
      <!-- Tab filter -->
      <view class="tabs-row">
        <view class="tab-item" :class="{ active: currentType === 'all' }" @click="currentType = 'all'">全部</view>
        <view class="tab-item" :class="{ active: currentType === 'PENDING' }" @click="currentType = 'PENDING'">待发货</view>
        <view class="tab-item" :class="{ active: currentType === 'PAID' }" @click="currentType = 'PAID'">已付款</view>
        <view class="tab-item" :class="{ active: currentType === 'SHIPPED' }" @click="currentType = 'SHIPPED'">已发货</view>
        <view class="tab-item" :class="{ active: currentType === 'COMPLETED' }" @click="currentType = 'COMPLETED'">已完成</view>
      </view>

      <!-- Order list -->
      <view class="order-card" v-for="order in filteredOrders" :key="order.id" @click="goDetail(order.id)">
        <view class="order-header">
          <text class="order-id">订单号：{{ order.orderId || order.id }}</text>
          <text class="status-tag" :class="'status-' + order.status">{{ order.status }}</text>
        </view>
        <view class="order-body">
          <view class="order-info">
            <text class="item-title">{{ order.itemTitle }}</text>
            <text class="buyer-name">{{ order.buyerName }}</text>
          </view>
          <view class="order-price">¥ {{ order.amount }}</view>
        </view>
        <view class="order-footer">
          <text class="order-time">{{ formatTime(order.createdAt) }}</text>
          <button v-if="order.status === 'PENDING'" class="deliver-btn" @click.stop="deliverOrder(order.id)">去发货</button>
        </view>
      </view>

      <empty-state v-if="filteredOrders.length === 0" text="暂无订单" />
    </scroll-view>

    <!-- Delivery modal -->
    <view v-if="showDeliveryModal" class="modal-mask" @click="showDeliveryModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">填写物流信息</text>
        <input v-model="trackingNo" placeholder="请输入快递单号" class="input-field" />
        <input v-model="deliveryCompany" placeholder="快递公司（可选）" class="input-field" />
        <view class="modal-actions">
          <button class="cancel-btn" @click="showDeliveryModal = false">取消</button>
          <button class="confirm-btn" @click="confirmDelivery">确认发货</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/api'
import type { OrderItem, DeliveryParams } from '@/types/order'
import EmptyState from '@/components/common/EmptyState.vue'

const orders = ref<OrderItem[]>([])
const loading = ref(false)
const currentType = ref('all')
const showDeliveryModal = ref(false)
const selectedOrderId = ref<number | null>(null)
const trackingNo = ref('')
const deliveryCompany = ref('')

const filteredOrders = computed(() => {
  if (currentType.value === 'all') return orders.value
  return orders.value.filter(o => o.status === currentType.value)
})

async function loadOrders() {
  loading.value = true
  try {
    const params: any = { page: 1, size: 50 }
    if (currentType.value !== 'all') params.status = currentType.value
    const res = await api.get<any>('/api/mini/orders', params, false)
    if (Array.isArray(res)) orders.value = res
    else if (res?.records) orders.value = res.records
    else orders.value = []
  } finally {
    loading.value = false
  }
}

async function syncOrders() {
  uni.showLoading({ title: '同步中...' })
  try {
    await api.post('/api/mini/orders/sync')
    uni.showToast({ title: '同步成功', icon: 'success' })
    loadOrders()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '同步失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/orders/detail?id=${id}` })
}

function deliverOrder(id: number) {
  selectedOrderId.value = id
  showDeliveryModal.value = true
}

async function confirmDelivery() {
  if (!selectedOrderId.value) return
  try {
    await api.post(`/api/mini/orders/${selectedOrderId.value}/delivery`, {
      trackingNo: trackingNo.value,
      deliveryCompany: deliveryCompany.value || undefined
    })
    uni.showToast({ title: '发货成功', icon: 'success' })
    showDeliveryModal.value = false
    trackingNo.value = ''
    deliveryCompany.value = ''
    loadOrders()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '发货失败', icon: 'none' })
  }
}

function formatTime(time: string): string {
  return new Date(time).toLocaleDateString()
}

function onPullRefresh() {
  loadOrders()
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped lang="scss">
.page-orders { min-height: 100vh; background: var(--bg-page); }

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background: #fff;
  border-bottom: 1rpx solid var(--border-color);
}

.title { font-size: 36rpx; font-weight: 700; color: var(--text-primary); }
.sync-btn { height: 56rpx; padding: 0 24rpx; background: var(--brand-gradient); color: #fff; border: none; border-radius: 16rpx; font-size: 24rpx; }

.main-scroll { height: calc(100vh - 180rpx); }

.tabs-row {
  display: flex;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  overflow-x: auto;
}

.tab-item {
  flex-shrink: 0;
  padding: 12rpx 24rpx;
  border-radius: 16rpx;
  font-size: 24rpx;
  color: var(--text-secondary);
  background: var(--bg-input);
  
  &.active { background: var(--brand); color: #fff; font-weight: 600; }
}

.order-card {
  background: #fff;
  margin: 16rpx 24rpx;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: var(--shadow-sm);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.order-id { font-size: 22rpx; color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 400rpx; }
.status-tag { padding: 6rpx 16rpx; border-radius: 8rpx; font-size: 20rpx; font-weight: 600; }
.status-PENDING { background: rgba(245,158,11,0.1); color: #f59e0b; }
.status-PAID { background: rgba(59,130,246,0.1); color: #3b82f6; }
.status-SHIPPED { background: rgba(124,58,237,0.1); color: #7c3aed; }
.status-COMPLETED { background: rgba(16,185,129,0.1); color: #10b981; }

.order-body { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16rpx; }
.order-info { flex: 1; margin-right: 16rpx; }
.item-title { font-size: 28rpx; font-weight: 700; color: var(--text-primary); display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.buyer-name { font-size: 22rpx; color: var(--text-secondary); margin-top: 8rpx; display: block; }
.order-price { font-size: 32rpx; font-weight: 800; color: var(--danger); flex-shrink: 0; }

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border-light);
}

.order-time { font-size: 22rpx; color: var(--text-tertiary); }
.deliver-btn { height: 56rpx; padding: 0 24rpx; background: var(--brand-gradient); color: #fff; border: none; border-radius: 16rpx; font-size: 24rpx; }

.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-content { width: 600rpx; background: #fff; border-radius: 24rpx; padding: 40rpx; }
.modal-title { font-size: 36rpx; font-weight: 700; color: var(--text-primary); display: block; margin-bottom: 32rpx; }

.input-field {
  width: 100%;
  height: 72rpx;
  background: var(--bg-input);
  border: 1rpx solid var(--border-color);
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  margin-bottom: 24rpx;
}

.modal-actions { display: flex; gap: 24rpx; margin-top: 32rpx; }
.cancel-btn, .confirm-btn { flex: 1; height: 72rpx; border-radius: 16rpx; border: none; font-size: 28rpx; }
.cancel-btn { background: var(--bg-input); color: var(--text-secondary); }
.confirm-btn { background: var(--brand-gradient); color: #fff; }
</style>
