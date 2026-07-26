<template>
  <view class="page-wallet">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">钱包</text>
      <text class="nav-action" @click="reload">⟳</text>
    </view>

    <scroll-view scroll-y class="content-area" @scrolltolower="loadMore">
      <view class="overview-card" v-if="overview">
        <view class="ov-label">可用余额</view>
        <view class="ov-amount">¥ {{ formatMoney(overview.withdrawableBalance) }}</view>
        <view class="ov-row">
          <view class="ov-cell">
            <text class="ov-cell-label">总余额</text>
            <text class="ov-cell-value">¥ {{ formatMoney(overview.balance) }}</text>
          </view>
          <view class="ov-cell">
            <text class="ov-cell-label">冻结</text>
            <text class="ov-cell-value">¥ {{ formatMoney(overview.frozenBalance) }}</text>
          </view>
        </view>
        <view class="ov-sync" v-if="overview.lastSyncedAt">同步于 {{ formatTime(overview.lastSyncedAt) }}</view>
      </view>

      <view class="section-head">
        <text class="sh-title">交易明细</text>
        <text class="sh-sub" v-if="list.length">共 {{ total }} 条</text>
      </view>

      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">💰</text>
        <text>暂无交易记录</text>
      </view>

      <view class="tx-card" v-for="t in list" :key="t.id">
        <view class="tx-icon" :class="t.amount >= 0 ? 'in' : 'out'">
          {{ t.amount >= 0 ? '↓' : '↑' }}
        </view>
        <view class="tx-info">
          <view class="tx-top">
            <text class="tx-type">{{ typeLabel(t.type) }}</text>
            <text class="tx-amount" :class="t.amount >= 0 ? 'in' : 'out'">{{ t.amount >= 0 ? '+' : '' }}¥ {{ formatMoney(t.amount) }}</text>
          </view>
          <view class="tx-bottom">
            <text class="tx-desc">{{ t.description || '—' }}</text>
            <text class="tx-time">{{ formatTime(t.occurredAt) }}</text>
          </view>
        </view>
      </view>

      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getWalletOverview, getTransactions } from '@/api/wallet'
import type { WalletOverview, TransactionItem } from '@/types/wallet'

const overview = ref<WalletOverview | null>(null)
const list = ref<TransactionItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 20

onMounted(() => reload())

async function reload() {
  page.value = 1
  noMore.value = false
  list.value = []
  await Promise.all([loadOverview(), load()])
}

async function loadOverview() {
  try {
    overview.value = await getWalletOverview()
  } catch {}
}

async function load() {
  if (page.value === 1) loading.value = true
  else loadingMore.value = true
  try {
    const res = await getTransactions({ page: page.value, pageSize })
    const rows = res?.records || []
    total.value = res?.total || rows.length
    if (page.value === 1) list.value = rows
    else list.value.push(...rows)
    if (rows.length < pageSize) noMore.value = true
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || noMore.value) return
  page.value += 1
  await load()
}

function formatMoney(n: number) {
  return (Number(n) || 0).toFixed(2)
}

function formatTime(s: string) {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 16)
}

function typeLabel(t: string) {
  return { INCOME: '收入', EXPENSE: '支出', REFUND: '退款', WITHDRAW: '提现', ADJUST: '调整' }[t] || t
}

function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-wallet { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); backdrop-filter: blur(20rpx); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 36rpx; color: #4f46e5; }

.content-area { flex: 1; padding: 20rpx 24rpx; }

.overview-card { background: linear-gradient(135deg, #4f46e5, #7c3aed); border-radius: 28rpx; padding: 40rpx 32rpx; margin-bottom: 24rpx; box-shadow: 0 8rpx 24rpx rgba(79,70,229,.25); }
.ov-label { font-size: 24rpx; color: rgba(255,255,255,.8); }
.ov-amount { font-size: 64rpx; font-weight: 700; color: #fff; margin: 8rpx 0 20rpx; }
.ov-row { display: flex; gap: 40rpx; }
.ov-cell { display: flex; flex-direction: column; gap: 6rpx; }
.ov-cell-label { font-size: 22rpx; color: rgba(255,255,255,.7); }
.ov-cell-value { font-size: 30rpx; font-weight: 600; color: #fff; }
.ov-sync { font-size: 20rpx; color: rgba(255,255,255,.6); margin-top: 20rpx; }

.section-head { display: flex; align-items: baseline; justify-content: space-between; margin: 24rpx 4rpx 16rpx; }
.sh-title { font-size: 28rpx; font-weight: 700; color: #111827; }
.sh-sub { font-size: 22rpx; color: #9ca3af; }

.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }

.tx-card { display: flex; align-items: center; gap: 20rpx; background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.tx-icon { width: 64rpx; height: 64rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 32rpx; color: #fff; flex-shrink: 0; }
.tx-icon.in { background: linear-gradient(135deg, #10b981, #22c55e); }
.tx-icon.out { background: linear-gradient(135deg, #ef4444, #f97316); }
.tx-info { flex: 1; min-width: 0; }
.tx-top { display: flex; align-items: center; justify-content: space-between; }
.tx-type { font-size: 28rpx; font-weight: 600; color: #111827; }
.tx-amount { font-size: 28rpx; font-weight: 700; }
.tx-amount.in { color: #10b981; }
.tx-amount.out { color: #ef4444; }
.tx-bottom { display: flex; align-items: center; justify-content: space-between; margin-top: 8rpx; }
.tx-desc { font-size: 22rpx; color: #6b7280; max-width: 380rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tx-time { font-size: 20rpx; color: #9ca3af; }
</style>
