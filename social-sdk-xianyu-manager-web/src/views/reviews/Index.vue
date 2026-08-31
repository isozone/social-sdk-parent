<template>
  <div class="page-root">
    <el-card shadow="never">
      <!-- 卡片头 -->
      <div class="card-head">
        <div class="card-head-left">
          <div class="card-chip chip-violet"><el-icon><StarFilled /></el-icon></div>
          <div class="card-head-text">
            <div class="card-title">评价与信用</div>
            <div class="card-sub">评价管理、信用画像与退款处理的统一工作台</div>
          </div>
        </div>
        <el-select
          v-model="accountId" placeholder="选择账号" :loading="accountsLoading" clearable 
          @change="onAccountChange"
        >
          <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
        </el-select>
      </div>

      <!-- 三层 Tab -->
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="评价管理" name="reviews">
          <div class="page-toolbar">
            <div class="toolbar-left">
              <el-input v-model="buyerId" placeholder="用户 ID（留空拉当前账号）" clearable  />
            </div>
            <div class="toolbar-right">
              <el-button type="primary" :loading="loading" @click="loadReviews">
                <el-icon><Refresh /></el-icon> 拉评价列表
              </el-button>
            </div>
          </div>
          <el-table :data="reviews" stripe v-loading="loading" height="360">
            <el-table-column label="订单号" width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ reviewOrderId(row) }}</template>
            </el-table-column>
            <el-table-column label="评分" width="80">
              <template #default="{ row }">
                <el-tag :type="reviewRateType(row)" size="small">{{ reviewRateLabel(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="评价内容" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ reviewFeedback(row) }}</template>
            </el-table-column>
            <el-table-column label="评价人" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ reviewRater(row) }}</template>
            </el-table-column>
            <el-table-column label="卖家" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ reviewSeller(row) }}</template>
            </el-table-column>
            <el-table-column label="买家" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ reviewBuyer(row) }}</template>
            </el-table-column>
            <el-table-column label="商品" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ reviewItemTitle(row) }}</template>
            </el-table-column>
            <el-table-column label="评价时间" width="180">
              <template #default="{ row }">{{ reviewCreateTime(row) }}</template>
            </el-table-column>
            <template #empty><el-empty description="暂无评价" /></template>
          </el-table>

          <!-- 发表评价 -->
          <div class="form-section">
            <div class="section-label">发表评价</div>
            <el-form :model="reviewForm" inline>
              <el-form-item label="订单号">
                <el-input v-model="reviewForm.orderId" placeholder="订单号"  />
              </el-form-item>
              <el-form-item label="评分">
                <el-select v-model="reviewForm.rating" >
                  <el-option label="好评" value="GOOD" />
                  <el-option label="中评" value="NORMAL" />
                  <el-option label="差评" value="BAD" />
                </el-select>
              </el-form-item>
              <el-form-item label="内容">
                <el-input v-model="reviewForm.content" placeholder="不错的买家"  />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="submittingReview" @click="submitReview">
                  <el-icon><Check /></el-icon> 提交评价
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="信用画像" name="credit">
          <div class="page-toolbar">
            <div class="toolbar-left">
              <el-input v-model="creditUserId" placeholder="用户 ID（留空取自己）" clearable  />
            </div>
            <div class="toolbar-right">
              <el-button type="primary" :loading="loading" @click="loadCredit">
                <el-icon><Search /></el-icon> 拉信用画像
              </el-button>
            </div>
          </div>

          <div v-if="!creditData" class="empty-state">
            <el-icon :size="48" color="var(--text-3)"><DataAnalysis /></el-icon>
            <div style="margin-top: 8px;">请选择账号后点击「拉信用画像」</div>
          </div>

          <div v-else>
            <!-- 用户头部信息 -->
            <div class="credit-hero">
              <el-avatar :size="64" :src="creditData.data?.module?.base?.avatar?.avatar" />
              <div class="credit-hero-info">
                <div class="credit-hero-name">{{ creditData.data?.module?.base?.displayName || '—' }}</div>
                <div class="credit-hero-meta">
                  <el-icon><Location /></el-icon>
                  <span>{{ creditData.data?.module?.base?.ipLocation || '未知' }}</span>
                  <el-divider direction="vertical" />
                  <span class="credit-hero-intro">{{ creditData.data?.module?.base?.introduction || '' }}</span>
                </div>
                <div v-if="creditData.data?.module?.base?.ylzTags?.length" class="credit-tags">
                  <el-tag v-for="tag in creditData.data.module.base.ylzTags" :key="tag.code"
                    :type="tag.code === 'cs_seller_level' ? 'success' : 'warning'" size="small" effect="dark">
                    <img v-if="tag.icon" :src="tag.icon" class="credit-tag-icon" />
                    {{ tag.text }} L{{ tag.attributes?.level }}
                  </el-tag>
                </div>
              </div>
            </div>

            <!-- 信用分统计卡片 -->
            <el-row :gutter="12" v-if="creditCards.length">
              <el-col :xs="12" :sm="8" :md="6" v-for="c in creditCards" :key="c.label">
                <div class="credit-stat-card">
                  <div class="metric-value is-info">{{ c.value }}</div>
                  <div class="metric-label">{{ c.label }}</div>
                </div>
              </el-col>
            </el-row>

            <!-- 详细数据 -->
            <el-descriptions :column="3" border size="small" class="credit-detail">
              <el-descriptions-item label="店铺等级">
                <el-tag type="primary">{{ creditData.data?.module?.shop?.level || '—' }}</el-tag>
                <span class="credit-detail-hint">
                  (还差 {{ creditData.data?.module?.shop?.nextLevelNeedScore ?? -1 }} 分升级)
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="信用分">{{ creditData.data?.module?.shop?.score ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="评价数">{{ creditData.data?.module?.shop?.reviewNum ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="在售宝贝">{{ creditData.data?.module?.tabs?.item?.number ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="总评价">{{ creditData.data?.module?.tabs?.rate?.number ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="业务质量">
                <a v-if="creditData.data?.module?.shop?.businessQuality?.targetUrl" :href="creditData.data.module.shop.businessQuality.targetUrl" target="_blank">
                  {{ creditData.data.module.shop.businessQuality.name }}
                </a>
                <span v-else>{{ creditData.data?.module?.shop?.businessQuality?.name || '—' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="粉丝数">{{ creditData.data?.module?.social?.followers ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="关注数">{{ creditData.data?.module?.social?.following ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="擦亮上限">{{ creditData.data?.module?.shop?.itemToppingLimit ?? 0 }} 次/天</el-descriptions-item>
            </el-descriptions>

            <!-- 原始 JSON -->
            <el-collapse style="margin-top: 16px">
              <el-collapse-item title="原始响应 JSON">
                <pre class="json-block">{{ JSON.stringify(creditData, null, 2) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-tab-pane>

        <el-tab-pane label="退款管理" name="refunds">
          <div class="page-toolbar">
            <div class="toolbar-left">
              <el-select v-model="refundStatus" placeholder="退款状态" clearable >
                <el-option label="全部" value="" />
                <el-option label="退款中" value="1" />
                <el-option label="退款审核中" value="2" />
                <el-option label="退款处理中" value="3" />
                <el-option label="退款成功" value="5" />
              </el-select>
            </div>
            <div class="toolbar-right">
              <el-button type="primary" :loading="loading" @click="loadRefunds">
                <el-icon><Refresh /></el-icon> 拉退款列表
              </el-button>
            </div>
          </div>
          <el-table :data="refunds" stripe v-loading="loading" height="360">
            <el-table-column label="订单号" width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ refundOrderId(row) }}</template>
            </el-table-column>
            <el-table-column label="买家" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ refundBuyer(row) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="100">
              <template #default="{ row }"><span style="color: var(--color-danger); font-weight: 600;">¥{{ refundAmount(row) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="refundStatusType(refundStatusValue(row))" size="small">
                  {{ refundStatusLabel(refundStatusValue(row)) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="退款原因" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ refundReason(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="viewRefundDetail(refundOrderId(row))">详情</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无退款" /></template>
          </el-table>

          <!-- 申请退款 -->
          <div class="form-section">
            <div class="section-label">申请退款</div>
            <el-form :model="refundForm" inline>
              <el-form-item label="订单号">
                <el-input v-model="refundForm.orderId" placeholder="订单号"  />
              </el-form-item>
              <el-form-item label="原因">
                <el-input v-model="refundForm.reason" placeholder="退款原因"  />
              </el-form-item>
              <el-form-item label="金额">
                <el-input v-model="refundForm.amount" placeholder="退款金额"  />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="submittingRefund" @click="submitRefund">
                  <el-icon><Promotion /></el-icon> 申请退款
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 退款详情弹窗 -->
    <el-dialog v-model="refundDetailVisible" title="退款详情" width="720" top="6vh" destroy-on-close>
      <div v-if="refundDetail" class="refund-detail-body">
        <div class="refund-header">
          <el-tag :type="refundStatusTagType" size="large" effect="dark">
            {{ basicInfo.refundStatusDesc || '退款' }}
          </el-tag>
          <div class="refund-header-amount">¥{{ basicInfo.applyMoney || '-' }}</div>
          <div class="refund-header-title">{{ statusInfo.title || '退款详情' }}</div>
        </div>

        <el-timeline v-if="nodeStatusList.length" class="refund-timeline">
          <el-timeline-item
            v-for="(node, idx) in nodeStatusList"
            :key="idx"
            :type="node.nodeStatus === 'finish' ? 'success' : (node.nodeStatus === 'complete' ? 'primary' : 'info')"
            :hollow="node.nodeStatus !== 'finish' && node.nodeStatus !== 'complete'"
            :timestamp="node.time" placement="top"
          >
            <div class="timeline-txt">{{ node.txt }}</div>
          </el-timeline-item>
        </el-timeline>

        <el-descriptions v-if="basicInfo.refundId" :column="2" border size="small" class="refund-desc">
          <el-descriptions-item label="退款单号">{{ basicInfo.refundId }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ orderId }}</el-descriptions-item>
          <el-descriptions-item label="退款类型">{{ basicInfo.refundTypeDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退款原因">{{ basicInfo.reasonText || '-' }}</el-descriptions-item>
          <el-descriptions-item label="商品状态">{{ basicInfo.goodsStatusDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="运费承担">{{ basicInfo.postFeeBear || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客服介入">{{ basicInfo.csStatusDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退款结束时间">{{ basicInfo.disputeEndTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="progressList.length" class="refund-progress">
          <div class="sub-title">进度详情</div>
          <div v-for="(p, idx) in progressList" :key="idx" class="progress-item">
            <div class="progress-time">{{ p.timeStr }}</div>
            <div class="progress-text">{{ p.text }}</div>
            <div v-if="p.tips && p.tips.length" class="progress-tips">
              <el-tag v-for="tip in p.tips" :key="tip" size="small" type="info" effect="plain">{{ tip }}</el-tag>
            </div>
          </div>
        </div>

        <div v-if="refundDescribe.title" class="refund-describe">
          <div class="sub-title">{{ refundDescribe.title }}</div>
          <div v-for="(line, li) in refundDescribe.descRichText" :key="li" class="describe-line">
            <span v-for="(seg, si) in line.data" :key="si">
              <a v-if="seg.linkUrl" :href="seg.linkUrl" target="_blank">{{ seg.content }}</a>
              <span v-else>{{ seg.content }}</span>
            </span>
          </div>
        </div>

        <el-collapse>
          <el-collapse-item title="原始响应 JSON">
            <pre class="json-block">{{ JSON.stringify(refundDetail, null, 2) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>
      <template #footer>
        <el-button @click="refundDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location, StarFilled, Refresh, Search, Check, Promotion, DataAnalysis } from '@element-plus/icons-vue'
import { reviewOrder, listReviews, getCredit, applyRefund, listRefunds, getRefundDetail } from '@/api/review'
import { listAccounts } from '@/api/account'

const activeTab = ref('reviews')
const accountId = ref(null)
const accounts = ref([])
const accountsLoading = ref(false)
const loading = ref(false)

// 评价
const buyerId = ref('')
const reviews = ref([])
const reviewForm = ref({ orderId: '', rating: 'GOOD', content: '' })
const submittingReview = ref(false)

// 信用
const creditUserId = ref('')
const creditData = ref(null)

// 退款
const refundStatus = ref('')
const refunds = ref([])
const refundForm = ref({ orderId: '', reason: '', amount: '' })
const submittingRefund = ref(false)
const refundDetailVisible = ref(false)
const refundDetail = ref(null)

const refundComponents = computed(() => refundDetail.value?.data?.data?.components || [])

const basicInfo = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'basicRefundInfo')
  return comp?.data || {}
})

const statusInfo = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'refundStatusInfo')
  return comp?.data || {}
})

const nodeStatusList = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'nodeStatusInfo')
  return comp?.data?.nodeStatusList || []
})

const progressList = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'progressDetail')
  return comp?.data?.progressNodeList || []
})

const refundDescribe = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'refundDescribe')
  return comp?.data || {}
})

const orderId = computed(() => refundDetail.value?.data?.data?.orderId || '')

const refundStatusTagType = computed(() => {
  const status = basicInfo.value.refundStatus
  if (status === 'REFUND_SUCCESS') return 'success'
  if (status === 'REFUND_CLOSED' || status === 'REFUND_FAIL') return 'danger'
  return 'warning'
})

const creditCards = computed(() => {
  if (!creditData.value) return []
  const shop = creditData.value?.data?.module?.shop || {}
  const tabs = creditData.value?.data?.module?.tabs || {}
  return [
    { label: '信用分', value: shop.score ?? '-' },
    { label: '评价数', value: shop.reviewNum ?? tabs?.rate?.number ?? '-' },
    { label: '店铺等级', value: shop.level ?? '-' },
    { label: '在售宝贝', value: tabs?.item?.number ?? '-' },
    { label: '业务质量', value: shop.businessQuality?.name ?? '-' },
  ].filter(c => c.value !== '-' && c.value != null)
})

onMounted(async () => {
  accountsLoading.value = true
  try {
    const res = await listAccounts()
    accounts.value = res.data || []
  } catch (e) {
    ElMessage.error('拉账号列表失败')
  } finally {
    accountsLoading.value = false
  }
})

async function onAccountChange() {
  if (!accountId.value) return
  if (activeTab.value === 'reviews') await loadReviews()
  else if (activeTab.value === 'refunds') await loadRefunds()
}

async function onTabChange() {
  if (!accountId.value) return
  if (activeTab.value === 'reviews') await loadReviews()
  else if (activeTab.value === 'refunds') await loadRefunds()
}

async function loadReviews() {
  if (!accountId.value) return ElMessage.warning('请选账号')
  loading.value = true
  try {
    const res = await listReviews(accountId.value, buyerId.value)
    const d = res.data
    reviews.value = extractReviewItems(d)
    if (!reviews.value.length && isMtopFailed(d)) {
      ElMessage.warning(mtopErrorMessage(d))
    }
  } catch (e) {
    ElMessage.error('拉评价失败: ' + (e?.response?.data?.message || e.message))
    reviews.value = []
  } finally { loading.value = false }
}

function extractReviewItems(payload) {
  if (Array.isArray(payload)) return payload
  const candidates = [
    payload?.data?.data?.cardList, payload?.data?.data?.items, payload?.data?.data?.list,
    payload?.data?.data?.rateList, payload?.data?.data?.rateInfos,
    payload?.data?.cardList, payload?.data?.items, payload?.data?.list,
    payload?.data?.rateList, payload?.data?.rateInfos,
    payload?.cardList, payload?.items, payload?.list,
    payload?.rateList, payload?.rateInfos,
  ]
  return candidates.find(Array.isArray) || []
}

function isMtopFailed(payload) {
  const ret = payload?.ret || payload?.data?.ret
  return Array.isArray(ret) && ret.some(item => String(item).startsWith('FAIL_'))
}

function mtopErrorMessage(payload) {
  const ret = payload?.ret || payload?.data?.ret || []
  return '闲鱼返回失败: ' + (ret[0] || '未知错误')
}

function reviewData(row) { return row?.cardData || row || {} }
function reviewOrderId(row) { const d = reviewData(row); return d?.orderId || d?.tradeId || d?.bizOrderId || d?.orderNo || d?.trade?.id || d?.trade?.orderId || '-' }
function reviewRate(row) { const d = reviewData(row); return d?.rate ?? d?.rateType ?? d?.rating ?? d?.score ?? d?.star }
function reviewRateLabel(row) {
  const rate = reviewRate(row), text = String(rate ?? '')
  if (rate === 1 || text === '1' || text.toUpperCase() === 'GOOD') return '好评'
  if (rate === 2 || text === '2' || text.toUpperCase() === 'NORMAL') return '中评'
  if (rate === 3 || text === '3' || text.toUpperCase() === 'BAD') return '差评'
  return rate || '-'
}
function reviewRateType(row) {
  const l = reviewRateLabel(row)
  if (l === '好评') return 'success'; if (l === '差评') return 'danger'; return 'info'
}
function reviewFeedback(row) { const d = reviewData(row); return d?.feedback || d?.content || d?.comment || d?.rateContent || d?.text || '-' }
function reviewRater(row) { const d = reviewData(row); return d?.raterNick || d?.raterUserNick || d?.raterNickname || d?.raterName || d?.userNick || d?.nick || d?.user?.nick || '-' }
function reviewSeller(row) { const d = reviewData(row); return d?.sellerName || d?.sellerNick || d?.seller?.nick || '-' }
function reviewBuyer(row) { const d = reviewData(row); return d?.buyerName || d?.buyerNick || d?.buyer?.nick || '-' }
function reviewItemTitle(row) { const d = reviewData(row); return d?.itemTitle || d?.title || d?.item?.title || '-' }
function reviewCreateTime(row) { const d = reviewData(row); return d?.createTime || d?.gmtCreate || d?.gmtCreateStr || d?.rateTime || d?.timeDesc || d?.time || '-' }

async function submitReview() {
  if (!accountId.value || !reviewForm.value.orderId) return ElMessage.warning('请填账号和订单号')
  submittingReview.value = true
  try {
    await reviewOrder(accountId.value, reviewForm.value.orderId, reviewForm.value.rating, reviewForm.value.content)
    ElMessage.success('评价已提交'); reviewForm.value.content = ''
  } catch (e) { ElMessage.error('评价失败: ' + e.message) }
  finally { submittingReview.value = false }
}

async function loadCredit() {
  if (!accountId.value) return ElMessage.warning('请选账号')
  loading.value = true
  try {
    const res = await getCredit(accountId.value, creditUserId.value)
    creditData.value = res.data
  } catch (e) { ElMessage.error('拉信用失败: ' + e.message); creditData.value = null }
  finally { loading.value = false }
}

async function loadRefunds() {
  if (!accountId.value) return ElMessage.warning('请选账号')
  loading.value = true
  try {
    const res = await listRefunds(accountId.value, refundStatus.value)
    const d = res.data
    refunds.value = extractRefundItems(d)
    if (!refunds.value.length && isMtopFailed(d)) ElMessage.warning(mtopErrorMessage(d))
  } catch (e) { ElMessage.error('拉退款列表失败: ' + (e?.response?.data?.message || e.message)); refunds.value = [] }
  finally { loading.value = false }
}

function extractRefundItems(payload) {
  if (Array.isArray(payload)) return payload
  const candidates = [payload?.data?.data?.items, payload?.data?.data?.list, payload?.data?.items, payload?.data?.list, payload?.items, payload?.list]
  return candidates.find(Array.isArray) || []
}

function refundData(row) { return row?.data || row?.refundInfo || row || {} }
function refundOrderId(row) { const d = refundData(row); return d?.orderId || d?.bizOrderId || d?.tradeId || d?.commonData?.orderId || d?.commonData?.orderIdStr || '-' }
function refundBuyer(row) { const d = refundData(row); return d?.buyerInfoVO?.userNick || d?.buyerNick || d?.buyerName || d?.buyer?.nick || d?.counterpartyName || '-' }
function refundAmount(row) { const d = refundData(row); return d?.priceVO?.auctionPrice || d?.refundFee || d?.refundAmount || d?.amount || d?.price || '-' }
function refundStatusValue(row) { const d = refundData(row); return d?.disputeStatus || d?.refundStatus || d?.status || d?.commonData?.disputeStatus }
function refundReason(row) { const d = refundData(row); return d?.reason || d?.refundReason || d?.desc || d?.title || '-' }

function refundStatusLabel(status) { const map = { '1': '退款中', '2': '退款审核中', '3': '退款处理中', '5': '退款成功' }; return map[String(status)] || status || '-' }
function refundStatusType(status) { const map = { '1': 'warning', '2': 'warning', '3': 'warning', '5': 'success' }; return map[String(status)] || 'info' }

async function submitRefund() {
  if (!accountId.value || !refundForm.value.orderId) return ElMessage.warning('请填账号和订单号')
  submittingRefund.value = true
  try {
    await applyRefund(accountId.value, refundForm.value.orderId, refundForm.value.reason, refundForm.value.amount)
    ElMessage.success('退款已申请')
    refundForm.value = { orderId: '', reason: '', amount: '' }
    loadRefunds()
  } catch (e) { ElMessage.error('申请退款失败: ' + e.message) }
  finally { submittingRefund.value = false }
}

async function viewRefundDetail(orderId) {
  if (!orderId || orderId === '-') return ElMessage.warning('无订单号，无法拉退款详情')
  try {
    const res = await getRefundDetail(accountId.value, orderId)
    refundDetail.value = res.data; refundDetailVisible.value = true
  } catch (e) { ElMessage.error('拉退款详情失败: ' + (e?.response?.data?.message || e.message)) }
}
</script>

<style scoped>
.page-root { padding: 0; }

.form-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
.section-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12px;
}

.credit-hero {
  display: flex; align-items: flex-start; gap: 16px;
  padding: 20px; background: var(--bg-soft);
  border-radius: var(--radius-lg); margin-top: 8px;
}
.credit-hero-info { flex: 1; min-width: 0; }
.credit-hero-name { font-size: 18px; font-weight: 700; color: var(--text-1); }
.credit-hero-meta { display: flex; align-items: center; gap: 6px; margin-top: 6px; font-size: 13px; color: var(--text-3); }
.credit-hero-intro { color: var(--text-2); }
.credit-tags { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.credit-tag-icon { width: 16px; height: 16px; vertical-align: middle; margin-right: 3px; }
.credit-stat-card {
  background: #fff; border: 1px solid var(--border);
  border-radius: var(--radius-md); padding: 14px 12px;
  text-align: center; transition: box-shadow var(--transition-fast);
}
.credit-stat-card:hover { box-shadow: var(--shadow-hover); }
.credit-detail { margin-top: 16px; }
.credit-detail-hint { font-size: 12px; color: var(--text-3); margin-left: 4px; }

.json-block {
  background: var(--bg-soft); padding: 16px;
  border-radius: var(--radius-md); max-height: 400px;
  overflow: auto; font-size: 12px; line-height: 1.5;
}

.refund-detail-body { max-height: 75vh; overflow-y: auto; padding-right: 4px; }
.refund-header {
  text-align: center; margin-bottom: 20px;
  padding: 18px; background: var(--bg-soft);
  border-radius: var(--radius-lg);
}
.refund-header-amount {
  font-size: 28px; font-weight: 700;
  background: var(--brand-gradient);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  margin-top: 8px;
}
.refund-header-title { font-size: 14px; color: var(--text-2); margin-top: 4px; }
.refund-timeline { margin: 16px 0; }
.timeline-txt { font-size: 14px; color: var(--text-1); }
.refund-desc { margin: 16px 0; }
.refund-progress, .refund-describe { margin: 16px 0; }
.sub-title {
  font-size: 14px; font-weight: 600; color: var(--text-1);
  margin-bottom: 10px; padding-left: 8px;
  border-left: 3px solid var(--brand);
}
.progress-item {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 0; border-bottom: 1px dashed var(--border);
}
.progress-item:last-child { border-bottom: none; }
.progress-time { width: 140px; font-size: 13px; color: var(--text-3); flex-shrink: 0; }
.progress-text { flex: 1; font-size: 14px; color: var(--text-1); }
.progress-tips { display: flex; gap: 6px; flex-wrap: wrap; }
.describe-line { font-size: 13px; color: var(--text-2); line-height: 1.8; }
.describe-line a { color: var(--brand-2); text-decoration: none; &:hover { text-decoration: underline; } }
</style>
