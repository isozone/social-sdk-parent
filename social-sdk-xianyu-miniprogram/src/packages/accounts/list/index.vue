<template>
  <view class="page-accounts">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><text class="nav-arrow">‹</text><text>返回</text></view>
      <text class="nav-title">账号管理</text>
      <text class="nav-action" @click="addAccount">＋</text>
    </view>
    <scroll-view scroll-y class="list-area" @scrolltolower="loadMore">
      <view v-if="loading && list.length === 0" class="loading-hint">加载中...</view>
      <view v-if="!loading && list.length === 0" class="empty-hint">
        <text class="empty-emoji">👤</text><text>暂未添加闲鱼账号</text>
        <view class="empty-btn" @click="addAccount">＋ 添加账号</view>
      </view>
      <view class="account-card" v-for="a in list" :key="a.id" @click="openDetail(a)">
        <view class="ac-avatar" :class="{ on: a.enabled }">{{ (a.nickname || a.username || '?')[0] }}</view>
        <view class="ac-body">
          <view class="ac-name">{{ a.nickname || a.username }}</view>
          <view class="ac-sub">
            <text class="ac-status" :class="{ on: a.enabled }">{{ a.enabled ? '● 已启用' : '○ 已停用' }}</text>
            <text class="ac-cookies" v-if="a.cookieHeader">Cookie {{ a.cookieHeader.length }} 字符</text>
          </view>
        </view>
        <view class="ac-action" @click.stop="toggle(a)">{{ a.enabled ? '停' : '启' }}</view>
      </view>
      <view v-if="loadingMore" class="loading-hint">加载中...</view>
      <view v-if="noMore && list.length > 0" class="end-hint">— 已到底 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api/request'

const list = ref<any[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20

onMounted(() => reload())

async function reload() { page.value = 1; noMore.value = false; list.value = []; await load() }
async function load() {
  if (page.value === 1) loading.value = true; else loadingMore.value = true
  try {
    const res: any = await api.get('/api/mini/accounts', { page: page.value, pageSize })
    const rows = res?.records || []
    if (page.value === 1) list.value = rows; else list.value.push(...rows)
    if (rows.length < pageSize) noMore.value = true
  } catch (e: any) { uni.showToast({ title: e?.message || '加载失败', icon: 'none' }) }
  finally { loading.value = false; loadingMore.value = false }
}
async function loadMore() { if (loadingMore.value || noMore.value) return; page.value += 1; await load() }

async function toggle(a: any) {
  try { await api.put(`/api/mini/accounts/${a.id}/toggle`, { enabled: !a.enabled }); a.enabled = !a.enabled }
  catch (e: any) { uni.showToast({ title: e?.message || '操作失败', icon: 'none' }) }
}
function addAccount() { uni.navigateTo({ url: '/packages/accounts/add/index' }) }
function openDetail(a: any) { uni.navigateTo({ url: `/packages/accounts/detail/index?id=${a.id}` }) }
function goBack() { uni.navigateBack() }
</script>

<style scoped lang="scss">
.page-accounts { min-height: 100vh; background: #f5f5f7; display: flex; flex-direction: column; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; background: rgba(255,255,255,.92); border-bottom: 1rpx solid #e5e7eb; flex-shrink: 0; }
.nav-back { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; color: #4f46e5; font-weight: 600; }
.nav-arrow { font-size: 36rpx; font-weight: 300; }
.nav-title { font-size: 32rpx; font-weight: 700; color: #111827; }
.nav-action { font-size: 44rpx; color: #4f46e5; font-weight: 300; }
.list-area { flex: 1; padding: 20rpx 24rpx; }
.loading-hint, .end-hint { text-align: center; font-size: 22rpx; color: #9ca3af; padding: 30rpx 0; }
.empty-hint { display: flex; flex-direction: column; align-items: center; padding: 80rpx 0; gap: 20rpx; font-size: 24rpx; color: #9ca3af; }
.empty-emoji { font-size: 80rpx; }
.empty-btn { margin-top: 20rpx; padding: 16rpx 40rpx; border-radius: 36rpx; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; font-size: 26rpx; font-weight: 600; }
.account-card { display: flex; align-items: center; gap: 20rpx; background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }
.ac-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #d1d5db; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 700; flex-shrink: 0; }
.ac-avatar.on { background: linear-gradient(135deg, #4f46e5, #7c3aed); }
.ac-body { flex: 1; min-width: 0; }
.ac-name { font-size: 28rpx; font-weight: 600; color: #111827; }
.ac-sub { display: flex; gap: 16rpx; margin-top: 6rpx; }
.ac-status { font-size: 20rpx; color: #9ca3af; }
.ac-status.on { color: #10b981; }
.ac-cookies { font-size: 20rpx; color: #9ca3af; }
.ac-action { padding: 12rpx 28rpx; border-radius: 24rpx; background: #f3f4f6; color: #4f46e5; font-size: 22rpx; font-weight: 600; }
</style>
