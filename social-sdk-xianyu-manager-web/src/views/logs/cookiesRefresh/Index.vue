<template>
  <div class="page-root">
    <el-card shadow="never" style="margin: 0;">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-slate"><el-icon><Refresh /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">Cookie 刷新日志</div>
            <div class="card-sub">追踪 Cookie 浏览器刷新（A1）批次的执行结果与失败原因</div>
          </div>
        </div>
        <div class="card-head-right">
          <el-button size="small" @click="handleRun" :loading="running" title="手动触发一次刷新批次">
            <el-icon><VideoPlay /></el-icon>&nbsp;手动刷新
          </el-button>
          <el-button size="small" @click="handleRefresh" title="刷新">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>

      <el-table :data="records" stripe v-loading="loading" empty-text="暂无数据">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="触发来源" width="120">
          <template #default="{ row }">
            <el-tag :type="sourceType(row.triggerSource)" size="small">{{ sourceLabel(row.triggerSource) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="总数" width="70" align="center" />
        <el-table-column prop="successCount" label="成功" width="70" align="center" />
        <el-table-column prop="failedCount" label="失败" width="70" align="center" />
        <el-table-column prop="skippedCount" label="跳过" width="70" align="center" />
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ formatTime(row.endedAt) }}</template>
        </el-table-column>
        <el-table-column prop="failureSummary" label="失败原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="batchJobId" label="批次ID" width="90">
          <template #default="{ row }">{{ row.batchJobId || '—' }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无数据" /></template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listCookieRenewLogs, runCookieRenew } from '@/api/renew'

const records = ref([])
const total = ref(0)
const loading = ref(false)
const running = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)

function sourceLabel(s) {
  return { SCHEDULER: '定时', MANUAL: '手动', SYSTEM: '系统' }[s] || s || '—'
}
function sourceType(s) {
  return { SCHEDULER: 'info', MANUAL: 'primary', SYSTEM: 'warning' }[s] || 'info'
}
function statusLabel(s) {
  return { RUNNING: '执行中', SUCCESS: '成功', PARTIAL: '部分成功', FAILED: '失败' }[s] || s || '—'
}
function statusType(s) {
  return { RUNNING: 'warning', SUCCESS: 'success', PARTIAL: 'primary', FAILED: 'danger' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listCookieRenewLogs({ page: pageNum.value, size: pageSize.value })
    if (res.success) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载 Cookie 刷新日志失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function handleRun() {
  running.value = true
  try {
    const res = await runCookieRenew()
    if (res.success) {
      ElMessage.success('已触发 Cookie 刷新批次')
      loadData()
    } else {
      ElMessage.error(res.message || '触发失败')
    }
  } catch (e) {
    ElMessage.error('触发失败：' + (e.message || '未知错误'))
  } finally {
    running.value = false
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
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.card-head-right { display: flex; gap: 8px; }
</style>
