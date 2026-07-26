<template>
  <view class="page-account-add">
        <view class="nav-bar">
      <navigator url="/packages/accounts/list/index" hover-class="none" class="nav-back"><text class="nav-arrow">‹</text><text>返回</text></navigator>
      <text class="nav-title">添加账号</text>
      <view class="nav-actions"><text class="nav-sub" @click="goQr">扫码登录</text></view>
    </view>

    <scroll-view scroll-y class="main-scroll">
      <view class="card"><text class="field-label">账号名称</text><input v-model="form.accountName" class="field-input" placeholder="如：闲鱼主力号" /></view>
      <view class="card"><text class="field-label">Cookie</text><textarea v-model="form.cookieHeader" class="field-textarea" placeholder="请粘贴完整 Cookie 字符串" /></view>
      <view class="card"><text class="field-label">备注（可选）</text><input v-model="form.remark" class="field-input" placeholder="添加备注便于区分" /></view>
      <button class="submit-btn" :disabled="!canSubmit" @click="createAccount">保存账号</button>
    </scroll-view>
  </view>
</template>
<script setup lang="ts">
import { ref, computed } from 'vue'
import { api } from '@/api/request'
const form = ref({ accountName: '', cookieHeader: '', remark: '' })
const canSubmit = computed(() => !!form.value.accountName && !!form.value.cookieHeader)
async function createAccount() {
  if (!canSubmit.value) return
  try { await api.post('/api/mini/accounts', form.value, false); uni.showToast({ title: '添加成功', icon: 'success' }); setTimeout(() => uni.navigateBack(), 800) }
  catch (e: any) { uni.showToast({ title: e?.message || '添加失败', icon: 'none' }) }
}
function goQr() { uni.showToast({ title: '扫码功能开发中', icon: 'none' }) }
</script>
<style scoped lang="scss">
.page-account-add{min-height:100vh;background:#f5f5f7}
.nav-bar{display:flex;align-items:center;justify-content:space-between;height:88rpx;padding:0 24rpx;background:#fff;border-bottom:1rpx solid #e5e7eb}
.nav-back{display:flex;align-items:center;gap:8rpx;font-size:28rpx;color:#4f46e5;font-weight:600;text-decoration:none}.nav-title{font-size:32rpx;font-weight:700;color:#111827}.nav-actions{display:flex;gap:16rpx}.nav-sub{font-size:26rpx;color:#4f46e5}
.main-scroll{height:calc(100vh - 88rpx)}
.card{background:#fff;border-radius:24rpx;margin:16rpx 24rpx;padding:24rpx}
.field-label{font-size:26rpx;font-weight:600;color:#374151;display:block;margin-bottom:14rpx}
.field-input{width:100%;height:80rpx;background:#f8f8f9;border:1rpx solid #e5e7eb;border-radius:16rpx;padding:0 24rpx;font-size:28rpx;color:#111827}
.field-textarea{width:100%;min-height:300rpx;background:#f8f8f9;border:1rpx solid #e5e7eb;border-radius:16rpx;padding:20rpx 24rpx;font-size:24rpx;color:#111827;line-height:1.6}
.submit-btn{margin:24rpx;height:88rpx;background:linear-gradient(135deg,#4f46e5,#7c3aed);color:#fff;border:none;border-radius:24rpx;font-size:30rpx;font-weight:600}
</style>
