<template>
  <div class="community-page">
    <div class="hero-card">
      <div>
        <div class="eyebrow">I 社区工作区</div>
        <h2>{{ pageTitle }}</h2>
        <p>这里承载你的 I 社区身份、VIP 权益、账户绑定、支付订单、公告资源和售后支持。</p>
      </div>
      <el-tag :type="vipStatus.active ? 'success' : 'warning'" size="large">
        {{ vipStatus.active ? 'VIP 已解锁' : '待解锁/已到期' }}
      </el-tag>
    </div>

    <el-row :gutter="16" class="section-row">
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>我的身份</template>
          <div class="identity-id">{{ vipStatus.communityUid || '未分配' }}</div>
          <div class="muted">支付成功后由 new-api 按真实支付渠道分配：ALIX / WXX / UX / MANX。</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>我的权益</template>
          <div class="identity-id">{{ vipStatus.vipLevel || 'free' }}</div>
          <div class="muted">到期时间：{{ vipStatus.expiredAt || '暂无' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>账户绑定</template>
          <el-space wrap>
            <el-tag>微信绑定</el-tag>
            <el-tag>邮箱绑定</el-tag>
            <el-tag type="info">授权迁移</el-tag>
          </el-space>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="feature-card">
      <template #header>已纳入闭环的社区能力</template>
      <el-row :gutter="12">
        <el-col v-for="item in features" :key="item.title" :xs="24" :sm="12" :md="6">
          <div class="feature-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.desc }}</span>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getVipStatus } from '@/api/vip'

const route = useRoute()
const vipStatus = ref({ active: false, communityUid: '', vipLevel: 'free' })
const titleMap = {
  home: '社区首页', profile: '我的身份', benefits: '我的权益', bindings: '账户绑定',
  orders: '支付订单', announcements: '社区公告', resources: '资源中心', support: '工单支持'
}
const pageTitle = computed(() => titleMap[route.params.section] || '社区首页')
const features = [
  { title: '唯一身份', desc: '支付成功后分配带渠道前缀的 I 社区 ID' },
  { title: '权益授权', desc: 'VIP features + limits 本地缓存并校验' },
  { title: '支付订单', desc: '支付宝、微信、U 支付均由 new-api 闭环' },
  { title: '社区运营', desc: '公告、教程、资源、工单、迁移持续沉淀' }
]

onMounted(async () => {
  try {
    const res = await getVipStatus()
    if (res.success) vipStatus.value = res.data || {}
  } catch (e) {}
})
</script>

<style scoped>
.community-page { padding: 2px; }
.hero-card { border-radius: 18px; padding: 24px; background: linear-gradient(135deg, #eef2ff, #fff7ed); display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 16px; }
.eyebrow { color: #7c3aed; font-weight: 700; font-size: 13px; margin-bottom: 6px; }
h2 { margin: 0 0 8px; font-size: 26px; color: #111827; }
p { margin: 0; color: #6b7280; }
.section-row { margin-bottom: 16px; }
.identity-id { font-size: 24px; font-weight: 800; color: #111827; margin-bottom: 8px; }
.muted { color: #6b7280; font-size: 13px; line-height: 1.6; }
.feature-card { border-radius: 16px; }
.feature-item { border: 1px solid #eef2ff; background: #fafafa; border-radius: 12px; padding: 14px; min-height: 84px; display: flex; flex-direction: column; gap: 8px; }
.feature-item span { color: #6b7280; font-size: 13px; line-height: 1.5; }
</style>
