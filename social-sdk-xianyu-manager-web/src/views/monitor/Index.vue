<template>
  <div class="page-root">
    <el-card style="margin: 0;">
      <template #header>
        <div class="card-head">
          <div class="card-head-left">
            <div class="card-chip chip-violet"><el-icon><Monitor /></el-icon></div>
            <div class="card-head-text">
              <div class="card-title">监控面板</div>
              <div class="card-sub">实时概况、账号状态与异常追踪</div>
            </div>
          </div>
          <el-button size="small" @click="handleRefresh"><el-icon><Refresh /></el-icon> 刷新</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="12" :md="12" :lg="6" v-for="card in statCards" :key="card.title">
          <el-card shadow="hover" style="margin-bottom: 16px;">
            <div class="stat-content">
              <div class="stat-text">
                <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
                <div class="stat-label">{{ card.title }}</div>
              </div>
              <div class="stat-icon" :style="{ background: card.color }">
                <el-icon :size="20" color="#fff"><component :is="card.icon" /></el-icon>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 16px;">
      <template #header>
        <div class="card-head">
          <div class="card-head-left">
            <div class="card-chip chip-cyan"><el-icon><User /></el-icon></div>
            <div class="card-head-text">
              <div class="card-title">账号维度统计</div>
              <div class="card-sub">商品数、在售数、浏览量与收藏数</div>
            </div>
          </div>
        </div>
      </template>
      <el-table :data="accounts" stripe>
        <el-table-column prop="displayName" label="账号名称" width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-avatar :size="28" :src="row.avatar" />
              <span>{{ row.displayName || row.accountName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" sortable width="100" />
        <el-table-column prop="onSaleCount" label="在售" sortable width="100" />
        <el-table-column prop="viewCount" label="浏览量" sortable width="100" />
        <el-table-column prop="favoriteCount" label="收藏数" sortable width="100" />
        <el-table-column prop="todayReplies" label="今日回复" sortable width="100" />
        <template #empty><el-empty description="暂无账号" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboard, clearCache } from '@/api/monitor'
import { User, CircleCheck, Goods, Shop, ChatLineRound, View, Star, Warning, Monitor, Refresh } from '@element-plus/icons-vue'
import { COLORS, EL_COLORS, BRAND } from '@/styles/color-palette'

const overview = ref({})
const accounts = ref([])

const statCards = computed(() => [
  { title: '总账号数', value: overview.value.totalAccounts || 0, icon: User, color: BRAND.primary2 },
  { title: '在线账号', value: overview.value.onlineAccounts || 0, icon: CircleCheck, color: COLORS.success },
  { title: '总商品数', value: overview.value.totalProducts || 0, icon: Goods, color: COLORS.warning },
  { title: '在售商品', value: overview.value.onSaleProducts || 0, icon: Shop, color: COLORS.danger },
  { title: '今日回复', value: overview.value.todayReplies || 0, icon: ChatLineRound, color: COLORS.info },
  { title: '总浏览量', value: overview.value.totalViews || 0, icon: View, color: BRAND.accent },
  { title: '总收藏', value: overview.value.totalFavorites || 0, icon: Star, color: COLORS.warning },
  { title: '异常账号', value: overview.value.cookieExpiredAccounts || 0, icon: Warning, color: EL_COLORS.danger }
])

function statusType(status) {
  return { ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }[status] || 'info'
}

function statusLabel(status) {
  return { ACTIVE: '在线', DISABLED: '离线', FROZEN: '冻结', COOKIE_EXPIRED: '过期' }[status] || status
}

async function loadDashboard() {
  try {
    const r1 = await getDashboard()
    if (r1.success) {
      overview.value = r1.data.overview || {}
      accounts.value = r1.data.accounts || []
    }
  } catch (e) {}
}

async function handleRefresh() {
  await clearCache()
  await loadDashboard()
}

onMounted(loadDashboard)
</script>

<style scoped>
.stat-content { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.stat-text { min-width: 0; }
.stat-value { font-size: 26px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: var(--text-3); margin-top: 6px; }
.stat-icon { width: 44px; height: 44px; border-radius: 13px; display: flex; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; box-shadow: 0 8px 18px -6px rgba(0,0,0,0.25); }
</style>
