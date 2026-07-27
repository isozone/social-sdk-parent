<template>
  <div class="community-page">
    <div class="hero-card">
      <div>
        <div class="eyebrow">I 社区工作区</div>
        <h2>{{ pageTitle }}</h2>
        <p>完整接入 I 社区：身份、权益校验、圈子、发帖回复、收藏购买、钱包充值、兑换、通知和排行榜。</p>
      </div>
      <el-space>
        <el-tag :type="vipStatus.active ? 'success' : 'warning'" size="large">{{ vipStatus.active ? 'VIP 已解锁' : '待解锁/已到期' }}</el-tag>
        <el-button :loading="verifying" @click="verifyVip">校验权益</el-button>
        <el-button @click="reloadCurrent">刷新</el-button>
      </el-space>
    </div>

    <template v-if="section === 'home'">
      <el-row :gutter="16" class="section-row">
        <el-col :xs="24" :md="8"><metric-card title="我的身份" :value="vipStatus.communityUid || profile.community_uid || '未分配'" :desc="`支付渠道：${vipChannelLabel}`" /></el-col>
        <el-col :xs="24" :md="8"><metric-card title="我的权益" :value="vipLevelLabel" :desc="homeVipDesc" /></el-col>
        <el-col :xs="24" :md="8"><metric-card title="社区钱包" :value="wallet.balance ?? wallet.coins ?? 0" desc="可用于购买、打赏、兑换与资源消费" /></el-col>
      </el-row>
      <el-card shadow="never" class="feature-card">
        <template #header>客户端功能闭环</template>
        <el-row :gutter="12">
          <el-col v-for="item in features" :key="item.title" :xs="24" :sm="12" :md="6">
            <div class="feature-item" @click="$router.push(item.path)"><strong>{{ item.title }}</strong><span>{{ item.desc }}</span></div>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <template v-else-if="section === 'topics'">
      <list-card title="帖子广场" :loading="loading" :empty="topics.length === 0">
        <template #actions>
          <el-input v-model="topicQuery.keyword" placeholder="搜索帖子" clearable style="width:220px" @keyup.enter="loadTopics" />
          <el-select v-model="topicQuery.category_id" clearable placeholder="分类" style="width:160px" @change="loadTopics"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select>
          <el-button @click="loadTopics">搜索</el-button>
          <el-button type="primary" @click="$router.push('/app/community/composer')">发布帖子</el-button>
        </template>
        <div v-for="topic in topics" :key="topic.id" class="topic-item" @click="openTopic(topic)">
          <div class="topic-title">{{ topic.title }}</div>
          <div class="topic-meta"><span>{{ topic.user?.nickname || topic.author_name || topic.username || '社区用户' }}</span><span>浏览 {{ topic.view_count || 0 }}</span><span>回复 {{ topic.reply_count || 0 }}</span><span>点赞 {{ topic.like_count || 0 }}</span><span v-if="topic.price > 0">售价 {{ topic.price }} 币</span></div>
          <div class="topic-summary">{{ topic.summary || stripHtml(topic.content || '') }}</div>
          <el-space class="topic-actions" @click.stop>
            <el-button size="small" @click="toggleFavorite(topic)">{{ topic.favored ? '取消收藏' : '收藏' }}</el-button>
            <el-button size="small" @click="react(topic, 'like')">{{ topic.liked ? '已赞' : '点赞' }}</el-button>
            <el-button v-if="topic.price > 0 && !topic.purchased" size="small" type="warning" @click="purchaseTopic(topic)">购买</el-button>
          </el-space>
        </div>
      </list-card>
    </template>

    <template v-else-if="section === 'composer'">
      <el-card shadow="never">
        <template #header>{{ composer.id ? '编辑草稿/帖子' : '发布帖子' }}</template>
        <el-form label-position="top">
          <el-form-item label="标题"><el-input v-model="composer.title" maxlength="120" show-word-limit placeholder="请输入帖子标题" /></el-form-item>
          <el-form-item label="内容"><div ref="topicEditorEl" class="community-editor"></div></el-form-item>
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item label="分类"><el-select v-model="composer.category_id" :disabled="composer.circle_id > 0" style="width:100%"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select><div v-if="composer.circle_id > 0" class="form-tip">选择圈子后，分类自动跟随圈子所属分类。</div></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="圈子"><el-select v-model="composer.circle_id" clearable style="width:100%"><el-option :value="0" label="不选择圈子" /><el-option v-for="c in filteredCircles" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="售价社区币"><el-input-number v-model="composer.price" :min="0" style="width:100%" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="标签"><el-input v-model="composer.tagsText" placeholder="多个标签用逗号分隔" /></el-form-item>
          <el-space><el-button type="primary" :loading="submitting" @click="submitTopic">发布</el-button><el-button :loading="submitting" @click="saveDraft">保存草稿</el-button><el-button @click="resetComposer">清空</el-button></el-space>
        </el-form>
      </el-card>
    </template>

    <template v-else-if="section === 'wallet'">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8"><metric-card title="社区钱包" :value="wallet.balance ?? wallet.coins ?? 0" :desc="`积分：${wallet.points ?? 0}`" /></el-col>
        <el-col :xs="24" :md="16">
          <el-card shadow="never"><template #header>充值套餐</template>
            <el-alert title="创建订单后可在支付订单页继续支付/查看状态；微信、支付宝、虚拟支付均由 I 社区后端完成。" type="info" :closable="false" class="mb12" />
            <el-radio-group v-model="rechargeChannel" class="mb12"><el-radio-button label="wechat">微信</el-radio-button><el-radio-button label="alipay">支付宝</el-radio-button><el-radio-button label="virtual-pay">虚拟支付</el-radio-button></el-radio-group>
            <el-space wrap><el-card v-for="plan in rechargePlans" :key="plan.id" shadow="hover" class="recharge-plan"><div class="vip-plan-title">{{ plan.name }}</div><div class="vip-plan-price">¥{{ formatCents(plan.price_cents) }}</div><div class="muted">{{ plan.coins }} 社区币</div><el-button size="small" type="primary" @click="createCreditOrder(plan)">创建并支付</el-button></el-card></el-space>
          </el-card>
        </el-col>
      </el-row>
      <data-table title="钱包流水" :data="transactions" :columns="txColumns" />
    </template>

    <template v-else-if="section === 'orders'">
      <data-table title="支付订单" :data="orders" :columns="orderColumns">
        <template #row-actions="{ row }"><el-button size="small" @click="openOrder(row)">详情/支付</el-button></template>
      </data-table>
    </template>

    <template v-else-if="section === 'circles'">
      <el-card shadow="never" class="mb12">
        <template #header>创建圈子</template>
        <el-form inline><el-form-item label="名称"><el-input v-model="circleForm.name" placeholder="圈子名称" /></el-form-item><el-form-item label="分类"><el-select v-model="circleForm.category_id" style="width:140px"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item><el-form-item label="加入方式"><el-select v-model="circleForm.join_policy" style="width:120px"><el-option label="免费" value="free" /><el-option label="审批" value="approve" /><el-option label="付费" value="paid" /></el-select></el-form-item><el-form-item label="价格"><el-input-number v-model="circleForm.join_price" :min="0" /></el-form-item><el-button type="primary" @click="createCircle">创建</el-button></el-form>
      </el-card>
      <simple-list title="社区圈子" :items="circles" name-key="name" desc-key="description" @reload="loadCircles"><template #item-actions="{ item }"><el-button size="small" type="primary" @click="joinCircle(item)">{{ item.joined ? '已加入' : item.join_policy === 'paid' ? `付费加入 ${item.join_price || 0}` : '加入' }}</el-button><el-button v-if="item.joined" size="small" @click="leaveCircle(item)">退出</el-button></template></simple-list>
    </template>

    <template v-else-if="section === 'my-topics'"><simple-list title="我的帖子" :items="myTopics" name-key="title" desc-key="summary" @reload="loadMyTopics"><template #item-actions="{ item }"><el-button size="small" @click="openTopic(item)">查看</el-button><el-button size="small" type="danger" @click="deleteTopic(item)">删除</el-button></template></simple-list></template>
    <template v-else-if="section === 'favorites'"><simple-list title="我的收藏" :items="favorites" name-key="title" desc-key="summary" @reload="loadFavorites"><template #item-actions="{ item }"><el-button size="small" @click="openTopic(item)">查看</el-button><el-button size="small" @click="toggleFavorite(item)">取消收藏</el-button></template></simple-list></template>
    <template v-else-if="section === 'drafts'"><simple-list title="草稿箱" :items="drafts" name-key="title" desc-key="content" @reload="loadDrafts"><template #item-actions="{ item }"><el-button size="small" @click="editDraft(item)">编辑</el-button><el-button size="small" type="danger" @click="deleteDraft(item)">删除</el-button></template></simple-list></template>
    <template v-else-if="section === 'purchases'"><simple-list title="我的购买" :items="purchases" name-key="title" desc-key="summary" @reload="loadPurchases"><template #item-actions="{ item }"><el-button size="small" @click="openTopic(item)">查看</el-button></template></simple-list></template>
    <template v-else-if="section === 'exchange'"><simple-list title="兑换商城" :items="exchangeItems" name-key="name" desc-key="description" @reload="loadExchangeItems"><template #item-actions="{ item }"><el-button size="small" type="primary" @click="exchange(item)">兑换 {{ item.price || 0 }} 币</el-button></template></simple-list></template>
    <template v-else-if="section === 'notifications'"><simple-list title="社区通知" :items="notifications" name-key="title" desc-key="content" @reload="loadNotifications"><template #header-actions><el-button @click="markAllNotificationsRead">全部已读</el-button></template><template #item-actions="{ item }"><el-button size="small" @click="markNotificationRead(item)">{{ item.is_read ? '已读' : '标为已读' }}</el-button></template></simple-list></template>
    <template v-else-if="section === 'leaderboard'"><simple-list title="排行榜" :items="leaderboard" name-key="username" desc-key="score" @reload="loadLeaderboard" /></template>
    <template v-else-if="section === 'announcements'"><simple-list title="社区公告" :items="announcements" name-key="title" desc-key="content" @reload="loadAnnouncements" /></template>
    <template v-else-if="section === 'resources'"><simple-list title="资源中心" :items="resources" name-key="title" desc-key="description" @reload="loadResources"><template #item-actions="{ item }"><el-button size="small" @click="openTopic(item)">查看资源</el-button></template></simple-list></template>

    <template v-else-if="section === 'profile'">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12"><el-card shadow="never"><template #header>社区资料</template><el-descriptions :column="1" border><el-descriptions-item label="社区 ID">{{ vipStatus.communityUid || profile.user?.username || '-' }}</el-descriptions-item><el-descriptions-item label="昵称">{{ profile.user?.nickname || profile.display_name || profile.nickname || '-' }}</el-descriptions-item><el-descriptions-item label="VIP 等级"><el-tag :type="vipLevelType" effect="dark">{{ vipLevelLabel }}</el-tag></el-descriptions-item><el-descriptions-item label="VIP 状态">{{ vipStatus.active ? '生效中' : '未生效/已过期' }}</el-descriptions-item><el-descriptions-item label="到期时间">{{ formatDateTime(vipStatus.expiredAt) || '-' }}<el-tag v-if="vipDaysLeft !== null" :type="vipDaysLeft <= 7 ? 'danger' : 'info'" size="small" style="margin-left:8px">剩余 {{ vipDaysLeft }} 天</el-tag></el-descriptions-item><el-descriptions-item label="最后校验">{{ formatDateTime(vipStatus.lastVerifiedAt) || '-' }}<el-tag v-if="vipVerifiedStale" type="warning" size="small" style="margin-left:8px">校验较旧，建议重新校验</el-tag></el-descriptions-item><el-descriptions-item label="社区等级">{{ profile.profile?.level ? 'Lv.' + profile.profile.level : '-' }}</el-descriptions-item><el-descriptions-item label="经验">{{ profile.profile?.exp || 0 }}</el-descriptions-item></el-descriptions></el-card></el-col>
        <el-col :xs="24" :md="12"><el-card shadow="never"><template #header>社区成长</template><el-descriptions :column="1" border><el-descriptions-item label="帖子数">{{ profile.profile?.topic_count || 0 }}</el-descriptions-item><el-descriptions-item label="回复数">{{ profile.profile?.reply_count || 0 }}</el-descriptions-item><el-descriptions-item label="获赞数">{{ profile.profile?.like_count || 0 }}</el-descriptions-item><el-descriptions-item label="徽章数">{{ profile.profile?.badge_count || 0 }}</el-descriptions-item></el-descriptions></el-card></el-col>
      </el-row>
    </template>

    <template v-else-if="section === 'benefits'">
      <el-card shadow="never">
        <template #header>
          <span>我的权益</span>
          <el-tag :type="vipStatus.active ? 'success' : 'warning'" size="small" effect="dark" style="margin-left:8px">{{ vipStatus.active ? '生效中' : '未生效/已过期' }}</el-tag>
          <el-tag :type="vipLevelType" size="small" effect="dark" style="margin-left:8px">{{ vipLevelLabel }}</el-tag>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="VIP 等级"><el-tag :type="vipLevelType" effect="dark">{{ vipLevelLabel }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="到期时间">{{ formatDateTime(vipStatus.expiredAt) || '-' }}<el-tag v-if="vipDaysLeft !== null" :type="vipDaysLeft <= 7 ? 'danger' : 'info'" size="small" style="margin-left:8px">剩余 {{ vipDaysLeft }} 天</el-tag></el-descriptions-item>
          <el-descriptions-item label="最后校验">{{ formatDateTime(vipStatus.lastVerifiedAt) || '-' }}<el-tag v-if="vipVerifiedStale" type="warning" size="small" style="margin-left:8px">校验较旧，建议重新校验</el-tag></el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">功能权益</el-divider>
        <el-space wrap>
          <el-tag v-for="f in vipFeatureList" :key="f.key" type="success" effect="plain">{{ f.label }}</el-tag>
          <el-empty v-if="vipFeatureList.length === 0" description="暂无功能权益" />
        </el-space>
        <el-divider content-position="left">额度限制</el-divider>
        <el-descriptions v-if="vipLimitItems.length" :column="1" border>
          <el-descriptions-item v-for="item in vipLimitItems" :key="item.key" :label="item.label">{{ item.value }} {{ item.unit }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="暂无额度限制" />
      </el-card>
    </template>

    <template v-else-if="section === 'bindings'">
      <el-card shadow="never"><template #header>账户绑定</template><el-alert type="info" :closable="false" title="VIP 身份以邮箱验证码为准；社区数据实时从 I 社区同步，当前客户端只保存绑定状态和授权快照。" class="mb12" /><el-descriptions :column="1" border><el-descriptions-item label="社区 ID">{{ vipStatus.communityUid || profile.user?.username || '-' }}</el-descriptions-item><el-descriptions-item label="绑定邮箱">{{ profile.email || vipStatus.email || '请在解锁 VIP 弹窗中绑定邮箱' }}</el-descriptions-item><el-descriptions-item label="邮箱状态">{{ (profile.email_verified || vipStatus.emailVerified) ? '已验证' : '未验证' }}</el-descriptions-item><el-descriptions-item label="VIP 等级"><el-tag :type="vipLevelType" effect="dark">{{ vipLevelLabel }}</el-tag></el-descriptions-item><el-descriptions-item label="客户端授权">{{ vipStatus.active ? '已授权' : '未授权/已过期' }}</el-descriptions-item><el-descriptions-item label="到期时间">{{ formatDateTime(vipStatus.expiredAt) || '-' }}<el-tag v-if="vipDaysLeft !== null" :type="vipDaysLeft <= 7 ? 'danger' : 'info'" size="small" style="margin-left:8px">剩余 {{ vipDaysLeft }} 天</el-tag></el-descriptions-item><el-descriptions-item label="最后校验">{{ formatDateTime(vipStatus.lastVerifiedAt) || '-' }}<el-tag v-if="vipVerifiedStale" type="warning" size="small" style="margin-left:8px">校验较旧，建议重新校验</el-tag></el-descriptions-item><el-descriptions-item label="社区昵称">{{ profile.user?.nickname || profile.display_name || profile.nickname || '-' }}</el-descriptions-item></el-descriptions><el-space class="mt16"><el-button type="primary" @click="verifyVip">重新校验授权</el-button><el-button @click="$router.push('/app/community/profile')">查看社区资料</el-button></el-space></el-card>
    </template>

    <template v-else-if="section === 'support'"><el-card shadow="never"><template #header>工单支持</template><el-alert type="info" :closable="false" title="工单支持通过 I 社区通知、公告与资源帖子闭环承载。请在帖子广场发布问题或查看官方公告。" /><el-button class="mt16" type="primary" @click="$router.push('/app/community/composer')">发布求助帖</el-button></el-card></template>

    <el-dialog v-model="topicDialogVisible" title="帖子详情" width="780px">
      <div v-if="currentTopic"><h3>{{ currentTopic.title }}</h3><div class="topic-meta"><span>{{ currentTopic.user?.nickname || currentTopic.author_name || currentTopic.username }}</span><span>回复 {{ replies.length }}</span><span v-if="currentTopic.price > 0">售价 {{ currentTopic.price }} 币</span></div><div class="topic-content" v-html="safeHtml(currentTopic.content || currentTopic.summary || '')"></div><el-divider content-position="left">回复</el-divider><div v-for="reply in replies" :key="reply.id" class="reply-item"><strong>{{ reply.user?.nickname || '社区用户' }}：</strong><span v-html="safeHtml(reply.content || '')"></span></div><div ref="replyEditorEl" class="community-reply-editor"></div></div>
      <template #footer><el-button @click="topicDialogVisible = false">关闭</el-button><el-button v-if="currentTopic?.price > 0 && !currentTopic?.purchased" type="warning" :loading="submitting" @click="purchaseTopic(currentTopic)">购买后查看全文</el-button><el-button type="primary" :loading="submitting" @click="submitReply">回复</el-button></template>
    </el-dialog>

    <el-dialog v-model="orderDialogVisible" title="支付订单" width="680px">
      <div v-if="currentOrder"><el-descriptions :column="1" border><el-descriptions-item label="订单号">{{ currentOrder.order_no }}</el-descriptions-item><el-descriptions-item label="金额">¥{{ formatCents(currentOrder.pay_amount) }}</el-descriptions-item><el-descriptions-item label="渠道">{{ currentOrder.pay_channel }}</el-descriptions-item><el-descriptions-item label="状态">{{ currentOrder.status }}</el-descriptions-item></el-descriptions><pre v-if="paymentInfo" class="pay-json">{{ paymentInfo }}</pre></div>
      <template #footer><el-button @click="orderDialogVisible = false">关闭</el-button><el-button :loading="submitting" @click="refreshOrder">刷新状态</el-button><el-button v-if="currentOrder?.status === 'pending'" type="primary" :loading="submitting" @click="initiateOrderPayment">继续支付</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElCard, ElEmpty, ElSkeleton, ElTable, ElTableColumn, ElMessage, ElMessageBox } from 'element-plus'
import { AiEditor } from 'aieditor'
import 'aieditor/dist/style.css'
import { communityDelete, communityGet, communityPost, getVipIdentity, getVipStatus, verifyVipStatus } from '@/api/vip'

const MetricCard = defineComponent({ props: ['title','value','desc'], setup: p => () => h(ElCard, { shadow:'never' }, { header: () => p.title, default: () => [h('div',{class:'identity-id'}, p.value), h('div',{class:'muted'}, p.desc)] }) })
const ListCard = defineComponent({ props: ['title','loading','empty'], setup: (p,{slots}) => () => h(ElCard,{shadow:'never'},{header:()=>h('div',{class:'card-header'},[h('span',p.title), h('div', slots.actions?.())]), default:()=>h(ElSkeleton,{loading:p.loading,animated:true,rows:5},{default:()=>p.empty?h('div',{class:'empty-box'},'暂无数据'):slots.default?.()})}) })
const DataTable = defineComponent({ props: ['title','data','columns'], setup: (p,{slots}) => () => h(ElCard,{shadow:'never',class:'mt16'},{header:()=>p.title, default:()=>h(ElTable,{data:p.data||[],stripe:true},()=> [...(p.columns||[]).map(c=>h(ElTableColumn,c)), slots['row-actions'] ? h(ElTableColumn,{label:'操作',width:140},{default:({row})=>slots['row-actions']({row})}) : null])}) })
const SimpleList = defineComponent({ props: ['title','items','nameKey','descKey'], emits:['reload'], setup: (p,{emit,slots}) => () => h(ElCard,{shadow:'never'},{header:()=>h('div',{class:'card-header'},[h('span',p.title),h('div',[slots['header-actions']?.(), h(ElButton,{onClick:()=>emit('reload')},()=> '刷新')])]), default:()=> (p.items||[]).length===0?h(ElEmpty,{description:'暂无数据'}):h('div', (p.items||[]).map(item=>h('div',{class:'topic-item'},[h('div',{class:'topic-title'}, item[p.nameKey] || item.title || item.name || `#${item.id}`), h('div',{class:'topic-summary'}, String(item[p.descKey] || item.content || item.description || item.score || '')), h('div',{class:'topic-actions'}, slots['item-actions']?.({item}))])))}) })

const route = useRoute(); const router = useRouter(); const loading = ref(false); const submitting = ref(false); const verifying = ref(false)
const vipStatus = ref({ active:false, communityUid:'', vipLevel:'free' }); const profile = ref({}); const wallet = ref({})
const topics = ref([]); const replies = ref([]); const orders = ref([]); const transactions = ref([]); const rechargePlans = ref([]); const categories = ref([])
const circles = ref([]); const myTopics = ref([]); const favorites = ref([]); const drafts = ref([]); const purchases = ref([]); const exchangeItems = ref([]); const notifications = ref([]); const leaderboard = ref([]); const announcements = ref([]); const resources = ref([])
const topicDialogVisible = ref(false); const currentTopic = ref(null); const replyText = ref(''); const topicQuery = ref({ keyword:'', category_id:'' })
const topicEditorEl = ref(null); const replyEditorEl = ref(null); const topicEditor = ref(null); const replyEditor = ref(null)
const orderDialogVisible = ref(false); const currentOrder = ref(null); const paymentInfo = ref(''); const rechargeChannel = ref('wechat')
const composer = ref({ title:'', content:'', circle_id:0, category_id:null, price:0, tagsText:'' }); const circleForm = ref({ name:'', description:'', category_id:null, join_policy:'free', join_price:0 })
const titleMap = { home:'社区首页', profile:'我的身份', benefits:'我的权益', bindings:'账户绑定', topics:'帖子广场', composer:'发布帖子', wallet:'社区钱包', orders:'支付订单', circles:'社区圈子', 'my-topics':'我的帖子', favorites:'我的收藏', drafts:'草稿箱', purchases:'我的购买', exchange:'兑换商城', notifications:'社区通知', leaderboard:'排行榜', announcements:'社区公告', resources:'资源中心', support:'工单支持' }
const section = computed(() => route.params.section || 'home'); const pageTitle = computed(() => titleMap[section.value] || '社区首页')
const filteredCircles = computed(() => {
  const categoryId = Number(composer.value.category_id || 0)
  if (!categoryId) return circles.value
  return circles.value.filter(c => Number(c.category_id || 0) === categoryId)
})
const VIP_LEVEL_LABEL = { free: '免费版', pro: '专业版 PRO', premium: '旗舰版', team: '团队版', enterprise: '企业版' }
const VIP_LEVEL_TAG = { free: 'info', pro: 'warning', premium: 'danger', team: 'success', enterprise: 'success' }
const FEATURE_LABELS = { multi_account_pro: '多账号专业版', ai_auto_reply: 'AI 自动回复', batch_task: '批量任务', advanced_dashboard: '高级数据看板', rule_engine_pro: '规则引擎专业版', cloud_sync: '云同步' }
const LIMIT_LABELS = { max_accounts: { label: '可管理账号数', unit: '个' }, max_ai_rules: { label: 'AI 规则上限', unit: '条' }, max_batch_tasks_per_day: { label: '每日批量任务上限', unit: '次/天' }, max_rules: { label: '规则上限', unit: '条' } }
const vipLevelLabel = computed(() => VIP_LEVEL_LABEL[vipStatus.value.vipLevel] || (vipStatus.value.vipLevel ? String(vipStatus.value.vipLevel).toUpperCase() : '免费版'))
const vipLevelType = computed(() => VIP_LEVEL_TAG[vipStatus.value.vipLevel] || 'info')
const vipFeatureList = computed(() => normalizeFeatureArray(vipStatus.value.features).map(k => ({ key: k, label: FEATURE_LABELS[k] || String(k) })))
const vipLimitItems = computed(() => { const raw = vipStatus.value.limits; if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return []; return Object.entries(raw).map(([key, value]) => { const m = LIMIT_LABELS[key] || { label: key, unit: '' }; return { key, label: m.label, value, unit: m.unit } }) })
const vipDaysLeft = computed(() => { const t = toTimestamp(vipStatus.value.expiredAt); if (!t) return null; const diff = t - Date.now(); return diff <= 0 ? 0 : Math.ceil(diff / 86400000) })
const vipVerifiedStale = computed(() => { const t = toTimestamp(vipStatus.value.lastVerifiedAt); if (!t) return false; return (Date.now() - t) > 6 * 3600 * 1000 })
const VIP_CHANNEL_LABEL = { ALIX: '支付宝', WXX: '微信', UX: '闲鱼', MANX: '人工' }
const vipChannelLabel = computed(() => { const uid = vipStatus.value.communityUid || profile.value.community_uid || ''; const m = String(uid).match(/^([A-Za-z]+)/); const prefix = m ? m[1] : ''; return VIP_CHANNEL_LABEL[prefix] || '未知渠道' })
const homeVipDesc = computed(() => { const exp = formatDateTime(vipStatus.value.expiredAt); if (!exp) return '暂无到期时间'; if (vipDaysLeft.value === null) return `到期：${exp}`; if (vipDaysLeft.value <= 0) return `已过期（${exp}）`; return `到期：${exp} · 剩余 ${vipDaysLeft.value} 天` })
const features = [ { title:'帖子广场', desc:'浏览、搜索、查看、回复、收藏、点赞社区帖子', path:'/app/community/topics' }, { title:'发布帖子', desc:'发布教程、问题、资源，可设置售价和草稿', path:'/app/community/composer' }, { title:'社区钱包', desc:'查看余额、流水、充值套餐和订单', path:'/app/community/wallet' }, { title:'兑换/通知', desc:'兑换商城、排行榜、通知中心、我的内容', path:'/app/community/exchange' } ]
const txColumns = [{prop:'created_at',label:'时间',width:180},{prop:'currency_type',label:'币种',width:100},{prop:'direction',label:'方向',width:100},{prop:'amount',label:'数量',width:120},{prop:'description',label:'备注'}]
const orderColumns = [{prop:'order_no',label:'订单号',minWidth:180},{prop:'plan_name',label:'套餐',minWidth:120},{prop:'pay_channel',label:'渠道',width:120},{prop:'pay_amount',label:'金额(分)',width:120},{prop:'status',label:'状态',width:120},{prop:'created_at',label:'创建时间',minWidth:160}]

onMounted(reloadCurrent); onBeforeUnmount(destroyEditors)
watch(() => route.params.section, reloadCurrent)
watch(topicDialogVisible, async (visible) => { if (visible) await nextTick(initReplyEditor); else destroyReplyEditor() })
watch(() => composer.value.circle_id, (circleId) => {
  if (!circleId) return
  const circle = circles.value.find(c => Number(c.id) === Number(circleId))
  if (circle?.category_id) composer.value.category_id = circle.category_id
})
watch(() => composer.value.category_id, (categoryId) => {
  if (!composer.value.circle_id) return
  const circle = circles.value.find(c => Number(c.id) === Number(composer.value.circle_id))
  if (circle && Number(circle.category_id || 0) !== Number(categoryId || 0)) composer.value.circle_id = 0
})
async function reloadCurrent(){ await Promise.allSettled([loadVipStatus(), loadCategories()]); const s=section.value; if(s==='home') await Promise.allSettled([loadProfile(),loadWallet()]); else if(s==='topics') await loadTopics(); else if(s==='composer') { await Promise.allSettled([loadCategories(),loadCircles()]); await nextTick(initTopicEditor) } else { destroyTopicEditor(); if(s==='wallet') await Promise.allSettled([loadWallet(),loadTransactions(),loadRechargePlans()]); else if(s==='orders') await loadOrders(); else if(['profile','benefits'].includes(s)) await loadProfile(); else if(s==='bindings') await Promise.allSettled([loadProfile(), loadVipIdentity()]); else if(s==='circles') await loadCircles(); else if(s==='my-topics') await loadMyTopics(); else if(s==='favorites') await loadFavorites(); else if(s==='drafts') await loadDrafts(); else if(s==='purchases') await loadPurchases(); else if(s==='exchange') await loadExchangeItems(); else if(s==='notifications') await loadNotifications(); else if(s==='leaderboard') await loadLeaderboard(); else if(s==='announcements') await loadAnnouncements(); else if(s==='resources') await loadResources() } }
async function loadVipStatus(){ try{const r=await getVipStatus(); if(r.success) vipStatus.value=r.data||{}}catch(e){} }
async function loadVipIdentity(){ try{const r=await getVipIdentity(); if(r.success&&r.data){ vipStatus.value={...vipStatus.value,email:r.data.email||vipStatus.value.email,emailVerified:r.data.emailVerified??vipStatus.value.emailVerified,communityUid:r.data.communityUid||vipStatus.value.communityUid} }}catch(e){} }
async function verifyVip(){ verifying.value=true; try{const r=await verifyVipStatus(); if(r.success) { vipStatus.value=r.data||{}; ElMessage.success('权益校验完成') }}catch(e){ElMessage.error(e.message||'校验失败')}finally{verifying.value=false} }
async function loadCategories(){ try{categories.value=pickList(normalize(await communityGet('/categories'))); if(!composer.value.category_id && categories.value[0]) composer.value.category_id=categories.value[0].id; if(!circleForm.value.category_id && categories.value[0]) circleForm.value.category_id=categories.value[0].id}catch(e){} }
async function loadProfile(){ try{profile.value=normalize(await communityGet('/profile'))}catch(e){} }
async function loadWallet(){ try{wallet.value=normalize(await communityGet('/wallet'))}catch(e){} }
async function loadTopics(){ loading.value=true; try{topics.value=pickList(normalize(await communityGet('/topics', compact(topicQuery.value))))}catch(e){ElMessage.error(e.message||'加载帖子失败')}finally{loading.value=false} }
async function loadOrders(){ try{orders.value=pickList(normalize(await communityGet('/credit-orders')))}catch(e){} }
async function loadTransactions(){ try{transactions.value=pickList(normalize(await communityGet('/wallet/transactions')))}catch(e){} }
async function loadRechargePlans(){ try{rechargePlans.value=pickList(normalize(await communityGet('/recharge/plans'))).filter(p => !p.product_type || p.product_type === 'credit_recharge')}catch(e){} }
async function loadCircles(){ try{circles.value=pickList(normalize(await communityGet('/circles')))}catch(e){} }
async function loadMyTopics(){ try{myTopics.value=pickList(normalize(await communityGet('/my/topics')))}catch(e){} }
async function loadFavorites(){ try{favorites.value=pickList(normalize(await communityGet('/my/favorites')))}catch(e){} }
async function loadDrafts(){ try{drafts.value=pickList(normalize(await communityGet('/my/drafts')))}catch(e){} }
async function loadPurchases(){ try{purchases.value=pickList(normalize(await communityGet('/my/purchases')))}catch(e){} }
async function loadExchangeItems(){ try{exchangeItems.value=pickList(normalize(await communityGet('/exchange/items')))}catch(e){} }
async function loadNotifications(){ try{notifications.value=pickList(normalize(await communityGet('/notifications')))}catch(e){} }
async function loadLeaderboard(){ try{leaderboard.value=pickList(normalize(await communityGet('/leaderboard')))}catch(e){} }
async function loadAnnouncements(){ try{announcements.value=pickList(normalize(await communityGet('/announcements')))}catch(e){} }
async function loadResources(){ try{resources.value=pickList(normalize(await communityGet('/topics',{tag:'资源'})))}catch(e){resources.value=[]} }
async function submitTopic(){ syncTopicEditorContent(); if(!composer.value.title||!stripHtml(composer.value.content)||!composer.value.category_id){ElMessage.warning('分类、标题和内容不能为空');return} submitting.value=true; try{await communityPost('/topics', topicPayload()); ElMessage.success('发布成功'); resetComposer(); router.push('/app/community/topics')}catch(e){ElMessage.error(e.message||'发布失败')}finally{submitting.value=false} }
async function saveDraft(){ syncTopicEditorContent(); submitting.value=true; try{await communityPost('/my/drafts', {...topicPayload(), tags: composer.value.tagsText}); ElMessage.success('草稿已保存')}catch(e){ElMessage.error(e.message||'保存失败')}finally{submitting.value=false} }
async function openTopic(topic){ currentTopic.value=topic; topicDialogVisible.value=true; try{currentTopic.value=normalize(await communityGet(`/topics/${topic.id}`))||topic}catch(e){} try{replies.value=pickList(normalize(await communityGet(`/topics/${topic.id}/replies`)))}catch(e){replies.value=[]} await nextTick(initReplyEditor) }
async function submitReply(){ syncReplyEditorContent(); if(!stripHtml(replyText.value)||!currentTopic.value)return; submitting.value=true; try{await communityPost(`/topics/${currentTopic.value.id}/replies`,{content:replyText.value,content_format:'html'}); replyText.value=''; replyEditor.value?.clear(); await openTopic(currentTopic.value); ElMessage.success('回复成功')}catch(e){ElMessage.error(e.message||'回复失败')}finally{submitting.value=false} }
async function toggleFavorite(t){ try{await communityPost(`/topics/${t.id}/favorite`,{}); t.favored=!t.favored; ElMessage.success('收藏状态已更新')}catch(e){ElMessage.error(e.message||'操作失败')} }
async function react(t,type){ try{await communityPost(`/reactions/topic/${t.id}`,{reaction_type:type}); t.liked=true; t.like_count=(t.like_count||0)+1; ElMessage.success('已互动')}catch(e){ElMessage.error(e.message||'操作失败')} }
async function purchaseTopic(t){ try{await ElMessageBox.confirm(`确认花费 ${t.price || 0} 社区币购买？`,'购买确认'); await communityPost(`/topics/${t.id}/purchase`,{}); t.purchased=true; ElMessage.success('购买成功'); await openTopic(t)}catch(e){ if(e !== 'cancel') ElMessage.error(e.message||'购买失败') } }
async function createCreditOrder(plan){ try{const r=normalize(await communityPost('/credit-orders',{plan_id:plan.id,pay_channel:rechargeChannel.value})); currentOrder.value=r.order || r; orders.value.unshift(currentOrder.value); ElMessage.success('充值订单已创建'); await initiateOrderPayment()}catch(e){ElMessage.error(e.message||'创建充值订单失败')} }
async function openOrder(row){ currentOrder.value=row; paymentInfo.value=''; orderDialogVisible.value=true; await refreshOrder() }
async function refreshOrder(){ if(!currentOrder.value?.id)return; submitting.value=true; try{currentOrder.value=normalize(await communityGet(`/credit-orders/${currentOrder.value.id}`)); const idx=orders.value.findIndex(o=>o.id===currentOrder.value.id); if(idx>=0) orders.value[idx]=currentOrder.value }catch(e){ElMessage.error(e.message||'刷新失败')}finally{submitting.value=false} }
async function initiateOrderPayment(){ if(!currentOrder.value?.id)return; submitting.value=true; try{const channel=currentOrder.value.pay_channel || rechargeChannel.value; const r=normalize(await communityPost(`/payment/${channel}/${currentOrder.value.id}`,{})); paymentInfo.value=JSON.stringify(r,null,2); orderDialogVisible.value=true; ElMessage.success('支付信息已生成')}catch(e){ElMessage.error(e.message||'发起支付失败')}finally{submitting.value=false} }
async function exchange(item){ try{await communityPost('/exchange',{item_id:item.id}); ElMessage.success('兑换成功'); await Promise.allSettled([loadWallet(),loadExchangeItems()])}catch(e){ElMessage.error(e.message||'兑换失败')} }
async function createCircle(){ if(!circleForm.value.name){ElMessage.warning('请输入圈子名称');return} if(!circleForm.value.category_id){ElMessage.warning('请选择圈子分类');return} try{await communityPost('/circles', circleForm.value); ElMessage.success('圈子已创建'); circleForm.value={name:'',description:'',category_id:categories.value[0]?.id||null,join_policy:'free',join_price:0}; await loadCircles()}catch(e){ElMessage.error(e.message||'创建失败')} }
async function joinCircle(item){ if(item.joined)return; try{ if(item.join_policy==='paid') await ElMessageBox.confirm(`确认花费 ${item.join_price || 0} 社区币加入？`,'付费加入'); await communityPost(`/circles/${item.id}/join`,{}); ElMessage.success('加入申请已提交'); await loadCircles()}catch(e){ if(e !== 'cancel') ElMessage.error(e.message||'加入失败') } }
async function leaveCircle(item){ try{await communityDelete(`/circles/${item.id}/leave`); ElMessage.success('已退出'); await loadCircles()}catch(e){ElMessage.error(e.message||'退出失败')} }
async function deleteTopic(item){ try{await ElMessageBox.confirm('确认删除该帖子？','删除确认'); await communityDelete(`/topics/${item.id}`); ElMessage.success('已删除'); await loadMyTopics()}catch(e){ if(e !== 'cancel') ElMessage.error(e.message||'删除失败') } }
function editDraft(item){ composer.value={id:item.id,title:item.title||'',content:item.content||'',circle_id:item.circle_id||0,category_id:item.category_id||categories.value[0]?.id,price:item.price||0,tagsText:item.tags||''}; router.push('/app/community/composer'); nextTick(initTopicEditor) }
async function deleteDraft(item){ try{await communityDelete(`/my/drafts/${item.id}`); ElMessage.success('已删除'); await loadDrafts()}catch(e){ElMessage.error(e.message||'删除失败')} }
async function markNotificationRead(item){ if(item.is_read)return; try{await communityPost(`/notifications/${item.id}/read`,{}); item.is_read=true; ElMessage.success('已读')}catch(e){ElMessage.error(e.message||'操作失败')} }
async function markAllNotificationsRead(){ try{await communityPost('/notifications/read-all',{}); notifications.value.forEach(n=>n.is_read=true); ElMessage.success('已全部标为已读')}catch(e){ElMessage.error(e.message||'操作失败')} }
function initAiEditor(element, content, placeholder, onChange){ if(!element) return null; return new AiEditor({ element, content: content || '', placeholder, theme:'light', contentRetention:false, toolbarSize:'small', onChange: editor => onChange(editor.getHtml()) }) }
function initTopicEditor(){ if(!topicEditorEl.value) return; if(!topicEditor.value){ topicEditor.value = initAiEditor(topicEditorEl.value, composer.value.content, '分享教程、经验、问题或资源', html => { composer.value.content = html }) } else { topicEditor.value.setContent(composer.value.content || '') } }
function initReplyEditor(){ if(!replyEditorEl.value || !topicDialogVisible.value) return; if(!replyEditor.value){ replyEditor.value = initAiEditor(replyEditorEl.value, replyText.value, '写下你的回复', html => { replyText.value = html }) } }
function destroyTopicEditor(){ if(topicEditor.value){ topicEditor.value.destroy(); topicEditor.value = null } }
function destroyReplyEditor(){ if(replyEditor.value){ replyEditor.value.destroy(); replyEditor.value = null } }
function destroyEditors(){ destroyTopicEditor(); destroyReplyEditor() }
function syncTopicEditorContent(){ if(topicEditor.value) composer.value.content = topicEditor.value.getHtml() }
function syncReplyEditorContent(){ if(replyEditor.value) replyText.value = replyEditor.value.getHtml() }
function topicPayload(){ const circle = circles.value.find(c => Number(c.id) === Number(composer.value.circle_id)); return {title:composer.value.title,content:composer.value.content,content_format:'html',circle_id:composer.value.circle_id||0,category_id:circle?.category_id || composer.value.category_id,price:composer.value.price||0,tags:composer.value.tagsText?composer.value.tagsText.split(',').map(s=>s.trim()).filter(Boolean):[]} }
function resetComposer(){ composer.value={title:'',content:'',circle_id:0,category_id:categories.value[0]?.id||null,price:0,tagsText:''}; topicEditor.value?.clear() }
function compact(obj){ return Object.fromEntries(Object.entries(obj).filter(([,v])=>v!==''&&v!==null&&v!==undefined)) }
function normalize(res){ return res?.data || res || {} } function pickList(data){ return Array.isArray(data)?data:(data.items||data.list||data.records||data.topics||data.orders||data.transactions||data.plans||data.circles||data.notifications||[]) } function stripHtml(text){ return String(text||'').replace(/<[^>]+>/g,'').slice(0,500) } function safeHtml(html){ return String(html || '').replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '') } function formatCents(cents){ return (Number(cents||0)/100).toFixed(2) }
function parseLocalDateTime(str){ if(str === null || str === undefined) return null; let s = String(str).trim(); if(!s) return null; s = s.replace(/Z$/,'').replace(/([+-]\d{2}:?\d{2})$/,''); s = s.replace(/\.(\d{3})\d*/, '.$1'); const d = new Date(s); return isNaN(d.getTime()) ? null : d }
function formatDateTime(str){ const d = parseLocalDateTime(str); if(!d) return ''; const p = n => String(n).padStart(2,'0'); return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}` }
function toTimestamp(str){ const d = parseLocalDateTime(str); return d ? d.getTime() : null }
function normalizeFeatureArray(raw){ if(!raw) return []; let v = raw; if(typeof v === 'string'){ try{ v = JSON.parse(v) }catch{ return [] } } if(Array.isArray(v)) return v; if(v && Array.isArray(v.features)) return v.features; if(v && Array.isArray(v.value)) return v.value; return [] }
</script>

<style scoped>
.community-page{padding:2px}.hero-card{border-radius:18px;padding:24px;background:linear-gradient(135deg,#eef2ff,#fff7ed);display:flex;justify-content:space-between;gap:16px;align-items:center;margin-bottom:16px}.eyebrow{color:#7c3aed;font-weight:700;font-size:13px;margin-bottom:6px}h2{margin:0 0 8px;font-size:26px;color:#111827}p{margin:0;color:#6b7280}.section-row,.mt16{margin-top:16px;margin-bottom:16px}.mb12{margin-bottom:12px}.identity-id{font-size:24px;font-weight:800;color:#111827;margin-bottom:8px}.muted{color:#6b7280;font-size:13px;line-height:1.6}.feature-card{border-radius:16px}.feature-item{border:1px solid #eef2ff;background:#fafafa;border-radius:12px;padding:14px;min-height:84px;display:flex;flex-direction:column;gap:8px;cursor:pointer;transition:all .2s}.feature-item:hover{border-color:#6366f1;transform:translateY(-1px)}.feature-item span{color:#6b7280;font-size:13px;line-height:1.5}.card-header{display:flex;align-items:center;justify-content:space-between;gap:12px}.empty-box{text-align:center;color:#9ca3af;padding:36px}.topic-item{padding:16px 0;border-bottom:1px solid #eef2f7;cursor:pointer}.topic-title{font-size:17px;font-weight:700;color:#111827;margin-bottom:8px}.topic-meta{display:flex;gap:12px;color:#9ca3af;font-size:12px;margin-bottom:8px;flex-wrap:wrap}.topic-summary,.topic-content{color:#4b5563;line-height:1.7;white-space:pre-wrap}.topic-actions{margin-top:10px}.reply-item{padding:10px 0;border-bottom:1px dashed #e5e7eb;color:#374151}.recharge-plan{width:170px}.vip-plan-title{font-weight:700;color:#111827}.vip-plan-price{font-size:22px;font-weight:800;color:#ef4444;margin:8px 0 4px}.pay-json{background:#0f172a;color:#e5e7eb;padding:12px;border-radius:10px;white-space:pre-wrap;max-height:260px;overflow:auto;margin-top:12px}
</style>
