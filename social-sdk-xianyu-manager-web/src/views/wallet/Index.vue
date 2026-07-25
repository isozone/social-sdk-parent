<template>
  <div class="page-root">
    <el-card shadow="never">
      <!-- ===== 卡片头部 + 筛选栏 ===== -->
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-violet">
            <el-icon><Money /></el-icon>
          </div>
          <div class="card-head-text">
            <div class="card-title">钱包资产</div>
            <div class="card-sub">闲鱼账号余额、交易记录与提现配置</div>
          </div>
        </div>
        <div class="toolbar-right">
          <el-select v-model="selectedAccountId" @change="loadWallet" placeholder="选择账号" style="width: 200px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
          <el-button type="primary" size="small" :loading="syncing" :disabled="!selectedAccountId" @click="syncWalletFn">
            <el-icon><Refresh /></el-icon> 同步钱包
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="syncMsg"
        :title="syncMsg"
        :type="syncOk ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />

      <template v-if="wallet">
        <el-descriptions :column="2" border class="wallet-desc">
          <el-descriptions-item label="余额" class="amount-col">¥{{ wallet.balance }}</el-descriptions-item>
          <el-descriptions-item label="冻结金额">¥{{ wallet.frozenAmount }}</el-descriptions-item>
          <el-descriptions-item label="可用余额">¥{{ wallet.availableBalance != null ? wallet.availableBalance : '-' }}</el-descriptions-item>
          <el-descriptions-item label="总资产">¥{{ wallet.totalAssets != null ? wallet.totalAssets : '-' }}</el-descriptions-item>
          <el-descriptions-item label="可提现">¥{{ wallet.withdrawableAmount != null ? wallet.withdrawableAmount : '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付宝">{{ wallet.alipayAccount || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付宝实名">{{ wallet.alipayRealName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="银行卡">{{ wallet.bankCard || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else description="请选择账号查看钱包信息" class="mt-16" />
    </el-card>

    <el-card shadow="never" class="mt-16">
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-cyan">
            <el-icon><List /></el-icon>
          </div>
          <div class="card-head-text">
            <div class="card-title">交易记录</div>
            <div class="card-sub">充值 / 扣费 / 提现流水</div>
          </div>
        </div>
      </div>

      <el-table :data="transactions" stripe>
        <el-table-column prop="transactionId" label="交易ID" width="200" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="{ INCOME: 'success', EXPENSE: 'danger', TRANSFER: 'info' }[row.type]">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="余额" width="100">
          <template #default="{ row }">¥{{ row.balanceAfter }}</template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="transactionTime" label="时间" width="180" />
        <template #empty><el-empty description="暂无交易记录" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'
import { ElMessage } from 'element-plus'
import { Money, List, Refresh } from '@element-plus/icons-vue'
import { getWallet, getRecentTransactions, syncWallet } from '@/api/wallet'

const accounts = ref([])
const wallet = ref(null)
const transactions = ref([])
const selectedAccountId = ref(null)
const syncing = ref(false)
const syncMsg = ref('')
const syncOk = ref(true)

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      accounts.value = res.data
      // 默认选中第一个账号，否则钱包页打开时 selectedAccountId 一直为 null，数据不加载
      if (accounts.value.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = accounts.value[0].id
        await loadWallet()
      }
    }
  } catch (e) {}
}

async function loadWallet() {
  syncMsg.value = ''
  if (!selectedAccountId.value) {
    wallet.value = null
    transactions.value = []
    return
  }
  try {
    const r1 = await getWallet(selectedAccountId.value)
    if (r1.success) wallet.value = r1.data
  } catch (e) { wallet.value = null }
  try {
    const r2 = await getRecentTransactions(selectedAccountId.value, 20)
    if (r2.success) transactions.value = r2.data
  } catch (e) { transactions.value = [] }
}

async function syncWalletFn() {
  if (!selectedAccountId.value) return
  syncing.value = true
  syncMsg.value = ''
  try {
    const res = await syncWallet(selectedAccountId.value)
    if (res.success) {
      syncOk.value = true
      const d = res.data || {}
      syncMsg.value = `同步完成：余额已更新，账单 ${d.billCount || 0} 条`
      ElMessage.success('钱包同步成功')
      await loadWallet()
    } else {
      syncOk.value = false
      syncMsg.value = '同步未返回有效数据：' + (res.message || '未知错误')
      ElMessage.warning('钱包同步未完成，请确认接口名（详见后端日志 /api/wallet/api-names）')
    }
  } catch (e) {
    syncOk.value = false
    syncMsg.value = '同步请求失败，请检查后端日志'
  } finally {
    syncing.value = false
  }
}

onMounted(() => { loadAccounts() })
</script>

<style scoped>
.toolbar-right {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 14px; flex-wrap: wrap; margin-bottom: 16px;
}
.card-head-left {
  display: flex; align-items: center; gap: 14px; min-width: 0;
}
.card-chip {
  width: 44px; height: 44px; border-radius: 13px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 20px; flex-shrink: 0;
}
.chip-violet {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.22);
}
.chip-cyan {
  background: linear-gradient(135deg, #06b6d4, #22d3ee);
  box-shadow: 0 8px 18px rgba(6, 182, 212, 0.25);
}
.card-head-text {
  display: flex; flex-direction: column; gap: 3px; min-width: 0;
}
.card-title {
  font-size: 16px; font-weight: 600; color: var(--text-1);
}
.card-sub {
  font-size: 12px; color: var(--text-3);
}
.mt-16 { margin-top: 16px; }
.wallet-desc .amount-col {
  color: var(--color-danger); font-weight: 700;
}
.page-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; margin-bottom: var(--space-4); flex-wrap: wrap;
}
.page-toolbar .toolbar-left {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.page-toolbar .toolbar-right {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
</style>
