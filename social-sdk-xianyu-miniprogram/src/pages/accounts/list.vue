<template>
  <view class="page-accounts">
    <view class="header-bar"><text class="title">账号管理</text><button class="add-btn" @click="goAdd">+ 添加</button></view>
    <scroll-view scroll-y class="list-scroll" @refresherpulling="onPullRefresh" :refresher-enabled="true" :refresher-triggered="loading">
      <view class="account-card" v-for="acc in list" :key="acc.id" @click="goDetail(acc.id)">
        <view class="avatar" :style="{background:acc.status==='ACTIVE'?'linear-gradient(135deg,#4f46e5,#7c3aed)':'linear-gradient(135deg,#f59e0b,#ef4444)'}">{{ (acc.accountName||'?')[0] }}</view>
        <view class="info"><text class="name">{{ acc.accountName }}</text><text class="desc">{{ acc.remark || acc.status }}</text></view>
        <view class="badge" :class="acc.status==='ACTIVE'?'active':'inactive'">{{ acc.status==='ACTIVE'?'运行中':'已停用' }}</view>
      </view>
      <empty-state v-if="!loading && list.length===0" text="暂无账号" action-text="添加账号" @action="goAdd" />
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'; import { api } from '@/api/request'; import EmptyState from '@/components/common/EmptyState.vue'
const list = ref<any[]>([]), loading = ref(false)
async function load() { loading.value=true; try{const r=await api.get('/api/mini/accounts',{page:1,size:50},false);if(Array.isArray(r))list.value=r;else if(r?.records)list.value=r.records}catch{}finally{loading.value=false} }
function goAdd(){uni.navigateTo({url:'/packages/accounts/add/index'})}
function goDetail(id:number){uni.navigateTo({url:`/packages/accounts/detail/index?id=${id}`})}
function onPullRefresh(){load()} load()
</script>
<style scoped lang="scss">
.page-accounts{min-height:100vh;background:var(--bg-page)}.header-bar{display:flex;justify-content:space-between;align-items:center;padding:24rpx;background:#fff;border-bottom:1rpx solid var(--border-color)}.title{font-size:36rpx;font-weight:700;color:var(--text-primary)}.add-btn{height:56rpx;padding:0 32rpx;background:var(--brand-gradient);color:#fff;border:none;border-radius:16rpx;font-size:26rpx;font-weight:600}.list-scroll{height:calc(100vh-100rpx)}.account-card{display:flex;align-items:center;gap:24rpx;padding:24rpx;margin:16rpx 24rpx;background:#fff;border-radius:24rpx;border:1rpx solid var(--border-color);box-shadow:var(--shadow-sm)}.avatar{width:80rpx;height:80rpx;border-radius:20rpx;display:flex;align-items:center;justify-content:center;color:#fff;font-size:32rpx;font-weight:700}.info{flex:1;min-width:0}.name{font-size:28rpx;font-weight:700;color:var(--text-primary)}.desc{font-size:22rpx;color:var(--text-tertiary);margin-top:4rpx;display:block}.badge{padding:6rpx 16rpx;border-radius:8rpx;font-size:22rpx;font-weight:600}.active{background:rgba(16,185,129,.1);color:#10b981}.inactive{background:rgba(245,158,11,.1);color:#f59e0b}
</style>
