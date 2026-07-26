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
        <text class="section-title">经营中心</text>
        <view class="action-row" @click="goTo('/packages/accounts/list/index')">
          <text>账号管理</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/pages/orders/list')">
          <text>订单管理</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/rules/list/index')">
          <text>关键词规则</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/wallet/index/index')">
          <text>钱包资产</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/notify/index/index')">
          <text>站内通知</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/reviews/index/index')">
          <text>评价退款</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/collect/list/index')">
          <text>收藏关注</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/virtualShip/tasks/index')">
          <text>虚拟发货</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/report/replyLogs/index/index')">
          <text>自动回复日志</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/ai/index/index')">
          <text>AI 管理中心</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/market/index/index')">
          <text>市场情报</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/buyer/detail/index')">
          <text>买家画像</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/storage/cloud-storage/index')">
          <text>网盘存储</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/storage/chrome-config/index')">
          <text>Chrome 配置</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/monitor/circuit-breaker/index')">
          <text>熔断器</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/monitor/proxy-management/index')">
          <text>代理管理</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/monitor/index/index')">
          <text>监控面板</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/tasks/index/index')">
          <text>监控任务</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/polish/index/index')">
          <text>商品擦亮</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/logs/cookies-refresh/index')">
          <text>Cookie 刷新日志</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/logs/login-renew/index')">
          <text>登录续期日志</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/bot/delivery-rules/index')">
          <text>发货匹配规则</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/bot/comment-templates/index')">
          <text>评价模板</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/bot/item-reply/index')">
          <text>商品专属回复</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/bot/close-notice/index')">
          <text>关闭平台通知</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="goTo('/packages/monitor/audit-logs/index')">
          <text>审计日志</text>
          <text class="arrow">></text>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">账户安全</text>
        <view class="action-row" @click="showPasswordModal = true">
          <text>修改密码</text>
          <text class="arrow">></text>
        </view>
        <view class="action-row" @click="showServerSwitch">
          <text>后台地址</text>
          <text class="info-value server-url">{{ serverBaseLabel }}</text>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">关于</text>
        <view class="action-row" @click="showAbout">
          <text>关于 AI 鱼多宝</text>
          <text class="arrow">></text>
        </view>
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
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/store/modules/auth'
import { readServerBase } from '@/api/request'

const auth = useAuthStore()
const showPasswordModal = ref(false)
const newPwd = ref('')
const newPwdConfirm = ref('')
const loading = ref(false)
const serverBase = ref('')

const profileItems = computed(() => [
  { key: 'username', label: '用户名', value: auth.profile?.username },
  { key: 'displayName', label: '昵称', value: auth.profile?.displayName },
  { key: 'email', label: '邮箱', value: auth.profile?.email },
  { key: 'phone', label: '手机', value: auth.profile?.phone },
])

const serverBaseLabel = computed(() => serverBase.value || '默认（同源）')

function refreshServerBase() {
  serverBase.value = readServerBase()
}

function normalizeServerBase(input: string): string {
  const raw = String(input || '').trim()
  if (!raw || raw === '默认（同源）') return ''
  return raw.replace(/\/+$/, '')
}

function goTo(url: string) {
  uni.navigateTo({ url })
}

async function editItem(key: string) {
  if (key === 'username') {
    uni.showToast({ title: '用户名不可修改', icon: 'none' })
    return
  }
  const labelMap: Record<string, string> = {
    displayName: '昵称',
    email: '邮箱',
    phone: '手机',
  }
  const current = (auth.profile as any)?.[key] || ''
  uni.showModal({
    title: `编辑${labelMap[key] || key}`,
    editable: true,
    placeholderText: `请输入${labelMap[key] || key}`,
    content: String(current),
    success: async (m) => {
      if (!m.confirm) return
      try {
        await auth.updateProfile({ [key]: m.content || '' } as any)
        uni.showToast({ title: '已保存', icon: 'success' })
      } catch (e: any) {
        uni.showToast({ title: e?.message || '保存失败', icon: 'none' })
      }
    }
  })
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

function showServerSwitch() {
  const current = normalizeServerBase(serverBase.value) || '默认（同源）'
  uni.showActionSheet({
    itemList: ['默认（同源）', '自定义后台基础 URL'],
    success: (r) => {
      if (r.tapIndex === 1) {
        uni.showModal({
          title: '自定义后台基础 URL',
          editable: true,
          placeholderText: 'https://your-server.com',
          content: current === '默认（同源）' ? '' : current,
          success: (m) => {
            if (!m.confirm) return
            const base = normalizeServerBase(m.content || '')
            if (base) uni.setStorageSync('aiyudb_server_base', base)
            else uni.removeStorageSync('aiyudb_server_base')
            refreshServerBase()
            uni.showToast({ title: '已保存，立即生效', icon: 'none' })
          }
        })
      } else if (r.tapIndex === 0) {
        uni.removeStorageSync('aiyudb_server_base')
        refreshServerBase()
        uni.showToast({ title: '已切回同源，立即生效', icon: 'none' })
      }
    }
  })
}

function showAbout() {
  uni.showModal({
    title: '关于 AI 鱼多宝',
    content: '闲鱼卖家智能经营助手\n账号 · 商品 · 消息 · 订单 · 钱包 · AI 客服 · 关键词规则\nv1.0.0\n配置一次后台基础 URL 即可完成业务闭环',
    showCancel: false,
    confirmText: '知道了'
  })
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
  loading.value = true
  auth.fetchProfile().finally(() => { loading.value = false })
  refreshServerBase()
}

onShow(() => {
  refreshServerBase()
  if (auth.token) auth.fetchProfile()
})
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
