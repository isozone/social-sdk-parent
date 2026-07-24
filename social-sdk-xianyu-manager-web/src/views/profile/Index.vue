<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <!-- 个人信息卡片 -->
      <el-col :xs="24" :lg="12">
        <el-card class="profile-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><User /></el-icon>
                个人信息
              </span>
              <span class="card-sub">维护你的昵称、邮箱与手机号</span>
            </div>
          </template>

          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-position="top"
            v-loading="loading"
          >
            <el-form-item label="登录用户名" prop="username">
              <el-input v-model="profileForm.username" disabled />
            </el-form-item>
            <el-form-item label="角色" prop="roleLevel">
              <el-input :model-value="roleLabel" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="displayName">
              <el-input v-model="profileForm.displayName" placeholder="请输入昵称" maxlength="64" clearable />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" maxlength="128" clearable />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" placeholder="请输入手机号" maxlength="32" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">
                <el-icon><Check /></el-icon> 保存资料
              </el-button>
              <el-button @click="handleResetProfile" :disabled="savingProfile">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 修改密码卡片 -->
      <el-col :xs="24" :lg="12">
        <el-card class="profile-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Lock /></el-icon>
                修改密码
              </span>
              <span class="card-sub">定期更换密码以保障账号安全</span>
            </div>
          </template>

          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-position="top"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="pwdForm.oldPassword"
                type="password"
                show-password
                placeholder="请输入当前密码"
                autocomplete="off"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="pwdForm.newPassword"
                type="password"
                show-password
                placeholder="6-64 位字符"
                autocomplete="off"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwdForm.confirmPassword"
                type="password"
                show-password
                placeholder="再次输入新密码"
                autocomplete="off"
              />
            </el-form-item>
            <el-form-item>
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
import { User, Lock, Check, Key } from '@element-plus/icons-vue'
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

const roleLabel = computed(() => {
  const level = profileForm.roleLevel
  if (level === 9) return '超级管理员'
  if (level === 1) return '普通管理员'
  return level != null ? `管理员（等级 ${level}）` : '管理员'
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

async function loadProfile() {
  loading.value = true
  try {
    const res = await getProfile()
    if (res.success && res.data) {
      profileForm.id = res.data.id
      profileForm.username = res.data.username || ''
      profileForm.displayName = res.data.displayName || ''
      profileForm.roleLevel = res.data.roleLevel
      // email / phone 仅后端 update 后返回，初始可能缺；用 store 兜底
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
  max-width: 1080px;
  margin: 0 auto;
}
.profile-card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.card-title .el-icon {
  color: #4f46e5;
}
.card-sub {
  font-size: 12px;
  color: #909399;
}
.profile-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
  padding-bottom: 4px;
}
</style>
