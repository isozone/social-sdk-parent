<template>
  <div class="profile-page">
    <!-- 顶部身份 Hero -->
    <div class="profile-hero">
      <div class="hero-orb hero-orb-1" />
      <div class="hero-orb hero-orb-2" />
      <div class="hero-orb hero-orb-3" />
      <div class="hero-content">
        <div class="avatar-wrap">
          <div class="avatar">{{ avatarText }}</div>
          <span class="avatar-status" />
        </div>
        <div class="hero-info">
          <div class="hero-name-row">
            <h2 class="hero-name">{{ profileForm.displayName || '管理员' }}</h2>
            <span class="hero-role" :class="roleClass">{{ roleLabel }}</span>
          </div>
          <div class="hero-meta">
            <el-icon><User /></el-icon>
            <span>{{ profileForm.username || '—' }}</span>
            <span class="hero-dot">·</span>
            <el-icon><Postcard /></el-icon>
            <span>ID {{ profileForm.id || '—' }}</span>
          </div>
        </div>
        <div class="hero-actions">
          <el-button class="hero-btn" @click="scrollToSection('profile')">
            <el-icon><EditPen /></el-icon> 编辑资料
          </el-button>
          <el-button class="hero-btn" @click="scrollToSection('security')">
            <el-icon><Lock /></el-icon> 安全设置
          </el-button>
        </div>
      </div>
    </div>

    <el-row :gutter="20">
      <!-- 基本信息卡片 -->
      <el-col :xs="24" :lg="12">
        <el-card id="sec-profile" class="profile-card" shadow="hover">
          <div class="card-head">
            <div class="card-chip chip-violet">
              <el-icon><User /></el-icon>
            </div>
            <div class="card-head-text">
              <div class="card-title">基本信息</div>
              <div class="card-sub">管理你的昵称与联系方式</div>
            </div>
          </div>

          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-position="top"
            v-loading="loading"
            class="profile-form"
          >
            <el-form-item label="登录用户名" prop="username">
              <el-input v-model="profileForm.username" disabled>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="角色权限" prop="roleLevel">
              <el-input :model-value="roleLabel" disabled>
                <template #prefix><el-icon><Medal /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="昵称" prop="displayName">
              <el-input v-model="profileForm.displayName" placeholder="请输入昵称" maxlength="64" clearable>
                <template #prefix><el-icon><ChatDotRound /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" maxlength="128" clearable>
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" placeholder="请输入手机号" maxlength="32" clearable>
                <template #prefix><el-icon><Iphone /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item class="form-actions">
              <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">
                <el-icon><Check /></el-icon> 保存资料
              </el-button>
              <el-button @click="handleResetProfile" :disabled="savingProfile">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 安全设置卡片 -->
      <el-col :xs="24" :lg="12">
        <el-card id="sec-security" class="profile-card" shadow="hover">
          <div class="card-head">
            <div class="card-chip chip-cyan">
              <el-icon><Lock /></el-icon>
            </div>
            <div class="card-head-text">
              <div class="card-title">安全设置</div>
              <div class="card-sub">定期更新密码以保护账号安全</div>
            </div>
          </div>

          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-position="top"
            class="profile-form"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="pwdForm.oldPassword"
                type="password"
                show-password
                placeholder="请输入当前密码"
                autocomplete="off"
              >
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="pwdForm.newPassword"
                type="password"
                show-password
                placeholder="6-64 位字符"
                autocomplete="off"
              >
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
              <div class="pwd-tip">
                <el-icon><InfoFilled /></el-icon>
                建议使用字母、数字与符号的组合
              </div>
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwdForm.confirmPassword"
                type="password"
                show-password
                placeholder="再次输入新密码"
                autocomplete="off"
              >
                <template #prefix><el-icon><Select /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item class="form-actions">
              <el-button type="primary" :loading="savingPwd" @click="handleChangePassword">
                <el-icon><Key /></el-icon> 更新密码
              </el-button>
              <el-button @click="handleResetPwd" :disabled="savingPwd">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  User, Lock, Check, Key, Medal, Message, Iphone,
  ChatDotRound, Postcard, EditPen, InfoFilled, Select
} from '@element-plus/icons-vue'
import { getProfile, updateProfile, changePassword } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()

const loading = ref(false)
const savingProfile = ref(false)
const savingPwd = ref(false)

const profileFormRef = ref(null)
const pwdFormRef = ref(null)

const profileForm = reactive({
  id: null,
  username: '',
  displayName: '',
  email: '',
  phone: '',
  roleLevel: null
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const avatarText = computed(() => {
  const name = profileForm.displayName || profileForm.username || '管'
  return name.trim().charAt(0).toUpperCase()
})

const roleLabel = computed(() => {
  const level = profileForm.roleLevel
  if (level === 9) return '超级管理员'
  if (level === 1) return '普通管理员'
  return level != null ? `管理员（等级 ${level}）` : '管理员'
})

const roleClass = computed(() => {
  const level = profileForm.roleLevel
  if (level === 9) return 'role-super'
  if (level === 1) return 'role-normal'
  return 'role-other'
})

const profileRules = {
  displayName: [{ max: 64, message: '昵称长度不能超过 64', trigger: 'blur' }],
  email: [{ max: 128, message: '邮箱长度不能超过 128', trigger: 'blur' }],
  phone: [{ max: 32, message: '手机号长度不能超过 32', trigger: 'blur' }]
}

const validateConfirm = (rule, value, callback) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需为 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

function scrollToSection(id) {
  const el = document.getElementById('sec-' + id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function loadProfile() {
  loading.value = true
  try {
    const res = await getProfile()
    if (res.success && res.data) {
      profileForm.id = res.data.id
      profileForm.username = res.data.username || ''
      profileForm.displayName = res.data.displayName || ''
      profileForm.roleLevel = res.data.roleLevel
      profileForm.email = res.data.email || authStore.user?.email || ''
      profileForm.phone = res.data.phone || authStore.user?.phone || ''
    }
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleSaveProfile() {
  if (!profileFormRef.value) return
  try {
    await profileFormRef.value.validate()
  } catch (e) {
    return
  }
  savingProfile.value = true
  try {
    const res = await updateProfile({
      displayName: profileForm.displayName,
      email: profileForm.email,
      phone: profileForm.phone
    })
    if (res.success) {
      ElMessage.success('个人资料已保存')
      authStore.applyProfile(res.data || {})
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message || e))
  } finally {
    savingProfile.value = false
  }
}

function handleResetProfile() {
  loadProfile()
}

async function handleChangePassword() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  savingPwd.value = true
  try {
    const res = await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    if (res.success) {
      ElMessage.success('密码已更新，请使用新密码重新登录')
      pwdForm.oldPassword = ''
      pwdForm.newPassword = ''
      pwdForm.confirmPassword = ''
      pwdFormRef.value.clearValidate()
    } else {
      ElMessage.error(res.message || '修改失败')
    }
  } catch (e) {
    ElMessage.error('修改失败：' + (e.response?.data?.message || e.message || e))
  } finally {
    savingPwd.value = false
  }
}

function handleResetPwd() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.profile-page {
  width: 100%;
}

/* ===== Hero 头部 ===== */
.profile-hero {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  padding: 30px 34px;
  margin-bottom: 20px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  box-shadow: 0 18px 40px -18px rgba(79, 70, 229, 0.55);
}
.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(38px);
  opacity: 0.5;
  pointer-events: none;
}
.hero-orb-1 {
  width: 220px;
  height: 220px;
  top: -90px;
  right: -40px;
  background: #22d3ee;
}
.hero-orb-2 {
  width: 180px;
  height: 180px;
  bottom: -100px;
  left: 20px;
  background: #a855f7;
  opacity: 0.4;
}
.hero-orb-3 {
  width: 120px;
  height: 120px;
  top: 40px;
  left: 45%;
  background: #6366f1;
  opacity: 0.35;
}
.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
}
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}
.avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  font-weight: 700;
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border: 3px solid rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(4px);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.2);
}
.avatar-status {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #34d399;
  border: 3px solid #4f46e5;
}
.hero-info {
  flex: 1;
  min-width: 200px;
  color: #fff;
}
.hero-name-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.hero-name {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
}
.hero-role {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.16);
}
.hero-role.role-super {
  background: rgba(251, 191, 36, 0.22);
  border-color: rgba(251, 191, 36, 0.6);
}
.hero-role.role-normal {
  background: rgba(34, 211, 238, 0.18);
  border-color: rgba(34, 211, 238, 0.5);
}
.hero-meta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
}
.hero-meta .el-icon {
  font-size: 15px;
}
.hero-dot {
  margin: 0 4px;
  opacity: 0.6;
}
.hero-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}
.hero-btn {
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.45);
  color: #fff;
  backdrop-filter: blur(4px);
  transition: background 0.2s, transform 0.2s;
}
.hero-btn:hover {
  background: rgba(255, 255, 255, 0.28);
  color: #fff;
  transform: translateY(-1px);
}
.hero-btn .el-icon {
  margin-right: 4px;
}

/* ===== 卡片 ===== */
.profile-card {
  border-radius: 14px;
  border: 1px solid #ebeef5;
  margin-bottom: 20px;
  transition: box-shadow 0.25s, transform 0.25s;
}
.profile-card:hover {
  transform: translateY(-2px);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.card-chip {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  flex-shrink: 0;
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.22);
}
.chip-violet {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
}
.chip-cyan {
  background: linear-gradient(135deg, #06b6d4, #22d3ee);
  box-shadow: 0 8px 18px rgba(6, 182, 212, 0.25);
}
.card-head-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.card-sub {
  font-size: 12px;
  color: #909399;
}

.profile-form {
  margin-top: 22px;
}
.profile-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
  padding-bottom: 4px;
}
.profile-form :deep(.el-input__prefix) {
  color: #909399;
}
.form-actions {
  margin-top: 8px;
}
.pwd-tip {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.pwd-tip .el-icon {
  color: #22d3ee;
  font-size: 14px;
}

@media (max-width: 768px) {
  .profile-hero {
    padding: 24px 20px;
  }
  .hero-name {
    font-size: 20px;
  }
  .hero-actions {
    width: 100%;
  }
  .hero-btn {
    flex: 1;
  }
}
</style>
