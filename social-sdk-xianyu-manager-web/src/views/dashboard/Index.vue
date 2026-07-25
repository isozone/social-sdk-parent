<template>
  <div class="dashboard">
    <!-- ===== 顶部统计卡片 ===== -->
    <el-row :gutter="14" class="stat-row">
      <el-col
        :xs="12" :sm="12" :md="8" :lg="6" :xl="3"
        v-for="(card, idx) in statCards" :key="idx"
      >
        <el-card shadow="never" class="stat-card">
          <div class="stat-content">
            <div class="stat-text">
              <div class="stat-value" :class="'is-' + card.kind">{{ card.value }}</div>
              <div class="stat-label">{{ card.title }}</div>
            </div>
            <div class="stat-icon" :class="'icon-' + card.iconId">
              <el-icon :size="20"><component :is="card.icon" /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 图表区域：第一排三饼图 ===== -->
    <el-row :gutter="14" class="chart-row">
      <el-col :xs="24" :md="8" v-for="(chart, idx) in pieCharts" :key="idx">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <div class="card-head-left">
                <div class="card-chip chip-violet" style="width:36px;height:36px;font-size:16px;">
                  <el-icon><component :is="chart.chipIcon" /></el-icon>
                </div>
                <div class="card-head-text">
                  <div class="card-title">{{ chart.title }}</div>
                </div>
              </div>
              <el-button v-if="idx === 0" text @click="refreshCharts" title="刷新数据">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </template>
          <v-chart :option="chart.option" autoresize style="height: 260px;" />
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 图表区域：第二排折线图 ===== -->
    <el-row :gutter="14" class="chart-row">
      <el-col :xs="24" :md="12" v-for="(chart, idx) in lineCharts" :key="idx">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <div class="card-head-left">
                <div class="card-chip chip-cyan" style="width:36px;height:36px;font-size:16px;">
                  <el-icon><component :is="chart.chipIcon" /></el-icon>
                </div>
                <div class="card-head-text">
                  <div class="card-title">{{ chart.title }}</div>
                </div>
              </div>
            </div>
          </template>
          <v-chart :option="chart.option" autoresize style="height: 300px;" />
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 账号维度柱状图 ===== -->
    <el-row :gutter="14" class="chart-row">
      <el-col :xs="24" :md="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <div class="card-head-left">
                <div class="card-chip chip-green" style="width:36px;height:36px;font-size:16px;">
                  <el-icon><Monitor /></el-icon>
                </div>
                <div class="card-head-text">
                  <div class="card-title">账号维度概览</div>
                </div>
              </div>
            </div>
          </template>
          <v-chart :option="accountOverviewOption" autoresize style="height: 350px;" />
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 账号详情表 ===== -->
    <el-row :gutter="14" class="table-row">
      <el-col :xs="24" :md="24">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-head">
              <div class="card-head-left">
                <div class="card-chip chip-slate" style="width:36px;height:36px;font-size:16px;">
                  <el-icon><User /></el-icon>
                </div>
                <div class="card-head-text">
                  <div class="card-title">账号详情</div>
                </div>
              </div>
            </div>
          </template>
          <el-table :data="accounts" stripe v-loading="loading">
            <el-table-column prop="displayName" label="账号名" width="160">
              <template #default="{ row }">
                <div class="account-cell">
                  <el-avatar :size="28" :src="row.avatar" />
                  <span>{{ row.displayName || row.accountName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="productCount" label="商品数" sortable width="100" />
            <el-table-column prop="onSaleCount" label="在售" sortable width="100" />
            <el-table-column prop="viewCount" label="浏览量" sortable width="100" />
            <el-table-column prop="favoriteCount" label="收藏数" sortable width="100" />
            <el-table-column prop="todayReplies" label="今日回复" sortable width="100" />
            <el-table-column label="Cookie 有效期" width="160">
              <template #default="{ row }">
                <span :class="'cookie-' + cookieClass(row.cookieExpiresAt)">
                  {{ formatCookieExpires(row.cookieExpiresAt) }}
                </span>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无账号" /></template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent, TitleComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'
import { BRAND, CHART } from '@/styles/color-palette'

use([CanvasRenderer, PieChart, LineChart, BarChart, TooltipComponent, LegendComponent, GridComponent, TitleComponent])

const overview = ref({})
const accounts = ref([])
const orderTrend = ref([])
const messageActivity = ref([])
const accountStatus = ref([])

// ===== 统计卡片：统一色板与图标 =====
const statCards = computed(() => [
  { title: '总账号数', value: overview.value.totalAccounts || 0, icon: 'User', iconId: 'user',   kind: 'primary' },
  { title: '在线账号', value: overview.value.onlineAccounts || 0, icon: 'CircleCheck', iconId: 'check', kind: 'success' },
  { title: '总商品数', value: overview.value.totalProducts || 0, icon: 'Goods', iconId: 'goods', kind: 'warning' },
  { title: '在售商品', value: overview.value.onSaleProducts || 0, icon: 'Shop', iconId: 'shop', kind: 'danger' },
  { title: '今日回复', value: overview.value.todayReplies || 0, icon: 'ChatLineRound', iconId: 'chat', kind: 'info' },
  { title: '总浏览量', value: overview.value.totalViews || 0, icon: 'View', iconId: 'view', kind: 'info' },
  { title: '总收藏', value: overview.value.totalFavorites || 0, icon: 'Star', iconId: 'star', kind: 'warning' },
  { title: '异常账号', value: overview.value.cookieExpiredAccounts || 0, icon: 'Warning', iconId: 'warn', kind: 'danger' },
])

// ===== 饼图配置（用 CHART 色板） =====
const pieCharts = computed(() => [
  {
    title: '账号状态分布',
    chipIcon: 'UserFilled',
    option: {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: '5%', left: 'center', textStyle: { color: '#909399', fontSize: 12 } },
      series: [{
        type: 'pie', radius: ['42%', '72%'],
        avoidLabelOverlap: false,
        label: { show: false }, emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: accountStatus.value.length > 0 ? accountStatus.value : [
          { name: '在线', value: overview.value.onlineAccounts || 0 },
          { name: '离线', value: overview.value.offlineAccounts || 0 },
          { name: 'Cookie 过期', value: overview.value.cookieExpiredAccounts || 0 },
        ],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        color: [CHART[3], CHART[5], CHART[4]], // 绿、红、靛蓝
      }]
    }
  },
  {
    title: '商品状态分布',
    chipIcon: 'Goods',
    option: {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: '5%', left: 'center', textStyle: { color: '#909399', fontSize: 12 } },
      series: [{
        type: 'pie', radius: ['42%', '72%'],
        label: { show: false }, emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: [
          { name: '在售', value: overview.value.onSaleProducts || 0 },
          { name: '下架', value: overview.value.offSaleProducts || 0 },
          { name: '草稿', value: overview.value.draftProducts || 0 },
        ],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        color: [CHART[3], CHART[5], CHART[2]],
      }]
    }
  },
  {
    title: '订单状态分布',
    chipIcon: 'List',
    option: (() => {
      const soldCount = orderTrend.value.reduce((s, d) => s + (d.sold || 0), 0)
      const boughtCount = orderTrend.value.reduce((s, d) => s + (d.bought || 0), 0)
      return {
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: '5%', left: 'center', textStyle: { color: '#909399', fontSize: 12 } },
        series: [{
          type: 'pie', radius: ['42%', '72%'],
          label: { show: false }, emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
          labelLine: { show: false },
          data: [
            { name: '卖出', value: soldCount },
            { name: '买入', value: boughtCount },
          ],
          itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
          color: [CHART[0], CHART[5]],
        }]
      }
    })(),
  },
])

// ===== 折线图配置 =====
const lineCharts = computed(() => [
  {
    title: '近 14 天订单趋势',
    chipIcon: 'TrendCharts',
    option: {
      tooltip: { trigger: 'axis' },
      legend: { data: ['卖出', '买入'], bottom: 0, textStyle: { color: '#909399', fontSize: 12 } },
      grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: orderTrend.value.map(d => d.date), axisLabel: { color: '#909399' } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#909399' }, splitLine: { lineStyle: { color: '#f0f0f0' } } },
      series: [
        {
          name: '卖出', type: 'line', smooth: true, lineStyle: { width: 2 },
          data: orderTrend.value.map(d => d.sold || 0),
          itemStyle: { color: CHART[0] },
          areaStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(124,58,237,0.25)' },
                { offset: 1, color: 'rgba(124,58,237,0.02)' }
              ]
            }
          },
        },
        {
          name: '买入', type: 'line', smooth: true, lineStyle: { width: 2 },
          data: orderTrend.value.map(d => d.bought || 0),
          itemStyle: { color: CHART[5] }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(239,68,68,0.20)' }, { offset: 1, color: 'rgba(239,68,68,0.02)' }
          ])},
        },
      ]
    }
  },
  {
    title: '近 14 天消息活跃度',
    chipIcon: 'ChatDotRound',
    option: {
      tooltip: { trigger: 'axis' },
      legend: { data: ['收到', '回复'], bottom: 0, textStyle: { color: '#909399', fontSize: 12 } },
      grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: messageActivity.value.map(d => d.date), axisLabel: { color: '#909399' } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#909399' }, splitLine: { lineStyle: { color: '#f0f0f0' } } },
      series: [
        {
          name: '收到', type: 'line', smooth: true, lineStyle: { width: 2 },
          data: messageActivity.value.map(d => d.incoming || 0),
          itemStyle: { color: CHART[3] }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16,185,129,0.25)' }, { offset: 1, color: 'rgba(16,185,129,0.02)' }
          ])},
        },
        {
          name: '回复', type: 'line', smooth: true, lineStyle: { width: 2 },
          data: messageActivity.value.map(d => d.outgoing || 0),
          itemStyle: { color: CHART[2] }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245,158,11,0.20)' }, { offset: 1, color: 'rgba(245,158,11,0.02)' }
          ])},
        },
      ]
    }
  },
])

const accountOverviewOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['在售商品', '今日回复', '浏览量', '收藏数'], bottom: 0, textStyle: { color: '#909399', fontSize: 12 } },
  grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
  xAxis: {
    type: 'category', data: accounts.value.map(a => a.displayName || a.accountName || ('账号' + a.accountId)),
    axisLabel: { rotate: accounts.value.length > 6 ? 30 : 0, color: '#909399' },
    axisLine: { lineStyle: { color: '#e5e5e5' } },
  },
  yAxis: {
    type: 'value', minInterval: 1,
    axisLabel: { color: '#909399' },
    splitLine: { lineStyle: { color: '#f0f0f0' } },
  },
  series: [
    { name: '在售商品', type: 'bar', data: accounts.value.map(a => a.onSaleCount || 0), itemStyle: { color: CHART[0], borderRadius: [3,3,0,0] } },
    { name: '今日回复', type: 'bar', data: accounts.value.map(a => a.todayReplies || 0), itemStyle: { color: CHART[3], borderRadius: [3,3,0,0] } },
    { name: '浏览量', type: 'bar', data: accounts.value.map(a => a.viewCount || 0), itemStyle: { color: CHART[2], borderRadius: [3,3,0,0] } },
    { name: '收藏数', type: 'bar', data: accounts.value.map(a => a.favoriteCount || 0), itemStyle: { color: CHART[4], borderRadius: [3,3,0,0] } },
  ]
}))

// ===== 工具函数 =====
function statusType(s) {
  return { ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }[s] || 'info'
}
function statusLabel(s) {
  return { ACTIVE: '在线', DISABLED: '离线', FROZEN: '冻结', COOKIE_EXPIRED: '过期' }[s] || s
}
function formatCookieExpires(t) {
  if (!t) return '—'
  const d = new Date(t.replace(' ', 'T'))
  const diff = Math.floor((d - new Date()) / 86400000)
  if (diff < 0) return '已过期'
  if (diff === 0) return '今天到期'
  return `${diff} 天`
}
function cookieClass(t) {
  if (!t) return ''
  const diff = Math.floor((new Date(t.replace(' ', 'T')) - Date.now()) / 86400000)
  if (diff < 0) return 'expired'
  if (diff <= 3) return 'warn'
  return 'ok'
}

async function loadDashboard() {
  try {
    const res = await api.get('/monitor/dashboard')
    if (res.success) {
      overview.value = res.data.overview || {}
      accounts.value = res.data.accounts || []
      orderTrend.value = res.data.orderTrend || []
      messageActivity.value = res.data.messageActivity || []
      accountStatus.value = res.data.accountStatus || []
    } else {
      ElMessage.error(res.message || '加载仪表盘数据失败')
    }
  } catch (e) { /* 拦截器已提示 */ }
}

async function refreshCharts() {
  try {
    const res = await api.post('/monitor/cache/clear')
    if (res.success) {
      await loadDashboard()
      ElMessage.success('数据已刷新')
    } else {
      ElMessage.error(res.message || '刷新缓存失败')
    }
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(() => { loadDashboard() })
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

/* ========== 统计卡片行 ========== */
.stat-row { flex-shrink: 0; }
.stat-card { border-radius: 12px !important; background: #fff; }
.stat-content { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 4px 0; }
.stat-text { min-width: 0; }
.stat-value { font-size: 28px; font-weight: 800; line-height: 1.1; letter-spacing: -0.5px; }
.stat-value.is-primary  { color: var(--brand); }
.stat-value.is-success { color: var(--color-success); }
.stat-value.is-warning { color: var(--color-warning); }
.stat-value.is-danger  { color: var(--color-danger); }
.stat-value.is-info    { color: var(--accent); }
.stat-label { font-size: 12px; color: var(--text-3); margin-top: 4px; font-weight: 500; }
.stat-icon {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
}
.stat-icon.icon-user   { background: linear-gradient(135deg, #7c3aed, #a855f7); box-shadow: 0 6px 16px rgba(124,58,237,.22); }
.stat-icon.icon-check  { background: linear-gradient(135deg, #059669, #10b981); box-shadow: 0 6px 16px rgba(16,185,129,.22); }
.stat-icon.icon-goods  { background: linear-gradient(135deg, #d97706, #f59e0b); box-shadow: 0 6px 16px rgba(245,158,11,.22); }
.stat-icon.icon-shop   { background: linear-gradient(135deg, #dc2626, #ef4444); box-shadow: 0 6px 16px rgba(239,68,68,.22); }
.stat-icon.icon-chat   { background: linear-gradient(135deg, #0891b2, #22d3ee); box-shadow: 0 6px 16px rgba(6,182,212,.22); }
.stat-icon.icon-view   { background: linear-gradient(135deg, #4f46e5, #6366f1); box-shadow: 0 6px 16px rgba(79,70,229,.22); }
.stat-icon.icon-star   { background: linear-gradient(135deg, #d97706, #fbbf24); box-shadow: 0 6px 16px rgba(245,158,11,.22); }
.stat-icon.icon-warn   { background: linear-gradient(135deg, #dc2626, #f87171); box-shadow: 0 6px 16px rgba(239,68,68,.22); }

/* ========== 图表卡片 ========== */
.chart-row, .table-row { flex-shrink: 0; }
.chart-card, .table-card { border-radius: 12px !important; }
.chart-card .el-card__header { padding: 14px 20px; }
.table-card .el-card__header { padding: 14px 20px; }

/* ========== 账号名单元格 ========== */
.account-cell { display: flex; align-items: center; gap: 8px; }

/* ========== Cookie 有效期颜色 ========== */
.cookie-ok { color: var(--color-success); font-weight: 500; }
.cookie-warn { color: var(--color-warning); font-weight: 600; }
.cookie-expired { color: var(--color-danger); font-weight: 600; }

/* ========== ECharts 主题覆盖 ========== */
:deep(.echarts-for-react .echarts) { filter: none; }
</style>
