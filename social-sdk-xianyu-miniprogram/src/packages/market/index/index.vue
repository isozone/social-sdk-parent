<template>
  <view class="page">
    <view class="nav"><text class="back" @click="goBack">‹</text><text class="title">市场情报</text><text class="act" @click="load">⟳</text></view>
    <view class="hero"><text class="hero-title">关键词趋势</text><text class="hero-sub">基于后台市场情报聚合数据</text></view>
    <view class="search"><input v-model="keyword" placeholder="输入关键词查询" confirm-type="search" @confirm="search"/><button @click="search">查询</button></view>
    <scroll-view scroll-y class="list">
      <view v-if="loading" class="hint">加载中...</view>
      <view v-if="!loading && !items.length" class="hint">暂无市场数据</view>
      <view class="card" v-for="(it,i) in items" :key="i">
        <view class="row"><text class="name">{{ it.keyword || it.name || '关键词' }}</text><text class="score">{{ it.searchIndex || it.hot || 0 }}</text></view>
        <view class="meta"><text>在线商品 {{ it.onlineGoodsCount || it.productCount || 0 }}</text><text>竞争 {{ it.competition || '-' }}</text></view>
      </view>
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getLatest, getKeywords } from '@/api/market'
const keyword = ref('')
const items = ref<any[]>([])
const loading = ref(false)
async function load(){ loading.value=true; try{ const r:any=await getLatest(); items.value=Array.isArray(r)?r:(r?.marketStats||r?.records||[]) }catch(e:any){uni.showToast({title:e?.message||'加载失败',icon:'none'})}finally{loading.value=false} }
async function search(){ if(!keyword.value.trim()) return load(); loading.value=true; try{ const r:any=await getKeywords(keyword.value.trim()); items.value=Array.isArray(r)?r:(r?.marketStats||r?.records||[]) }catch(e:any){uni.showToast({title:e?.message||'查询失败',icon:'none'})}finally{loading.value=false} }
function goBack(){uni.navigateBack({fail:()=>uni.switchTab({url:'/pages/profile/index'})})}
onMounted(load)
</script>
<style scoped lang="scss">
.page{min-height:100vh;background:#f5f5f7}.nav{height:88rpx;padding:0 24rpx;background:#fff;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #e5e7eb}.back{font-size:48rpx;color:#6b7280}.title{font-size:32rpx;font-weight:700}.act{font-size:34rpx;color:#4f46e5}.hero{margin:24rpx;padding:32rpx;border-radius:28rpx;color:#fff;background:linear-gradient(135deg,#4f46e5,#7c3aed)}.hero-title{display:block;font-size:34rpx;font-weight:800}.hero-sub{display:block;margin-top:8rpx;font-size:22rpx;opacity:.75}.search{display:flex;gap:16rpx;margin:0 24rpx 20rpx}.search input{flex:1;height:72rpx;background:#fff;border-radius:36rpx;padding:0 28rpx;font-size:26rpx}.search button{height:72rpx;line-height:72rpx;border-radius:36rpx;background:#4f46e5;color:#fff;font-size:26rpx}.list{height:calc(100vh - 260rpx);padding:0 24rpx}.hint{text-align:center;color:#9ca3af;padding:40rpx 0}.card{background:#fff;border-radius:24rpx;padding:28rpx;margin-bottom:16rpx;box-shadow:0 2rpx 12rpx rgba(0,0,0,.03)}.row{display:flex;justify-content:space-between;align-items:center}.name{font-size:30rpx;font-weight:700;color:#111827}.score{font-size:34rpx;font-weight:800;color:#4f46e5}.meta{display:flex;justify-content:space-between;margin-top:16rpx;color:#6b7280;font-size:22rpx}
</style>
