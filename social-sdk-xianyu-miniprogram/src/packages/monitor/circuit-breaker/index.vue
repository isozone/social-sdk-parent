<template>
  <view class="page"><view class="nav"><text @click="back">‹</text><text>熔断器</text><text @click="load">⟳</text></view><view class="card"><text class="title">服务熔断状态</text><input v-model="serviceName" placeholder="服务名，如 message-sync"/><input v-model="accountId" placeholder="账号ID，0为全局" type="number"/><button @click="load">查询状态</button><button @click="doReset">重置熔断</button></view><view class="card" v-if="status"><text>{{ JSON.stringify(status, null, 2) }}</text></view></view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { getStatus, reset, globalReset } from '@/api/circuitBreaker'
const accountId=ref('0'), serviceName=ref('message-sync'), status=ref<any>(null)
async function load(){try{status.value=await getStatus(accountId.value||0,serviceName.value)}catch(e:any){uni.showToast({title:e?.message||'查询失败',icon:'none'})}}
async function doReset(){try{if(String(accountId.value)==='0')await globalReset(serviceName.value);else await reset(accountId.value,serviceName.value);uni.showToast({title:'已重置',icon:'success'});await load()}catch(e:any){uni.showToast({title:e?.message||'重置失败',icon:'none'})}}
function back(){uni.navigateBack({fail:()=>uni.switchTab({url:'/pages/profile/index'})})}
</script>
<style scoped lang="scss">.page{min-height:100vh;background:#f5f5f7}.nav{height:88rpx;background:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 24rpx;font-size:32rpx;font-weight:700}.nav text:first-child{font-size:48rpx;color:#6b7280}.nav text:last-child{font-size:34rpx;color:#4f46e5}.card{margin:24rpx;background:#fff;border-radius:24rpx;padding:28rpx;display:flex;flex-direction:column;gap:18rpx}.title{font-size:30rpx;font-weight:700}input{height:72rpx;background:#f9fafb;border-radius:16rpx;padding:0 20rpx}button{height:72rpx;line-height:72rpx;border-radius:16rpx;background:#4f46e5;color:#fff;font-size:26rpx}</style>
