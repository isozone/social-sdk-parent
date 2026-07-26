<template>
  <view class="page">
    <view class="nav">
      <text class="back" @click="goBack">‹</text>
      <text class="title">网盘存储</text>
      <text class="act" @click="load">⟳</text>
    </view>
    <scroll-view scroll-y class="list">
      <view v-if="loading" class="hint">加载中...</view>
      <view v-if="!loading && !accounts.length" class="hint">暂无网盘账号</view>
      <view class="card" v-for="a in accounts" :key="a.id">
        <view class="row">
          <text class="name">{{ a.name || a.provider || ('账号 ' + a.id) }}</text>
          <text class="status">{{ a.status || (a.enabled ? '启用' : '停用') }}</text>
        </view>
        <text class="meta">ID {{ a.id }} · {{ a.type || a.driver || '-' }}</text>
        <view class="actions">
          <button @click="openAuth(a)">授权</button>
          <button @click="listFiles(a)">文件</button>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAccounts, getAuthUrl, getFiles } from '@/api/cloudStorage'

const accounts = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res: any = await getAccounts({ page: 1, size: 50 })
    accounts.value = Array.isArray(res) ? res : (res?.records || res?.list || [])
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function openAuth(a: any) {
  try {
    const res: any = await getAuthUrl({ accountId: a.id, storageAccountId: a.id })
    const url = res?.url || res?.authUrl || res
    if (typeof url === 'string' && url) {
      uni.setClipboardData({ data: url })
      uni.showToast({ title: '授权链接已复制', icon: 'none' })
    } else {
      uni.showToast({ title: '无授权链接', icon: 'none' })
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '获取授权失败', icon: 'none' })
  }
}

async function listFiles(a: any) {
  try {
    const res: any = await getFiles({ storageAccountId: a.id, page: 1, size: 20 })
    const rows = Array.isArray(res) ? res : (res?.records || [])
    uni.showModal({
      title: '文件列表',
      content: rows.length ? rows.slice(0, 8).map((f: any) => f.name || f.fileName || f.id).join('\n') : '暂无文件',
      showCancel: false,
    })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '读取文件失败', icon: 'none' })
  }
}

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/profile/index' }) })
}

onMounted(load)
</script>

<style scoped lang="scss">
.page { min-height: 100vh; background: #f5f5f7; }
.nav { height: 88rpx; padding: 0 24rpx; background: #fff; display: flex; align-items: center; justify-content: space-between; border-bottom: 1rpx solid #e5e7eb; }
.back { font-size: 48rpx; color: #6b7280; }
.title { font-size: 32rpx; font-weight: 700; }
.act { font-size: 34rpx; color: #4f46e5; }
.list { height: calc(100vh - 88rpx); padding: 24rpx; }
.hint { text-align: center; color: #9ca3af; padding: 40rpx 0; }
.card { background: #fff; border-radius: 24rpx; padding: 28rpx; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; align-items: center; }
.name { font-size: 30rpx; font-weight: 700; color: #111827; }
.status { font-size: 22rpx; color: #4f46e5; }
.meta { display: block; margin-top: 10rpx; color: #9ca3af; font-size: 22rpx; }
.actions { display: flex; gap: 16rpx; margin-top: 18rpx; }
.actions button { flex: 1; height: 64rpx; line-height: 64rpx; border-radius: 16rpx; background: #f3f4f6; color: #4f46e5; font-size: 24rpx; }
</style>
