<template>
  <div class="page-root">
    <el-card shadow="never" style="margin: 0;">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-slate"><el-icon><Delete /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">日志管理</div>
            <div class="card-sub">查看各日志表占用情况，并按保留天数清理历史日志</div>
          </div>
        </div>
        <div class="card-head-right">
          <el-button size="small" @click="handleRefresh" title="刷新">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>

      <el-form :inline="true">
        <el-form-item label="保留天数">
          <el-input-number v-model="keepDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="cleaning" @click="handleCleanup">清理过期日志</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" stripe v-loading="loading" empty-text="暂无数据">
        <el-table-column prop="name" label="日志表" min-width="220">
          <template #default="{ row }">
            <code>{{ row.name }}</code>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="180">
          <template #default="{ row }">{{ row.label }}</template>
        </el-table-column>
        <el-table-column prop="total" label="总记录数" width="110" align="center">
          <template #default="{ row }">{{ row.total ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="最近记录" min-width="170">
          <template #default="{ row }">{{ formatTime(row.latest) }}</template>
        </el-table-column>
        <el-table-column :label="`可清理(>${keepDays}天)`" prop="deletable" width="120" align="center">
          <template #default="{ row }">{{ row.deletable ?? '—' }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无数据" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLogStats, cleanupLogs, getLogCleanupConfig } from '@/api/logCleanup'

const loading = ref(false)
const cleaning = ref(false)
const keepDays = ref(7)
const stats = ref({})

const LABELS = {
  risk_control_log: '风控日志',
  circuit_breaker_event: '熔断事件',
  audit_log: '审计日志',
  scheduled_login_renew_log: '登录续期日志',
  scheduled_cookies_refresh_log: 'Cookie 刷新日志',
  scheduled_token_renewal_log: 'Token 续期日志',
  scheduled_polish_log: '商品擦亮日志',
  scheduled_close_notice_log: '关闭平台通知日志',
  scheduled_rate_log: '自动评价日志',
  scheduled_red_flower_log: '送红花日志',
  xianyu_auto_reply_log: '自动回复日志',
  notify_log: '站内通知日志'
}

const tableData = computed(() => {
  return Object.entries(stats.value).map(([name, stat]) => ({
    name,
    label: LABELS[name] || name,
    total: stat?.total,
    latest: stat?.latest,
    deletable: stat?.deletable
  }))
})

async function loadData() {
  loading.value = true
  try {
    const [statsRes, configRes] = await Promise.all([getLogStats(), getLogCleanupConfig()])
    if (statsRes.success) stats.value = statsRes.data || {}
    if (configRes.success && configRes.data && configRes.data.keepDays) {
      keepDays.value = configRes.data.keepDays
    }
  } catch (e) {
    ElMessage.error('加载日志统计失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function handleCleanup() {
  try {
    await ElMessageBox.confirm(`确认清理 ${keepDays.value} 天前的历史日志？此操作不可恢复。`, '清理确认', {
      confirmButtonText: '确认清理',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  cleaning.value = true
  try {
    const res = await cleanupLogs(keepDays.value)
    if (res.success) {
      ElMessage.success('日志清理完成')
      loadData()
    } else {
      ElMessage.error(res.message || '清理失败')
    }
  } catch (e) {
    ElMessage.error('清理失败：' + (e.message || '未知错误'))
  } finally {
    cleaning.value = false
  }
}

async function handleRefresh() {
  await loadData()
  ElMessage.success('数据已刷新')
}

function formatTime(t) {
  if (!t) return '—'
  const d = typeof t === 'string' ? new Date(t) : t
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

onMounted(loadData)
</script>

<style scoped>
.card-head-right { display: flex; gap: 8px; }
</style>
