<template>
  <view class="page-vship">
    <view class="nav-bar">
      <text class="nav-back" @click="goBack">‹</text>
      <text class="nav-title">虚拟发货</text>
      <text class="nav-action" @click="reload">⟳</text>
    </view>

    <scroll-view scroll-y class="content" @scrolltolower="loadMore">
      <view class="progress-card">
        <text class="pg-title">发货任务总览</text>
        <text class="pg-desc">共 {{ total }} 条 · 待处理 {{ pendingCount }} · 失败 {{ failedCount }}</text>
        <view class="pg-bar"><view class="pg-fill" :style="{ width: progressPercent + '%' }" /></view>
        <view class="pg-info">
          <text>完成率 {{ progressPercent }}%</text>
          <text @click="openConfig">配置 ›</text>
        </view>
      </view>

      <view class="filter-row">
        <view
          v-for="f in filters"
          :key="f.value"
          class="filter-chip"
          :class="{ active: status === f.value }"
          @click="switchStatus(f.value)"
        >{{ f.label }}</view>
      </view>

      <view v-if="loading && !list.length" class="hint">加载中...</view>
      <view v-else-if="!list.length" class="hint">暂无虚拟发货任务</view>

      <view class="task-card" v-for="t in list" :key="t.id">
        <view class="task-top">
          <view>
            <text class="task-name">任务 #{{ t.id }}</text>
            <text class="task-order">订单 {{ t.orderId || '-' }} · 商品 {{ t.productId || '-' }}</text>
          </view>
          <text class="status-pill" :class="statusClass(t.status)">{{ statusLabel(t.status) }}</text>
        </view>
        <view class="task-detail">
          <text>账号 {{ t.accountId || '-' }}</text>
          <text>{{ t.executeAt || t.scheduledAt || t.createdAt || '' }}</text>
        </view>
        <view v-if="t.errorMessage" class="task-error">{{ t.errorMessage }}</view>
        <view class="task-actions">
          <view class="task-btn secondary" @click="retry(t)" v-if="t.status === 'FAILED'">重试</view>
          <view class="task-btn primary" @click="trigger(t)" v-if="t.status === 'PENDING' || t.status === 'FAILED'">立即发货</view>
          <view class="task-btn secondary" @click="sendByOrder(t)" v-if="t.orderId">按订单触发</view>
        </view>
      </view>

      <view v-if="loadingMore" class="hint">加载更多...</view>
      <view v-if="noMore && list.length" class="hint">— 已到底 —</view>
      <view style="height: 40rpx;" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getTasks, retryTask, sendCard, triggerTask, getConfig, updateConfig } from '@/api/virtualShip'
import { useAccountStore } from '@/store/modules/account'

const accountStore = useAccountStore()
const list = ref<any[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const status = ref('')
const filters = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

const pendingCount = computed(() => list.value.filter(t => t.status === 'PENDING').length)
const failedCount = computed(() => list.value.filter(t => t.status === 'FAILED').length)
const progressPercent = computed(() => {
  if (!list.value.length) return 0
  const done = list.value.filter(t => t.status === 'SUCCESS' || t.status === 'COMPLETED').length
  return Math.round((done / list.value.length) * 100)
})

onMounted(() => reload())

async function reload() {
  page.value = 1
  noMore.value = false
  list.value = []
  await load()
}

async function load() {
  if (page.value === 1) loading.value = true
  else loadingMore.value = true
  try {
    const params: any = { page: page.value, size: pageSize }
    if (status.value) params.status = status.value
    const res: any = await getTasks(params)
    const rows = Array.isArray(res) ? res : (res?.records || res?.list || [])
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

function switchStatus(v: string) {
  if (status.value === v) return
  status.value = v
  reload()
}

async function trigger(t: any) {
  try {
    await triggerTask(t.id)
    uni.showToast({ title: '已触发', icon: 'success' })
    await reload()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '触发失败', icon: 'none' })
  }
}

async function retry(t: any) {
  try {
    await retryTask(t.id)
    uni.showToast({ title: '已重试', icon: 'success' })
    await reload()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '重试失败', icon: 'none' })
  }
}

async function sendByOrder(t: any) {
  try {
    await sendCard({ orderId: t.orderId, taskId: t.id })
    uni.showToast({ title: '已按订单触发', icon: 'success' })
    await reload()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' })
  }
}

async function openConfig() {
  const accountId = accountStore.current?.id
  if (!accountId) {
    uni.showToast({ title: '请先选择账号', icon: 'none' })
    return
  }
  try {
    const cfg: any = await getConfig(accountId)
    uni.showModal({
      title: '虚拟发货配置',
      content: `账号 ${accountId}\n启用: ${cfg?.enabled ? '是' : '否'}\n延迟: ${cfg?.delaySeconds ?? 0}s\n自动确认: ${cfg?.autoConfirmDays ?? 7}天`,
      confirmText: '切换启用',
      success: async (r) => {
        if (!r.confirm) return
        try {
          await updateConfig({
            id: cfg?.id,
            accountId,
            enabled: !cfg?.enabled,
            delaySeconds: cfg?.delaySeconds ?? 0,
            autoConfirmDays: cfg?.autoConfirmDays ?? 7,
            notifyAfterShip: cfg?.notifyAfterShip ?? true,
          })
          uni.showToast({ title: '配置已更新', icon: 'success' })
        } catch (e: any) {
          uni.showToast({ title: e?.message || '更新失败', icon: 'none' })
        }
      }
    })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '读取配置失败', icon: 'none' })
  }
}

function statusLabel(s?: string) {
  return ({ PENDING: '待处理', SUCCESS: '成功', COMPLETED: '完成', FAILED: '失败', SENDING: '发送中' } as any)[s || ''] || (s || '未知')
}
function statusClass(s?: string) {
  return ({ PENDING: 'pending', SUCCESS: 'done', COMPLETED: 'done', FAILED: 'fail', SENDING: 'sending' } as any)[s || ''] || 'pending'
}
function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/profile/index' }) })
}
</script>

<style scoped lang="scss">
.page-vship { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar {
  display: flex; align-items: center; justify-content: space-between;
  height: 88rpx; padding: 0 24rpx; background: #fff; border-bottom: 1rpx solid #e5e7eb;
}
.nav-back { font-size: 48rpx; color: #6b7280; width: 48rpx; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 36rpx; color: #4f46e5; width: 48rpx; text-align: right; }
.content { flex: 1; padding: 20rpx 0; }
.progress-card {
  margin: 0 24rpx 20rpx; padding: 28rpx; border-radius: 28rpx; color: #fff;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 8rpx 24rpx rgba(79,70,229,.25);
}
.pg-title { display: block; font-size: 30rpx; font-weight: 700; }
.pg-desc { display: block; font-size: 22rpx; opacity: .75; margin: 8rpx 0 18rpx; }
.pg-bar { height: 16rpx; background: rgba(255,255,255,.18); border-radius: 12rpx; overflow: hidden; }
.pg-fill { height: 100%; background: linear-gradient(90deg, #22d3ee, #34d399); }
.pg-info { display: flex; justify-content: space-between; margin-top: 12rpx; font-size: 22rpx; opacity: .85; }
.filter-row { display: flex; gap: 12rpx; padding: 0 24rpx 16rpx; }
.filter-chip {
  padding: 10rpx 22rpx; border-radius: 24rpx; background: #fff; color: #6b7280;
  font-size: 22rpx; border: 1rpx solid #e5e7eb;
}
.filter-chip.active { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border-color: transparent; }
.hint { text-align: center; color: #9ca3af; font-size: 22rpx; padding: 24rpx 0; }
.task-card {
  margin: 0 24rpx 16rpx; background: #fff; border-radius: 24rpx; padding: 24rpx;
  border: 1rpx solid #e5e7eb; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03);
}
.task-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 16rpx; }
.task-name { display: block; font-size: 28rpx; font-weight: 700; color: #111827; }
.task-order { display: block; font-size: 20rpx; color: #9ca3af; margin-top: 6rpx; }
.status-pill { padding: 6rpx 16rpx; border-radius: 12rpx; font-size: 20rpx; font-weight: 700; }
.status-pill.pending { background: rgba(156,163,175,.12); color: #6b7280; }
.status-pill.sending { background: rgba(79,70,229,.1); color: #4f46e5; }
.status-pill.done { background: rgba(16,185,129,.1); color: #10b981; }
.status-pill.fail { background: rgba(239,68,68,.1); color: #ef4444; }
.task-detail { display: flex; justify-content: space-between; margin-top: 14rpx; font-size: 22rpx; color: #9ca3af; }
.task-error { margin-top: 12rpx; font-size: 22rpx; color: #ef4444; background: rgba(239,68,68,.06); padding: 12rpx 16rpx; border-radius: 12rpx; }
.task-actions { display: flex; gap: 12rpx; margin-top: 16rpx; }
.task-btn {
  flex: 1; height: 68rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center;
  font-size: 24rpx; font-weight: 600;
}
.task-btn.primary { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; }
.task-btn.secondary { background: #f3f4f6; color: #6b7280; }
</style>
