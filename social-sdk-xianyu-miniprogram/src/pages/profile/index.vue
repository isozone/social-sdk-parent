<template>
  <view class="page-profile">
    <view class="profile-header">
      <view class="avatar-box">🐟</view>
      <view class="profile-info">
        <text class="display-name">{{ auth.profile?.displayName || '未登录' }}</text>
        <text class="username">{{ auth.profile?.username || '' }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="content-scroll" @refresherpulling="onRefresh" :refresher-enabled="true" :refresher-triggered="loading">
      <view class="section-card">
        <text class="section-title">个人资料</text>
        <view class="info-row" v-for="(item, idx) in profileItems" :key="idx" @click="editItem(item.key)">
          <text class="info-label">{{ item.label }}</text>
          <text class="info-value">{{ item.value || '未设置' }}</text>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">账户安全</text>
        <view class="action-row" @click="showPasswordModal = true">
          <text>修改密码</text>
          <text class="arrow">></text>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">关于</text>
        <navigator url="/pages/service/index" class="action-row">
          <text>用户协议</text>
          <text class="arrow">></text>
        </navigator>
        <navigator url="/pages/privacy/index" class="action-row">
          <text>隐私政策</text>
          <text class="arrow">></text>
        </navigator>
      </view>

      <button class="logout-btn" @click="confirmLogout">退出登录</button>
    </scroll-view>

    <!-- Password Modal -->
    <view v-if="showPasswordModal" class="modal-mask" @click="showPasswordModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">修改密码</text>
        <view class="input-group">
          <text class="label">新密码</text>
          <input type="password" v-model="newPwd" placeholder="请输入新密码" />
        </view>
        <view class="input-group">
          <text class="label">确认新密码</text>
          <input type="password" v-model="newPwdConfirm" placeholder="请再次输入新密码" />
        </view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showPasswordModal = false">取消</button>
          <button class="confirm-btn" @click="changePassword">确认</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/store/modules/auth'

const auth = useAuthStore()
const showPasswordModal = ref(false)
const newPwd = ref('')
const newPwdConfirm = ref('')
const loading = ref(false)

const profileItems = computed(() => [
  { key: 'username', label: '用户名', value: auth.profile?.username },
  { key: 'displayName', label: '昵称', value: auth.profile?.displayName },
  { key: 'email', label: '邮箱', value: auth.profile?.email },
  { key: 'phone', label: '手机', value: auth.profile?.phone },
])

async function editItem(key: string) {
  // For now just show a toast
  uni.showToast({ title: `编辑${key}`, icon: 'none' })
}

async function changePassword() {
  if (!newPwd.value || !newPwdConfirm.value) {
    uni.showToast({ title: '请填写完整', icon: 'none' })
    return
  }
  if (newPwd.value !== newPwdConfirm.value) {
    uni.showToast({ title: '两次新密码不一致', icon: 'none' })
    return
  }
  try {
    await auth.changePassword(newPwd.value)
    uni.showToast({ title: '密码修改成功', icon: 'success' })
    showPasswordModal.value = false
    newPwd.value = ''
    newPwdConfirm.value = ''
  } catch (e: any) {
    uni.showToast({ title: e?.message || '修改失败', icon: 'none' })
  }
}

function confirmLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        auth.logout()
      }
    }
  })
}

function onRefresh() {
  auth.fetchProfile()
}
</script>

<style scoped lang="scss">
.page-profile { min-height: 100vh; background: var(--bg-page); }

.profile-header {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  padding: 60rpx 40rpx 80rpx;
  border-radius: 0 0 48rpx 48rpx;
  display: flex;
  align-items: center;
  gap: 28rpx;
  position: relative;
  overflow: hidden;
}

.avatar-box {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: rgba(255,255,255,0.18);
  border: 4rpx solid rgba(255,255,255,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
}

.display-name { font-size: 40rpx; font-weight: 700; color: #fff; }
.username { font-size: 24rpx; color: rgba(255,255,255,0.7); margin-top: 4rpx; display: block; }

.content-scroll { height: calc(100vh - 240rpx); }

.section-card {
  background: #fff;
  margin: 24rpx;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: var(--shadow-sm);
}

.section-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--text-primary);
  display: block;
  margin-bottom: 16rpx;
}

.info-row, .action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--border-light);
}

.info-label { font-size: 28rpx; color: var(--text-secondary); }
.info-value { font-size: 28rpx; color: var(--text-primary); }
.action-row { cursor: pointer; }
.arrow { color: var(--text-tertiary); }

.logout-btn {
  margin: 40rpx 24rpx;
  height: 88rpx;
  background: #fff;
  color: var(--danger);
  border: 1rpx solid var(--border-color);
  border-radius: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
}

/* Modal */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.modal-content {
  width: 600rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx;
}

.modal-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-primary);
  display: block;
  margin-bottom: 32rpx;
}

.input-group {
  margin-bottom: 24rpx;
}

.label {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary);
  margin-bottom: 12rpx;
}

input {
  width: 100%;
  height: 72rpx;
  background: var(--bg-input);
  border: 1rpx solid var(--border-color);
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
}

.modal-actions {
  display: flex;
  gap: 24rpx;
  margin-top: 32rpx;
}

.cancel-btn, .confirm-btn {
  flex: 1;
  height: 72rpx;
  border-radius: 16rpx;
  border: none;
  font-size: 28rpx;
}

.cancel-btn {
  background: var(--bg-input);
  color: var(--text-secondary);
}

.confirm-btn {
  background: var(--brand-gradient);
  color: #fff;
}
</style>
