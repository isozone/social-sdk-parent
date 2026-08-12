<template>
  <div class="page-root">
    <el-card shadow="never" class="accounts-page">
      <!-- ===== 头部 ===== -->
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-violet">
            <el-icon><User /></el-icon>
          </div>
          <div class="card-head-text">
            <div class="card-title">账号管理</div>
            <div class="card-sub">管理与维护闲鱼账号状态</div>
          </div>
        </div>
        <el-button type="primary" @click="showLoginDialog = true">
          <el-icon><Plus /></el-icon> 添加账号
        </el-button>
      </div>

      <!-- ===== 列表 ===== -->
      <el-table :data="accounts" stripe v-loading="loading" class="accounts-table">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="accountName" label="账号名称" width="150">
          <template #default="{ row }">
            <span class="acc-name">{{ row.accountName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="昵称" width="150">
          <template #default="{ row }">
            <span class="acc-display">{{ row.displayName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light" round>{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="最后登录" width="180">
          <template #default="{ row }">
            <span class="time-cell">{{ formatTime(row.lastLoginAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="warning" text @click="editAccount(row)">编辑</el-button>
            <el-button
              v-if="['COOKIE_EXPIRED','OFFLINE','FROZEN'].includes(row.status)"
              size="small" type="danger" text
              @click="reloginAccount(row)"
            >重新登录</el-button>
            <el-button size="small" text @click="editStatus(row)">切换状态</el-button>
            <el-button size="small" type="danger" text @click="deleteAccount(row.id)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无账号" /></template>
      </el-table>
    </el-card>

    <!-- 添加账号对话框 -->
    <el-dialog v-model="showLoginDialog" title="添加账号" width="520px" destroy-on-close center>
      <el-tabs v-model="loginMode" class="login-tabs">
        <el-tab-pane label="二维码登录" name="qr" />
        <el-tab-pane label="Cookie 登录" name="cookie" />
      </el-tabs>

      <div v-if="loginMode === 'qr'" class="dialog-body">
        <el-form :model="qrForm" label-width="80px">
          <el-form-item label="账号名称">
            <el-input v-model="qrForm.accountName" placeholder="如：账号A" clearable />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="qrForm.remark" placeholder="可选" clearable />
          </el-form-item>
        </el-form>
        <div v-if="qrState.loading" class="qr-wrap">
          <el-icon class="is-loading" :size="40"><Loading /></el-icon>
          <p>正在生成二维码...</p>
        </div>
        <div v-else-if="qrState.qrCodeDataUrl" class="qr-wrap">
          <img :src="qrState.qrCodeDataUrl" alt="二维码" class="qr-image" />
          <p class="qr-tip">请使用闲鱼 APP 扫码登录</p>
          <p v-if="qrState.status === 'SCANNED'" class="qr-msg qr-scanned">
            <el-icon><SuccessFilled /></el-icon> 已扫码，请在手机上确认
          </p>
          <p v-if="qrState.status === 'VERIFICATION_REQUIRED'" class="qr-msg">
            <el-alert title="需要验证" type="warning" :closable="false" show-icon />
          </p>
          <p v-if="qrState.status === 'EXPIRED'" class="qr-msg">
            <el-alert title="二维码已过期" type="error" :closable="false" show-icon>
              <template #default>请重新生成二维码</template>
            </el-alert>
          </p>
          <p v-if="qrState.status === 'ERROR'" class="qr-msg">
            <el-alert :title="qrState.message || '生成失败'" type="error" :closable="false" show-icon />
          </p>
        </div>
        <div v-else-if="qrState.error" class="qr-wrap">
          <el-alert :title="qrState.message || '生成失败'" type="error" :closable="false" show-icon />
        </div>
        <div class="qr-actions" v-if="qrState.qrCodeDataUrl || qrState.error">
          <el-button v-if="qrState.status === 'EXPIRED'" type="primary" @click="refreshQrCode">
            <el-icon><Refresh /></el-icon> 刷新二维码
          </el-button>
          <el-button v-if="['WAITING','SCANNED'].includes(qrState.status)" type="danger" plain @click="cancelQrLogin">
            <el-icon><Close /></el-icon> 取消登录
          </el-button>
        </div>
      </div>

      <div v-if="loginMode === 'cookie'" class="dialog-body">
        <el-form :model="loginForm" label-width="80px">
          <el-form-item label="账号名称">
            <el-input v-model="loginForm.accountName" placeholder="如：账号A" clearable />
          </el-form-item>
          <el-form-item label="Cookie">
            <el-input v-model="loginForm.cookieHeader" type="textarea" :rows="4" placeholder="粘贴 Cookie 字符串" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="loginForm.remark" placeholder="可选" clearable />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button
          v-if="loginMode === 'cookie'" type="primary" :loading="submitting"
          @click="handleCookieLogin"
        >确定</el-button>
        <el-button
          v-else type="primary" :loading="qrState.submitting" :disabled="!qrForm.accountName"
          @click="handleQrLogin"
        >生成二维码</el-button>
      </template>
    </el-dialog>

    <!-- 切换状态对话框 -->
    <el-dialog v-model="showStatusDialog" title="切换状态" width="360px" center>
      <el-form :model="statusForm" label-width="80px">
        <el-form-item label="状态">
          <el-select v-model="statusForm.status" style="width:100%;">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
            <el-option label="冻结" value="FROZEN" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="statusForm.remark" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showStatusDialog = false">取消</el-button>
        <el-button type="primary" @click="handleStatusUpdate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑账号对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑账号" width="520px" center>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="账号名称">
          <el-input v-model="editForm.accountName" placeholder="账号名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%;">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
            <el-option label="冻结" value="FROZEN" />
            <el-option label="Cookie 过期" value="COOKIE_EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cookie">
          <el-input v-model="editForm.cookieHeader" type="textarea" :rows="6" placeholder="粘贴新的 Cookie 字符串以更换；留空则保持原 Cookie 不变" />
          <div style="font-size:12px;color:var(--text-3);margin-top:4px;">传入新 Cookie 会自动重置登录时间并将状态置为 ACTIVE</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" placeholder="可选" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重新登录二维码对话框 -->
    <el-dialog v-model="showReloginDialog" title="重新登录" width="420px" center>
      <el-alert
        v-if="reloginForm.status === 'COOKIE_EXPIRED'"
        title="该账号 Cookie 已过期，请用闲鱼 APP 扫码重新登录"
        type="warning" :closable="false" show-icon
        style="margin-bottom:16px;"
      />
      <el-alert
        v-else
        title="该账号处于离线状态，请扫码重新登录"
        type="info" :closable="false" show-icon
        style="margin-bottom:16px;"
      />

      <div style="margin-bottom:12px;font-weight:600;">账号：<span class="brand-gradient-text">{{ reloginForm.accountName }}</span></div>

      <div v-if="reloginState.loading" class="qr-wrap">
        <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        <p>正在生成二维码...</p>
      </div>
      <div v-else-if="reloginState.qrCodeDataUrl" class="qr-wrap">
        <img :src="reloginState.qrCodeDataUrl" alt="二维码" class="qr-image" />
        <p class="qr-tip">请使用闲鱼 APP 扫码登录</p>
        <p v-if="reloginState.status === 'SCANNED'" class="qr-msg qr-scanned">
          <el-icon><SuccessFilled /></el-icon> 已扫码，请在手机上确认
        </p>
        <p v-if="reloginState.status === 'EXPIRED'" class="qr-msg">
          <el-alert title="二维码已过期" type="error" :closable="false" show-icon>
            <template #default>请重新生成二维码</template>
          </el-alert>
        </p>
      </div>
      <div v-else-if="reloginState.error" class="qr-wrap">
        <el-alert :title="reloginState.message || '生成失败'" type="error" :closable="false" show-icon />
      </div>

      <div class="qr-actions" v-if="reloginState.qrCodeDataUrl || reloginState.error">
        <el-button v-if="reloginState.status === 'EXPIRED'" type="primary" @click="refreshReloginQr">
          <el-icon><Refresh /></el-icon> 刷新二维码
        </el-button>
        <el-button v-if="['WAITING','SCANNED'].includes(reloginState.status)" type="danger" plain @click="cancelRelogin">
          <el-icon><Close /></el-icon> 取消登录
        </el-button>
      </div>

      <template #footer>
        <el-button @click="closeReloginDialog">关闭</el-button>
        <el-button type="primary" :loading="reloginState.submitting" :disabled="!!reloginState.qrCodeDataUrl" @click="handleReloginQr">生成二维码</el-button>
      </template>
    </el-dialog>

    <!-- 账号详情抽屉 -->
    <el-drawer v-model="showDetailDrawer" :title="`账号详情 — ${detailForm.accountName || ''}`" size="560px" destroy-on-close>
      <div v-loading="detailLoading" class="drawer-body">
        <!-- 头像 + 基本信息 -->
        <div class="detail-header">
          <el-avatar :size="72" :src="detailForm.avatar" class="detail-avatar">
            {{ detailForm.displayName ? detailForm.displayName.charAt(0) : '?' }}
          </el-avatar>
          <div>
            <h3 class="detail-name">{{ detailForm.displayName || '—' }}</h3>
            <p class="detail-account">{{ detailForm.accountName }}</p>
            <el-tag :type="statusType(detailForm.status)" size="small" round>{{ statusLabel(detailForm.status) }}</el-tag>
          </div>
        </div>

        <!-- 统计 -->
        <div class="stat-grid">
          <div class="stat-item">
            <div class="metric-value" style="color:var(--brand-2);">{{ detailForm.followers || 0 }}</div>
            <div class="metric-label">粉丝</div>
          </div>
          <div class="stat-item">
            <div class="metric-value" style="color:var(--color-success);">{{ detailForm.following || 0 }}</div>
            <div class="metric-label">关注</div>
          </div>
          <div class="stat-item">
            <div class="metric-value" style="color:var(--color-warning);">{{ detailForm.soldCount || 0 }}</div>
            <div class="metric-label">卖出</div>
          </div>
          <div class="stat-item">
            <div class="metric-value" style="color:var(--color-danger);">{{ detailForm.purchaseCount || 0 }}</div>
            <div class="metric-label">买过</div>
          </div>
        </div>

        <!-- 详细资料 -->
        <el-descriptions :column="2" border class="detail-desc" size="default">
          <el-descriptions-item label="用户ID">{{ detailForm.userId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="IP 属地">{{ detailForm.ipLocation || '—' }}</el-descriptions-item>
          <el-descriptions-item label="个人简介" :span="2">{{ detailForm.introduction || '—' }}</el-descriptions-item>
          <el-descriptions-item label="在售宝贝">{{ detailForm.onSaleCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="收藏数">{{ detailForm.collectionCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="店铺等级">{{ detailForm.shopLevel || '—' }}</el-descriptions-item>
          <el-descriptions-item label="信用分">{{ detailForm.creditScore || 0 }}</el-descriptions-item>
          <el-descriptions-item label="评价数">{{ detailForm.reviewNum || 0 }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailForm.remark || '—' }}</el-descriptions-item>
          <el-descriptions-item label="最后登录">{{ formatTime(detailForm.lastLoginAt) }}</el-descriptions-item>
          <el-descriptions-item label="上次同步">{{ formatTime(detailForm.profileSyncedAt) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 操作 -->
        <div class="detail-actions">
          <el-button type="primary" :loading="detailSyncing" @click="syncProfile" class="full-btn">
            <el-icon><Refresh /></el-icon> 刷新实时数据
          </el-button>
          <el-button @click="copyCookie" class="full-btn">复制 Cookie</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Plus, Refresh, Avatar, ChatLineSquare,
  Loading, SuccessFilled, Close, UserFilled
} from '@element-plus/icons-vue'
import api from '@/api/request'

// ==================== 工具函数 ====================
function formatTime(t) {
  if (!t) return '—'
  return t.replace('T', ' ').substring(0, 19)
}

const statusMap = {
  ACTIVE: { type: 'success', label: '在线' },
  DISABLED: { type: 'info', label: '离线' },
  FROZEN: { type: 'danger', label: '冻结' },
  COOKIE_EXPIRED: { type: 'warning', label: '过期' },
  OFFLINE: { type: 'info', label: '离线' }
}
function statusType(s) { return (statusMap[s] || statusMap.OFFLINE).type }
function statusLabel(s) { return (statusMap[s] || statusMap.OFFLINE).label }

// ==================== 列表 ====================
const accounts = ref([])
const loading = ref(false)

async function loadAccounts() {
  loading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
    else ElMessage.error(res.message || '加载账号列表失败')
  } catch (e) { /* 拦截器已提示 */ }
  finally { loading.value = false }
}

// ==================== 状态切换 ====================
const showStatusDialog = ref(false)
const statusForm = ref({ id: null, status: 'ACTIVE', remark: '' })

function editStatus(row) {
  statusForm.value = { id: row.id, status: row.status, remark: row.remark || '' }
  showStatusDialog.value = true
}

async function handleStatusUpdate() {
  try {
    const res = await api.put(`/accounts/${statusForm.value.id}/status`, {
      status: statusForm.value.status,
      remark: statusForm.value.remark
    })
    if (res.success) {
      ElMessage.success('状态已更新')
      showStatusDialog.value = false
      await loadAccounts()
    } else {
      ElMessage.error(res.message || '状态更新失败')
    }
  } catch (e) { /* 拦截器已提示 */ }
}

async function deleteAccount(id) {
  await ElMessageBox.confirm('确认删除该账号？', '提示', { type: 'warning' })
  try {
    const res = await api.delete(`/accounts/${id}`)
    if (res.success) {
      ElMessage.success('已删除')
      await loadAccounts()
    } else {
      ElMessage.error(res.message || '删除账号失败')
    }
  } catch (e) { /* 拦截器已提示 */ }
}

// ==================== 编辑账号 ====================
const showEditDialog = ref(false)
const editSubmitting = ref(false)
const editForm = ref({ id: null, accountName: '', status: 'ACTIVE', cookieHeader: '', remark: '' })

function editAccount(row) {
  editForm.value = {
    id: row.id,
    accountName: row.accountName || '',
    status: row.status || 'ACTIVE',
    cookieHeader: '',
    remark: row.remark || ''
  }
  showEditDialog.value = true
}

async function handleEdit() {
  if (!editForm.value.id) return
  if (!editForm.value.accountName) {
    ElMessage.warning('请填写账号名称')
    return
  }
  editSubmitting.value = true
  try {
    const payload = {
      accountName: editForm.value.accountName,
      status: editForm.value.status,
      remark: editForm.value.remark
    }
    if (editForm.value.cookieHeader && editForm.value.cookieHeader.trim()) {
      payload.cookieHeader = editForm.value.cookieHeader.trim()
    }
    const res = await api.put(`/accounts/${editForm.value.id}`, payload)
    if (res.success) {
      ElMessage.success('账号已更新')
      showEditDialog.value = false
      await loadAccounts()
    }
  } catch (e) { /* handled by interceptor */ }
  finally { editSubmitting.value = false }
}

// ==================== 重新登录 ====================
const showReloginDialog = ref(false)
const reloginForm = ref({ id: null, accountName: '', status: '' })
const reloginState = ref({
  loading: false, sessionId: null, status: null,
  qrCodeDataUrl: null, message: null, error: false, submitting: false
})
let reloginPollTimer = null

function resetReloginState() {
  reloginState.value = {
    loading: false, sessionId: null, status: null,
    qrCodeDataUrl: null, message: null, error: false, submitting: false
  }
}

function stopReloginPolling() {
  if (reloginPollTimer) { clearInterval(reloginPollTimer); reloginPollTimer = null }
}

function closeReloginDialog() {
  showReloginDialog.value = false
  stopReloginPolling()
  resetReloginState()
}

async function handleReloginQr() {
  if (!reloginForm.value.id) return
  reloginState.value.submitting = true
  reloginState.value.loading = true
  reloginState.value.error = false

  try {
    const res = await api.post('/accounts/qr-login', {
      accountName: reloginForm.value.accountName,
      accountId: reloginForm.value.id
    })
    if (res.success && res.data) {
      const data = res.data
      Object.assign(reloginState.value, {
        sessionId: data.sessionId, qrCodeDataUrl: data.qrCodeDataUrl,
        status: data.status, message: data.message, loading: false
      })
      if (data.status === 'SUCCESS') {
        ElMessage.success('重新登录成功，Cookie 已更新')
        closeReloginDialog()
        await loadAccounts()
      } else {
        startReloginPolling()
      }
    } else {
      reloginState.value.error = true
      reloginState.value.message = res.message || '生成二维码失败'
      reloginState.value.loading = false
    }
  } catch (e) {
    reloginState.value.error = true
    reloginState.value.message = '生成二维码失败: ' + (e.message || '未知错误')
    reloginState.value.loading = false
  } finally {
    reloginState.value.submitting = false
  }
}

function startReloginPolling() {
  stopReloginPolling()
  reloginPollTimer = setInterval(async () => {
    if (!reloginState.value.sessionId) return
    try {
      const res = await api.get('/accounts/qr-login/status', { params: { sessionId: reloginState.value.sessionId } })
      if (res.success && res.data) {
        const data = res.data
        reloginState.value.status = data.status
        reloginState.value.message = data.message

        if (data.status === 'SUCCESS') {
          stopReloginPolling()
          ElMessage.success('重新登录成功，Cookie 已更新')
          closeReloginDialog()
          await loadAccounts()
        } else if (data.status === 'SCANNED') {
          reloginState.value.message = '已扫码，请在手机上确认'
        } else if (['EXPIRED','CANCELLED','ERROR'].includes(data.status)) {
          stopReloginPolling()
          if (data.status === 'CANCELLED') {
            ElMessage.info('已取消登录')
            closeReloginDialog()
          } else {
            reloginState.value.error = true
            reloginState.value.message = data.message || '登录失败或已过期'
          }
        }
      }
    } catch (e) { /* polling error */ }
  }, 3000)
}

async function refreshReloginQr() {
  resetReloginState()
  reloginState.value.loading = true
  await handleReloginQr()
}

async function cancelRelogin() {
  stopReloginPolling()
  reloginState.value.status = 'CANCELLED'
  ElMessage.info('已取消登录')
  closeReloginDialog()
}

function reloginAccount(row) {
  reloginForm.value = {
    id: row.id,
    accountName: row.accountName || row.displayName || ('账号#' + row.id),
    status: row.status || ''
  }
  resetReloginState()
  showReloginDialog.value = true
  handleReloginQr()
}

// ==================== Cookie 登录 ====================
const showLoginDialog = ref(false)
const loginMode = ref('qr')
const submitting = ref(false)
const loginForm = ref({ accountName: '', cookieHeader: '', remark: '' })

async function handleCookieLogin() {
  if (!loginForm.value.accountName || !loginForm.value.cookieHeader) {
    ElMessage.warning('请填写账号名称和 Cookie')
    return
  }
  submitting.value = true
  try {
    const res = await api.post('/accounts/login', loginForm.value)
    if (res.success) {
      ElMessage.success('账号添加成功')
      closeDialog()
      await loadAccounts()
    }
  } catch (e) { /* handled by interceptor */ }
  finally { submitting.value = false }
}

// ==================== 二维码登录 ====================
const qrForm = ref({ accountName: '', remark: '' })
const qrState = ref({
  loading: false, sessionId: null, status: null,
  qrCodeDataUrl: null, message: null, error: false, submitting: false
})
let qrPollTimer = null

function closeDialog() {
  showLoginDialog.value = false
  stopQrPolling()
  resetQrState()
  loginMode.value = 'qr'
  qrForm.value = { accountName: '', remark: '' }
  loginForm.value = { accountName: '', cookieHeader: '', remark: '' }
}

function resetQrState() {
  qrState.value = {
    loading: false, sessionId: null, status: null,
    qrCodeDataUrl: null, message: null, error: false, submitting: false
  }
}

function stopQrPolling() {
  if (qrPollTimer) { clearInterval(qrPollTimer); qrPollTimer = null }
}

async function handleQrLogin() {
  if (!qrForm.value.accountName) {
    ElMessage.warning('请填写账号名称')
    return
  }
  qrState.value.submitting = true
  qrState.value.loading = true
  qrState.value.error = false

  try {
    const res = await api.post('/accounts/qr-login', qrForm.value)
    if (res.success && res.data) {
      const data = res.data
      Object.assign(qrState.value, {
        sessionId: data.sessionId, qrCodeDataUrl: data.qrCodeDataUrl,
        status: data.status, message: data.message, loading: false
      })
      if (data.status === 'SUCCESS') {
        ElMessage.success('二维码登录成功！账号已添加')
        closeDialog()
        await loadAccounts()
      } else {
        startQrPolling()
      }
    } else {
      qrState.value.error = true
      qrState.value.message = res.message || '生成二维码失败'
      qrState.value.loading = false
    }
  } catch (e) {
    qrState.value.error = true
    qrState.value.message = '生成二维码失败: ' + (e.message || '未知错误')
    qrState.value.loading = false
  } finally {
    qrState.value.submitting = false
  }
}

function startQrPolling() {
  stopQrPolling()
  qrPollTimer = setInterval(async () => {
    if (!qrState.value.sessionId) return
    try {
      const res = await api.get('/accounts/qr-login/status', { params: { sessionId: qrState.value.sessionId } })
      if (res.success && res.data) {
        const data = res.data
        qrState.value.status = data.status
        qrState.value.message = data.message
        if (data.status === 'SUCCESS') {
          stopQrPolling()
          ElMessage.success('二维码登录成功！账号已添加')
          closeDialog()
          await loadAccounts()
        } else if (data.status === 'SCANNED') {
          qrState.value.message = '已扫码，请在手机上确认'
        } else if (['EXPIRED','CANCELLED','ERROR'].includes(data.status)) {
          stopQrPolling()
          if (data.status === 'CANCELLED') {
            ElMessage.info('已取消登录')
            closeDialog()
          } else {
            qrState.value.error = true
            qrState.value.message = data.message || '登录失败或已过期'
          }
        }
      }
    } catch (e) { /* polling error */ }
  }, 3000)
}

async function refreshQrCode() {
  resetQrState()
  qrState.value.loading = true
  await handleQrLogin()
}

async function cancelQrLogin() {
  stopQrPolling()
  qrState.value.status = 'CANCELLED'
  ElMessage.info('已取消登录')
  closeDialog()
}

// ==================== 详情抽屉 ====================
const showDetailDrawer = ref(false)
const detailLoading = ref(false)
const detailSyncing = ref(false)
const detailForm = ref({
  id: null, accountName: '', displayName: '', userId: '', avatar: '', status: '',
  remark: '', introduction: '', ipLocation: '', followers: 0, following: 0,
  soldCount: 0, purchaseCount: 0, collectionCount: 0, onSaleCount: 0,
  shopLevel: '', creditScore: 0, reviewNum: 0, lastLoginAt: '', profileSyncedAt: '', cookieHeader: ''
})

async function viewDetail(row) {
  detailForm.value = { ...row }
  showDetailDrawer.value = true
  detailLoading.value = true
  try {
    const res = await api.get(`/accounts/${row.id}/profile`)
    if (res.success && res.data) {
      const d = res.data
      Object.assign(detailForm.value, {
        displayName: d.displayName || detailForm.value.displayName,
        avatar: d.avatar || detailForm.value.avatar,
        introduction: d.introduction || '',
        ipLocation: d.ipLocation || '',
        followers: d.followers || 0, following: d.following || 0,
        soldCount: d.soldCount || 0, purchaseCount: d.purchaseCount || 0,
        collectionCount: d.collectionCount || 0, onSaleCount: d.onSaleCount || 0,
        shopLevel: d.shopLevel || '', creditScore: d.creditScore || 0, reviewNum: d.reviewNum || 0
      })
    } else {
      detailForm.value = { ...detailForm.value, status: 'COOKIE_EXPIRED' }
      ElMessage.warning(res.message || '实时数据获取失败，请检查 Cookie 状态')
      await loadAccounts()
    }
  } catch (e) { /* keep cached data */ }
  finally { detailLoading.value = false }
}

async function syncProfile() {
  if (!detailForm.value.id) return
  detailSyncing.value = true
  try {
    const res = await api.post(`/accounts/${detailForm.value.id}/profile/sync`)
    if (res.success && res.data) {
      const d = res.data
      Object.assign(detailForm.value, {
        displayName: d.displayName || detailForm.value.displayName,
        avatar: d.avatar || detailForm.value.avatar,
        introduction: d.introduction || '',
        ipLocation: d.ipLocation || '',
        followers: d.followers || 0, following: d.following || 0,
        soldCount: d.soldCount || 0, purchaseCount: d.purchaseCount || 0,
        collectionCount: d.collectionCount || 0, onSaleCount: d.onSaleCount || 0,
        shopLevel: d.shopLevel || '', creditScore: d.creditScore || 0, reviewNum: d.reviewNum || 0,
        profileSyncedAt: d.syncedAt || ''
      })
      ElMessage.success('数据已同步到数据库')
      await loadAccounts()
    } else {
      ElMessage.warning(res.message || '同步失败，请检查 Cookie 状态')
      await loadAccounts()
    }
  } catch (e) { ElMessage.error('同步失败') }
  finally { detailSyncing.value = false }
}

async function copyCookie() {
  if (!detailForm.value.cookieHeader) {
    ElMessage.warning('该账号暂无 Cookie')
    return
  }
  try {
    await navigator.clipboard.writeText(detailForm.value.cookieHeader)
    ElMessage.success('Cookie 已复制到剪贴板')
  } catch (e) {
    const textarea = document.createElement('textarea')
    textarea.value = detailForm.value.cookieHeader
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('Cookie 已复制到剪贴板')
  }
}

onUnmounted(() => {
  stopQrPolling()
  stopReloginPolling()
})

onMounted(loadAccounts)
</script>

<style scoped>
/* ===== 页面布局 ===== */
.accounts-page .el-card__header { padding: 20px; }
.accounts-table { margin-top: 16px; }

/* ===== 账号名单元格 ===== */
.acc-name { font-weight: 600; color: var(--text-1); }
.acc-display { color: var(--text-2); }
.time-cell { color: var(--text-3); font-size: var(--font-sm); }

/* ===== 二维码区域 ===== */
.qr-wrap { text-align: center; padding: 24px 0; }
.qr-image { width: 260px; height: 260px; border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 12px; background: #fff; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
.qr-tip { margin-top: 14px; color: var(--text-2); font-size: var(--font-md); }
.qr-msg { margin-top: 12px; display: flex; align-items: center; justify-content: center; gap: 6px; }
.qr-scanned { color: var(--color-success) !important; font-weight: 600; }
.qr-actions { display: flex; justify-content: center; gap: 12px; margin-top: 16px; }

/* ===== 对话框 tabs ===== */
.login-tabs { margin-bottom: 20px; }
.dialog-body { max-height: 60vh; overflow-y: auto; }

/* ===== 详情抽屉 ===== */
.drawer-body { padding: 24px; }
.detail-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid var(--border); }
.detail-avatar { background: var(--brand-gradient); color: #fff; font-size: 28px; }
.detail-name { margin: 0 0 2px; font-size: 18px; font-weight: 700; color: var(--text-1); }
.detail-account { margin: 0 0 8px; font-size: 13px; color: var(--text-3); }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
.stat-item { text-align: center; padding: 14px 8px; background: var(--bg-soft); border-radius: var(--radius-md); }
.stat-item .metric-value { font-size: 22px; font-weight: 700; }
.stat-item .metric-label { font-size: 12px; color: var(--text-3); margin-top: 4px; }
.detail-desc { margin-bottom: 20px; }
.detail-actions { display: flex; gap: 12px; }
.full-btn { flex: 1; }
.brand-gradient-text { font-weight: 600; }
</style>
