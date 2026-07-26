<template>
  <view class="page">
    <view class="nav"><text class="back" @click="goBack">‹</text><text class="title">买家画像</text><text class="act" @click="load">⟳</text></view>
    <view class="search"><input v-model="keyword" placeholder="搜索买家" confirm-type="search" @confirm="load"/><button @click="load">搜索</button></view>
    <scroll-view scroll-y class="list">
      <view v-if="loading" class="hint">加载中...</view>
      <view v-if="!loading && !items.length" class="hint">暂无买家画像</view>
      <view class="card" v-for="b in items" :key="b.buyerId || b.id">
        <view class="top"><view><text class="name">{{ b.buyerName || b.buyerId || '买家' }}</text><text class="sub">消费 ¥{{ b.totalSpent || b.totalAmount || 0 }} · 订单 {{ b.orderCount || 0 }}</text></view><text class="score">{{ b.credibilityScore || 50 }}</text></view>
        <view class="tags" v-if="b.tags"><text v-for="t in String(b.tags).split(',')" :key="t">{{ t }}</text></view>
        <view class="notes">{{ b.notes || '暂无备注' }}</view>
        <view class="actions"><button @click="addTag(b)">打标</button><button @click="editNote(b)">备注</button></view>
      </view>
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getList, tag, updateNote } from '@/api/buyer'
const keyword=ref(''); const items=ref<any[]>([]); const loading=ref(false)
function buyerId(b:any){return b.buyerId || b.id}
async function load(){loading.value=true;try{const r:any=await getList({keyword:keyword.value,page:0,size:50});items.value=Array.isArray(r)?r:(r?.records||r?.list||[])}catch(e:any){uni.showToast({title:e?.message||'加载失败',icon:'none'})}finally{loading.value=false}}
function addTag(b:any){uni.showModal({title:'添加标签',editable:true,placeholderText:'请输入标签',success:async m=>{if(!m.confirm||!m.content)return;try{await tag({buyerId:buyerId(b),tag:m.content});uni.showToast({title:'已打标',icon:'success'});load()}catch(e:any){uni.showToast({title:e?.message||'打标失败',icon:'none'})}}})}
function editNote(b:any){uni.showModal({title:'买家备注',editable:true,content:b.notes||'',placeholderText:'请输入备注',success:async m=>{if(!m.confirm)return;try{await updateNote({buyerId:buyerId(b),note:m.content||''});uni.showToast({title:'已保存',icon:'success'});load()}catch(e:any){uni.showToast({title:e?.message||'保存失败',icon:'none'})}}})}
function goBack(){uni.navigateBack({fail:()=>uni.switchTab({url:'/pages/profile/index'})})}
onMounted(load)
</script>
<style scoped lang="scss">
.page{min-height:100vh;background:#f5f5f7}.nav{height:88rpx;padding:0 24rpx;background:#fff;display:flex;align-items:center;justify-content:space-between;border-bottom:1rpx solid #e5e7eb}.back{font-size:48rpx;color:#6b7280}.title{font-size:32rpx;font-weight:700}.act{font-size:34rpx;color:#4f46e5}.search{display:flex;gap:16rpx;padding:24rpx}.search input{flex:1;height:72rpx;background:#fff;border-radius:36rpx;padding:0 28rpx}.search button{height:72rpx;line-height:72rpx;border-radius:36rpx;background:#4f46e5;color:#fff}.list{height:calc(100vh - 140rpx);padding:0 24rpx}.hint{text-align:center;color:#9ca3af;padding:40rpx 0}.card{background:#fff;border-radius:24rpx;padding:28rpx;margin-bottom:16rpx}.top{display:flex;justify-content:space-between;align-items:flex-start}.name{display:block;font-size:30rpx;font-weight:700;color:#111827}.sub{display:block;font-size:22rpx;color:#9ca3af;margin-top:8rpx}.score{font-size:38rpx;font-weight:800;color:#10b981}.tags{display:flex;gap:10rpx;flex-wrap:wrap;margin-top:16rpx}.tags text{background:rgba(79,70,229,.08);color:#4f46e5;padding:6rpx 16rpx;border-radius:14rpx;font-size:20rpx}.notes{margin-top:16rpx;padding:16rpx;background:#f9fafb;border-radius:14rpx;color:#6b7280;font-size:24rpx}.actions{display:flex;gap:16rpx;margin-top:16rpx}.actions button{flex:1;height:64rpx;line-height:64rpx;border-radius:16rpx;font-size:24rpx;background:#f3f4f6;color:#4f46e5}
</style>
