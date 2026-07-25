<template>
  <div class="page-root">
    <el-card shadow="never">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-amber"><el-icon><BellFilled /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">关闭平台通知</div>
            <div class="card-sub">定时逐账号关闭平台通知分类，批次日志查询与手动触发（BOT-A6/B5）</div>
          </div>
        </div>
        <el-button type="primary" :loading="running" @click="onRunManually">
          <el-icon><Promotion /></el-icon> 手动触发批次
        </el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe style="margin-top: 12px">
        <el-table-column prop="id" label="批次ID" width="90" />
        <el-table-column prop="triggerSource" label="触发来源" width="120" />
        <el-table-column prop="totalCount" label="账号数" width="90" />
        <el-table-column prop="successCount" label="成功" width="80">
          <template #default="{ row }"><span style="color: #10b981">{{ row.successCount }}</span></template>
        </el-table-column>
        <el-table-column prop="failedCount" label="失败" width="80">
          <template #default="{ row }"><span style="color: #ef4444">{{ row.failedCount }}</span></template>
        </el-table-column>
        <el-table-column prop="skippedCount" label="跳过" width="80">
          <template #default="{ row }"><span style="color: #6b7280">{{ row.skippedCount }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="170" />
        <el-table-column prop="endedAt" label="结束时间" width="170" />
        <el-table-column prop="failureSummary" label="失败摘要" min-width="280" show-overflow-tooltip />
      </el-table>

      <el-pagination style="margin-top: 12px" :current-page="page" :page-size="size" :total="total"
        layout="total, prev, pager, next" @current-change="onPage" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCloseNoticeLogs, runCloseNoticeManually } from '@/api/bot.js'

const rows = ref([])
const loading = ref(false)
const running = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const statusType = (s) => {
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'PARTIAL') return 'warning'
  return 'info'
}

async function loadList() {
  loading.value = true
  try {
    const res = await listCloseNoticeLogs({ page: page.value, size: size.value })
    if (res.success) { rows.value = res.data.records || []; total.value = res.data.total || 0 }
    else ElMessage.error(res.message || '加载失败')
  } catch (e) {} finally { loading.value = false }
}

function onPage(p) { page.value = p; loadList() }

async function onRunManually() {
  running.value = true
  try {
    const res = await runCloseNoticeManually()
    if (res.success) { ElMessage.success('批次已触发，请刷新查看'); await loadList() }
    else ElMessage.error(res.message || '触发失败')
  } catch (e) {} finally { running.value = false }
}

onMounted(() => loadList())
</script>

<style scoped>
.page-root { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-head-left { display: flex; align-items: center; gap: 12px; }
.card-chip { width: 40px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 20px; }
.chip-amber { background: #f59e0b; }
.card-title { font-size: 18px; font-weight: 600; }
.card-sub { font-size: 12px; color: #6b7280; margin-top: 2px; }
</style>
