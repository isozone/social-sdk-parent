<template>
  <view class="page-login">
    <view class="bg-gradient"></view>
    <view class="orb orb-1"></view>
    <view class="orb orb-2"></view>
    <view class="content">
      <view class="logo-wrap">
        <view class="logo">🐟</view>
        <text class="brand">AI 鱼多宝</text>
        <text class="tagline">闲鱼卖家智能经营助手</text>
      </view>

      <view class="glass">
        <view class="input-group">
          <view class="input-label">用户名</view>
          <view class="input-prefix">
            <text class="prefix-icon">👤</text>
            <input v-model="username" type="text" placeholder="请输入用户名或手机号" />
          </view>
        </view>

        <view class="input-group">
          <view class="input-label">密码</view>
          <view class="input-suffix">
            <text class="prefix-icon">🔒</text>
            <input v-model="password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" />
            <text class="show-pwd" @click="showPwd = !showPwd">{{ showPwd ? '🙈' : '👁️' }}</text>
          </view>
        </view>

        <view class="remember-row">
          <view class="remember" @click="remember = !remember">
            <view class="check" :class="{ on: remember }">✓</view>
            <text>记住我</text>
          </view>
          <text class="forgot" @click="showForgotTip">忘记密码？</text>
        </view>

        <button class="login-btn" :disabled="loading" @click="onLogin">登 录</button>

        <view class="divider">其他登录方式</view>
        <view class="quick-logins">
          <view class="quick-btn" @click="quickDemoLogin">
            <text>🚀</text>
            <text class="quick-label">演示账号</text>
          </view>
          <view class="quick-btn" @click="showServerSwitch">
            <text>🌐</text>
            <text class="quick-label">服务器</text>
          </view>
          <view class="quick-btn" @click="showAbout">
            <text>ℹ️</text>
            <text class="quick-label">关于</text>
          </view>
        </view>
      </view>
    </view>

    <view class="slogan">随时随地 掌舵经营</view>
    <view class="bottom-indicator"></view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/store/modules/auth'

const auth = useAuthStore()
const username = ref('')
const password = ref('')
const showPwd = ref(false)
const loading = ref(false)
const remember = ref(true)

onMounted(() => {
  // 记住登录态闭环：上次勾选「记住我」成功登录后，下次自动回填用户名
  try {
    const savedUser = uni.getStorageSync('aiyudb_remember_user')
    if (savedUser) {
      username.value = savedUser
      remember.value = true
    }
    const savedServer = uni.getStorageSync('aiyudb_server_base')
    if (savedServer) {
      // 服务器地址闭环：上次切换的环境本轮自动生效（request.ts 启动时也会读）
    }
  } catch {}
})

async function onLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    if (remember.value) {
      uni.setStorageSync('aiyudb_remember_user', username.value)
    } else {
      uni.removeStorageSync('aiyudb_remember_user')
    }
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' })
    }, 500)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function quickDemoLogin() {
  // 演示账号一键登录闭环：用后端 application.yml 配置的默认 admin/admin123
  uni.showModal({
    title: '演示账号登录',
    content: '将使用演示账号 admin / admin123 登录，仅用于功能体验',
    success: async (r) => {
      if (!r.confirm) return
      loading.value = true
      try {
        username.value = 'admin'
        password.value = 'admin123'
        await auth.login('admin', 'admin123')
        uni.setStorageSync('aiyudb_remember_user', 'admin')
        uni.showToast({ title: '演示账号登录成功', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 500)
      } catch (e: any) {
        uni.showToast({ title: e?.message || '演示账号未配置', icon: 'none' })
      } finally {
        loading.value = false
      }
    }
  })
}

function normalizeServerBase(input: string): string {
  const raw = String(input || '').trim()
  if (!raw || raw === '默认（同源）') return ''
  return raw.replace(/\/+$/, '')
}

function showServerSwitch() {
  // 服务器地址切换闭环：写入 aiyudb_server_base 后，request/upload 实时读取，无需重启
  const current = normalizeServerBase(uni.getStorageSync('aiyudb_server_base')) || '默认（同源）'
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
            if (m.confirm && m.content) {
              const base = normalizeServerBase(m.content)
              if (base) uni.setStorageSync('aiyudb_server_base', base)
              else uni.removeStorageSync('aiyudb_server_base')
              uni.showToast({ title: '已保存，立即生效', icon: 'none' })
            }
          }
        })
      } else if (r.tapIndex === 0) {
        uni.removeStorageSync('aiyudb_server_base')
        uni.showToast({ title: '已切回同源，立即生效', icon: 'none' })
      }
    }
  })
}

function showAbout() {
  uni.showModal({
    title: '关于 AI 鱼多宝',
    content: '闲鱼卖家智能经营助手\n账号 · 商品 · 消息 · 订单 · 钱包 · AI 客服 · 关键词规则\nv1.0.0',
    showCancel: false,
    confirmText: '知道了'
  })
}

function showForgotTip() {
  // 管理后台无 OAuth/短信找回，闭环：提示联系管理员重置（后端去原密码校验后用户可自行改）
  uni.showModal({
    title: '忘记密码',
    content: '请联系管理员重置密码，或登录后在「个人中心 → 修改密码」直接设置新密码（无需原密码）',
    showCancel: false,
    confirmText: '知道了'
  })
}
</script>

<style scoped lang="scss">
.page-login {
  width: 100%;
  min-height: 100vh;
  background: #0a0f1e;
  position: relative;
  overflow: hidden;
  color: #f9fafb;
}

.bg-gradient {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(180deg, #0a0f1e 0%, #1a1a2e 50%, #0a0f1e 100%);
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  z-index: 0;
  animation: float 8s ease-in-out infinite;

  &-1 {
    width: 360rpx;
    height: 360rpx;
    top: -80rpx;
    left: -100rpx;
    background: radial-gradient(circle, rgba(124, 58, 237, 0.45), transparent 70%);
  }

  &-2 {
    width: 300rpx;
    height: 300rpx;
    bottom: -60rpx;
    right: -80rpx;
    background: radial-gradient(circle, rgba(34, 211, 238, 0.3), transparent 70%);
    animation-delay: 2s;
  }
}

.content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 56rpx 120rpx;
}

.logo-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 8rpx;
}

.logo {
  width: 164rpx;
  height: 164rpx;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #4f46e5, #22d3ee);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 80rpx;
  margin-bottom: 36rpx;
  box-shadow: 0 28rpx 88rpx rgba(79, 70, 229, 0.4);
  position: relative;
}

.logo::after {
  content: '';
  position: absolute;
  inset: -10rpx;
  border-radius: 56rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.12);
}

.brand {
  font-size: 60rpx;
  font-weight: 700;
  color: #f9fafb;
  letter-spacing: 3rpx;
}

.tagline {
  font-size: 26rpx;
  color: rgba(156, 163, 175, 0.7);
  margin-top: 12rpx;
  letter-spacing: 1rpx;
}

.glass {
  width: 100%;
  margin-top: 68rpx;
  background: rgba(17, 24, 39, 0.6);
  backdrop-filter: blur(24rpx);
  border: 2rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 52rpx;
  padding: 56rpx 44rpx;
  box-shadow: 0 56rpx 160rpx rgba(0, 0, 0, 0.35);
}

.input-group {
  margin-bottom: 36rpx;
}

.input-label {
  display: block;
  font-size: 24rpx;
  font-weight: 500;
  color: #9ca3af;
  margin-bottom: 16rpx;
  letter-spacing: 1rpx;
}

.input-prefix,
.input-suffix {
  position: relative;
}

.prefix-icon {
  position: absolute;
  left: 30rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 44rpx;
}

.input-suffix .prefix-icon {
  left: 30rpx;
}

.input-field,
input {
  width: 100%;
  height: 104rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 2rpx solid rgba(255, 255, 255, 0.1);
  border-radius: 28rpx;
  padding: 0 84rpx;
  font-size: 30rpx;
  color: #f9fafb;
  outline: none;
  transition: all 0.3s;
}

input:focus {
  border-color: rgba(99, 102, 241, 0.7);
  background: rgba(79, 70, 229, 0.1);
  box-shadow: 0 0 0 8rpx rgba(79, 70, 229, 0.15);
}

input::placeholder {
  color: #6b7280;
}

.input-suffix input {
  padding-right: 164rpx;
}

.show-pwd {
  position: absolute;
  right: 28rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 44rpx;
}

.remember-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 44rpx;
}

.remember {
  display: flex;
  align-items: center;
  gap: 14rpx;
  font-size: 24rpx;
  color: #9ca3af;
}

.check {
  width: 34rpx;
  height: 34rpx;
  border-radius: 10rpx;
  border: 3rpx solid rgba(255, 255, 255, 0.2);
  background: rgba(99, 102, 241, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: transparent;
  transition: all 0.2s;
}

.check.on {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-color: transparent;
  color: #fff;
}

.forgot {
  font-size: 24rpx;
  color: #a78bfa;
  text-decoration: none;
}

.login-btn {
  width: 100%;
  height: 108rpx;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border: none;
  border-radius: 32rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 3rpx;
  box-shadow: 0 16rpx 64rpx rgba(79, 70, 229, 0.45);
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.login-btn:active {
  transform: translateY(0) scale(0.98);
}

.login-btn[disabled] {
  opacity: 0.6;
}

.divider {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin: 44rpx 0 32rpx;
  color: #6b7280;
  font-size: 24rpx;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 2rpx;
  background: rgba(255, 255, 255, 0.06);
}

.quick-logins {
  display: flex;
  justify-content: center;
  gap: 36rpx;
}

.quick-btn {
  width: 108rpx;
  height: 108rpx;
  border-radius: 36rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 2rpx solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  font-size: 40rpx;
  transition: all 0.25s;
  color: #9ca3af;
}

.quick-btn:active {
  background: rgba(255, 255, 255, 0.1);
  color: #f9fafb;
  transform: translateY(-6rpx);
  border-color: rgba(255, 255, 255, 0.15);
}

.quick-label {
  font-size: 20rpx;
  letter-spacing: 1rpx;
}

.slogan {
  position: fixed;
  bottom: 104rpx;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 24rpx;
  color: rgba(156, 163, 175, 0.25);
  letter-spacing: 2rpx;
  z-index: 5;
}

.bottom-indicator {
  position: fixed;
  bottom: 16rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 268rpx;
  height: 10rpx;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 6rpx;
  z-index: 100;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(28rpx, -32rpx); }
}
</style>
