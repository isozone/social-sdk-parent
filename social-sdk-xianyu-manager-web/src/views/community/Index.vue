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
        <el-col :xs="24" :md="8"><metric-card title="我的身份" :icon="User" accent="#6366f1" :value="vipStatus.communityUid || profile.community_uid || '未分配'" :desc="`支付渠道：${vipChannelLabel}`" /></el-col>
        <el-col :xs="24" :md="8"><metric-card title="我的权益" :icon="Medal" accent="#f59e0b" :value="vipLevelLabel" :desc="homeVipDesc" /></el-col>
        <el-col :xs="24" :md="8"><metric-card title="社区钱包" :icon="Wallet" accent="#10b981" :value="wallet.balance ?? wallet.coins ?? 0" desc="可用于购买、打赏、兑换与资源消费" /></el-col>
      </el-row>
      <el-card shadow="never" class="feature-card">
        <template #header>客户端功能闭环</template>
        <el-row :gutter="12">
          <el-col v-for="item in features" :key="item.title" :xs="24" :sm="12" :md="6">
            <div class="feature-item" @click="$router.push(item.path)">
              <el-icon class="feature-icon"><component :is="item.icon" class="feature-svg" /></el-icon>
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </div>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <template v-else-if="section === 'topics'">
      <el-card shadow="never" class="topics-card">
        <template #header>
          <div class="card-header">
            <span>帖子广场</span>
            <div class="dt-actions">
              <el-input v-model="topicQuery.keyword" placeholder="搜索帖子" clearable @keyup.enter="loadTopics" @clear="loadTopics" />
              <el-select v-model="topicQuery.category_id" clearable placeholder="全部分类" @change="loadTopics"><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select>
              <el-button @click="loadTopics">搜索</el-button>
              <el-button type="primary" @click="$router.push('/app/community/composer')">发布帖子</el-button>
            </div>
          </div>
        </template>
        <div v-loading="loading">
          <el-empty v-if="topics.length === 0 && !loading" description="暂无帖子，去发布第一篇吧" />
          <div v-for="topic in topics" :key="topic.id" class="topic-card" @click="openTopic(topic)">
            <div class="topic-avatar">{{ (topic.user?.nickname || topic.author_name || topic.username || '匿')[0]?.toUpperCase() }}</div>
            <div class="topic-main">
              <div class="topic-head">
                <span class="topic-title">{{ topic.title }}</span>
                <el-tag v-if="topic.price > 0" type="warning" size="small" effect="light">付费 {{ topic.price }} 币</el-tag>
              </div>
              <div class="topic-meta">
                <span class="t-author">{{ topic.user?.nickname || topic.author_name || topic.username || '社区用户' }}</span>
                <span>浏览 {{ topic.view_count || 0 }}</span>
                <span>回复 {{ topic.reply_count || 0 }}</span>
                <span>点赞 {{ topic.like_count || 0 }}</span>
              </div>
              <div class="topic-summary" v-html="safeHtml(mdToHtml(topic.summary || stripHtml(topic.content || '')))"></div>
              <div v-if="topicTags(topic).length" class="topic-tags">
                <el-tag v-for="tag in topicTags(topic)" :key="tag" size="small" effect="plain" type="info">{{ tag }}</el-tag>
              </div>
            </div>
            <div class="topic-side" @click.stop>
              <el-button :type="topic.favored ? 'warning' : 'default'" size="small" :plain="!topic.favored" @click="toggleFavorite(topic)">{{ topic.favored ? '已收藏' : '收藏' }}</el-button>
              <el-button :type="topic.liked ? 'danger' : 'default'" size="small" :plain="!topic.liked" @click="react(topic, 'like')">{{ topic.liked ? '已赞' : '点赞' }}</el-button>
              <el-button v-if="topic.price > 0 && !topic.purchased" size="small" type="warning" @click="purchaseTopic(topic)">购买</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </template>

    <template v-else-if="section === 'composer'">
      <el-card shadow="never">
        <template #header>{{ composer.id ? '编辑草稿/帖子' : '发布帖子' }}</template>
        <el-form label-position="top">
          <el-form-item label="标题"><el-input v-model="composer.title" maxlength="120" show-word-limit placeholder="请输入帖子标题" /></el-form-item>
          <el-form-item label="内容"><div ref="topicEditorEl" class="community-editor"></div></el-form-item>
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item label="圈子"><el-select v-model="composer.circle_id" clearable style="width:100%"><el-option :value="0" label="不选择圈子(公共社区)" /><el-option v-for="c in joinedCircles" :key="c.id" :label="c.name" :value="c.id" /></el-select><div class="form-tip">仅展示已加入的圈子；未加入的圈子不能发帖。</div></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="分类"><el-select v-model="composer.category_id" :disabled="!composer.circle_id && globalCategories.length === 0" style="width:100%"><el-option v-for="c in filteredCategories" :key="c.id" :label="c.name" :value="c.id" /></el-select><div class="form-tip">{{ composer.circle_id > 0 ? '分类来自所选圈子。' : '不选择圈子时使用全站分类。' }}</div></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="售价社区币"><el-input-number v-model="composer.price" :min="0" style="width:100%" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="标签"><el-input v-model="composer.tagsText" placeholder="多个标签用逗号分隔" /></el-form-item>
          <el-space><el-button type="primary" :loading="submitting" @click="submitTopic">发布</el-button><el-button :loading="submitting" @click="saveDraft">保存草稿</el-button><el-button @click="resetComposer">清空</el-button></el-space>
        </el-form>
      </el-card>
    </template>

    <template v-else-if="section === 'wallet'">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8"><metric-card title="社区钱包" :icon="Wallet" accent="#10b981" :value="wallet.balance ?? wallet.coins ?? 0" :desc="`积分：${wallet.points ?? 0}`" /></el-col>
        <el-col :xs="24" :md="16">
          <el-card shadow="never"><template #header>社区币充值</template>
            <el-alert title="支持微信 / 支付宝扫码充值，也可选择充值套餐快捷购买。兑换比例：1 元 = {{ creditExchangeRate || 100 }} 社区币。" type="info" :closable="false" class="mb12" />
            <div class="custom-recharge">
              <el-radio-group v-model="rechargeChannel" class="mb12"><el-radio-button label="wechat">微信扫码</el-radio-button><el-radio-button label="alipay">支付宝扫码</el-radio-button></el-radio-group>
              <div class="custom-recharge-row">
                <el-input-number v-model="customRechargeAmount" :min="1" :step="10" :precision="0" placeholder="充值社区币数量" />
                <span class="custom-recharge-tip">≈ ¥{{ formatCents(communityCreditAmountToCents(customRechargeAmount || 0, creditExchangeRate || 100)) }}</span>
                <el-button type="primary" :loading="submitting" @click="createCustomCreditOrder">扫码充值</el-button>
              </div>
            </div>
            <el-divider>或选择充值套餐</el-divider>
            <div class="recharge-grid"><el-card v-for="plan in rechargePlans" :key="plan.id" shadow="hover" class="recharge-plan"><div class="vip-plan-title">{{ plan.name }}</div><div class="vip-plan-price">¥{{ formatCents(plan.price_cents) }}</div><div class="muted">{{ plan.coins }} 社区币</div><el-button size="small" type="primary" @click="createCreditOrder(plan)">创建并支付</el-button></el-card></div>
          </el-card>
        </el-col>
      </el-row>
      <data-table title="钱包流水" :data="transactions" :columns="txColumns" />
    </template>

    <template v-else-if="section === 'orders'">
      <data-table title="支付订单" :data="displayOrders" :columns="orderColumns">
        <template #actions>
          <el-select v-model="orderQuery.status" clearable placeholder="状态"><el-option label="待支付" value="pending" /><el-option label="已支付" value="paid" /><el-option label="已过期" value="expired" /><el-option label="已取消" value="cancelled" /></el-select>
          <el-input v-model="orderQuery.keyword" clearable placeholder="搜索订单号"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        </template>
        <template #row-actions="{ row }"><el-button size="small" @click="openOrder(row)">详情/支付</el-button></template>
      </data-table>
    </template>

    <template v-else-if="section === 'circles'">
      <el-card shadow="never" class="circle-create-card">
        <template #header>创建圈子</template>
        <el-form class="circle-create-form" label-width="72px">
          <el-row :gutter="12">
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="名称"><el-input v-model="circleForm.name" placeholder="圈子名称" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="加入方式"><el-select v-model="circleForm.join_policy" style="width:100%"><el-option label="免费" value="free" /><el-option label="审批" value="approve" /><el-option label="付费" value="paid" /></el-select></el-form-item></el-col>
            <el-col v-if="circleForm.join_policy === 'paid'" :xs="24" :sm="12" :md="8"><el-form-item label="价格"><el-input-number v-model="circleForm.join_price" :min="0" style="width:100%" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="简介"><el-input v-model="circleForm.description" type="textarea" :rows="2" placeholder="一句话介绍这个圈子，方便他人了解" /></el-form-item>
          <el-form-item label=" " style="margin-bottom:0"><el-button type="primary" @click="createCircle">创建圈子</el-button></el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="circles-card mt16">
        <template #header>
          <div class="card-header">
            <span>社区圈子</span>
            <el-button size="small" text @click="loadCircles">刷新</el-button>
          </div>
        </template>
        <div v-if="circles.length === 0" class="empty-box">暂无圈子，创建第一个吧</div>
        <div v-else class="circle-list">
          <div v-for="c in circles" :key="c.id" class="circle-item">
            <div class="circle-avatar"><el-icon class="circle-avatar-icon"><Compass /></el-icon></div>
            <div class="circle-main">
              <div class="circle-name-row">
                <span class="circle-name">{{ c.name || '-' }}</span>
                <el-tag size="small" :type="c.joined ? 'success' : 'info'" effect="light">{{ c.joined ? '已加入' : '未加入' }}</el-tag>
              </div>
              <div class="circle-meta">
                <el-tag size="small" :type="c.join_policy === 'paid' ? 'warning' : c.join_policy === 'approve' ? 'primary' : 'success'" effect="plain">{{ c.join_policy === 'paid' ? '付费 ¥' + (c.join_price || 0) : c.join_policy === 'approve' ? '审批加入' : '免费加入' }}</el-tag>
                <span v-if="c.member_count != null" class="circle-members">{{ c.member_count }} 人</span>
                <span v-if="c.category_name" class="circle-cat">{{ c.category_name }}</span>
              </div>
              <div v-if="c.description" class="circle-desc">{{ c.description }}</div>
            </div>
            <div class="circle-actions">
              <el-button v-if="!c.joined" size="small" type="primary" @click="joinCircle(c)">{{ c.join_policy === 'paid' ? '付费加入' : '加入' }}</el-button>
              <el-button v-if="c.joined" size="small" @click="leaveCircle(c)">退出</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </template>

    <template v-else-if="section === 'my-topics'"><simple-list title="我的帖子" :icon="Document" :items="myTopics" name-key="title" desc-key="summary" @reload="loadMyTopics"><template #item-actions="{ item }"><el-button size="small" @click="openTopic(item)">查看</el-button><el-button v-if="item.status !== 'published'" size="small" type="danger" @click="deleteTopic(item)">删除</el-button></template></simple-list></template>
    <template v-else-if="section === 'favorites'"><simple-list title="我的收藏" :icon="Star" :items="favorites" name-key="title" desc-key="summary" @reload="loadFavorites"><template #item-actions="{ item }"><el-button size="small" @click="openTopic(item)">查看</el-button><el-button size="small" @click="toggleFavorite(item)">取消收藏</el-button></template></simple-list></template>
    <template v-else-if="section === 'drafts'"><simple-list title="草稿箱" :icon="EditPen" :items="drafts" name-key="title" desc-key="content" @reload="loadDrafts"><template #item-actions="{ item }"><el-button size="small" @click="editDraft(item)">编辑</el-button><el-button size="small" type="danger" @click="deleteDraft(item)">删除</el-button></template></simple-list></template>
    <template v-else-if="section === 'notifications'"><simple-list title="社区通知" :icon="Message" :items="notifications" name-key="title" desc-key="content" @reload="loadNotifications"><template #header-actions><el-button @click="markAllNotificationsRead">全部已读</el-button></template><template #item-actions="{ item }"><el-button size="small" @click="markNotificationRead(item)">{{ item.is_read ? '已读' : '标为已读' }}</el-button></template></simple-list></template>
    <template v-else-if="section === 'leaderboard'">
      <el-card shadow="never" class="leaderboard-card">
        <template #header>
          <div class="card-header">
            <span>排行榜</span>
            <el-button size="small" text @click="loadLeaderboard">刷新</el-button>
          </div>
        </template>
        <div v-if="leaderboard.length === 0" class="empty-box">暂无数据</div>
        <div v-else class="lb-list">
          <div v-for="(u, i) in leaderboard" :key="u.id || u.username" class="lb-item" :class="'rank-' + (i + 1)">
            <div class="lb-rank" :class="{ 'lb-rank-top': i < 3 }">{{ i + 1 }}</div>
            <div class="lb-avatar">{{ String(u.username || '?').slice(0, 1).toUpperCase() }}</div>
            <div class="lb-main">
              <div class="lb-name">{{ u.username || '-' }}</div>
              <div class="lb-score">{{ u.score || 0 }} 积分</div>
            </div>
          </div>
        </div>
      </el-card>
    </template>
    <template v-else-if="section === 'announcements'"><simple-list title="社区公告" :icon="Bell" :items="announcements" name-key="title" desc-key="content" @reload="loadAnnouncements" /></template>

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

    <el-dialog v-model="topicDialogVisible" title="帖子详情" width="780px">
      <div v-if="currentTopic"><h3>{{ currentTopic.title }}</h3><div class="topic-meta"><span>{{ currentTopic.user?.nickname || currentTopic.author_name || currentTopic.username }}</span><span>回复 {{ replies.length }}</span><span v-if="currentTopic.price > 0">售价 {{ currentTopic.price }} 币</span></div><div class="topic-content" v-html="safeHtml(currentTopic.content || currentTopic.summary || '')"></div><el-divider content-position="left">回复</el-divider><div v-for="reply in replies" :key="reply.id" class="reply-item"><strong>{{ reply.user?.nickname || '社区用户' }}：</strong><span v-html="safeHtml(reply.content || '')"></span></div><div ref="replyEditorEl" class="community-reply-editor"></div></div>
      <template #footer><el-button @click="topicDialogVisible = false">关闭</el-button><el-button v-if="currentTopic?.price > 0 && !currentTopic?.purchased" type="warning" :loading="submitting" @click="purchaseTopic(currentTopic)">购买后查看全文</el-button><el-button type="primary" :loading="submitting" @click="submitReply">回复</el-button></template>
    </el-dialog>

    <el-dialog v-model="orderDialogVisible" title="社区币充值支付" width="680px" @closed="stopOrderPayTimers">
      <div v-if="currentOrder"><el-descriptions :column="1" border><el-descriptions-item label="订单号">{{ currentOrder.order_no }}</el-descriptions-item><el-descriptions-item label="金额">¥{{ formatCents(currentOrder.pay_amount) }}</el-descriptions-item><el-descriptions-item label="渠道">{{ PAY_CHANNEL_LABEL[currentOrder.pay_channel] || currentOrder.pay_channel || '-' }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="ORDER_STATUS_TAG[currentOrder.status] || 'info'" effect="light">{{ ORDER_STATUS_LABEL[currentOrder.status] || currentOrder.status }}</el-tag></el-descriptions-item></el-descriptions><div v-if="orderPayInfo" class="wallet-pay-box"><img v-if="orderPayQr" :src="orderPayQr" class="wallet-pay-qr" alt="支付二维码" /><div class="wallet-pay-title">{{ orderPayInfo.provider === 'alipay' ? '请使用支付宝扫码支付' : orderPayInfo.provider === 'wechat' ? '请使用微信扫码支付' : '请按提示完成支付' }}</div><div class="muted">订单 10 分钟内有效，支付成功后钱包余额会自动刷新。</div><div v-if="orderPayRemainSeconds > 0" class="wallet-pay-countdown">剩余 {{ formatPayRemain(orderPayRemainSeconds) }}</div><div v-else class="wallet-pay-expired">订单已超时，请重新创建订单</div></div></div>
      <template #footer><el-button @click="orderDialogVisible = false">关闭</el-button><el-button :loading="submitting" @click="refreshOrder(true)">刷新状态</el-button><el-button v-if="currentOrder?.status === 'pending' && !orderPayInfo" type="primary" :loading="submitting" @click="initiateOrderPayment">继续支付</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElCard, ElEmpty, ElSkeleton, ElTable, ElTableColumn, ElTag, ElMessage, ElMessageBox } from 'element-plus'
import { AiEditor } from 'aieditor'
import 'aieditor/dist/style.css'
import QRCode from 'qrcode'
import { User, Medal, Wallet, ChatLineSquare, Edit, Present, Compass, Document, Star, EditPen, Message, Trophy, Bell, Search } from '@element-plus/icons-vue'
import { communityDelete, communityGet, communityPost, getVipIdentity, getVipStatus, verifyVipStatus } from '@/api/vip'
import { mdToHtml } from '@/utils/markdown'

const MetricCard = defineComponent({ props: ['title','value','desc','icon','accent'], setup: (p) => () => h(ElCard, { shadow:'never', class:'metric-card' }, { default: () => [
  h('div', { class:'metric-top' }, [
    p.icon ? h('div', { class:'metric-icon', style: p.accent ? { color: p.accent, background: (p.accent || '#4f46e5') + '14' } : {} }, () => [h(p.icon, { class: 'metric-svg', style: { width: '14px', height: '14px', display: 'block', flexShrink: 0 } })]) : null,
    h('div', { class:'metric-title' }, p.title)
  ]),
  h('div', { class:'metric-value' }, String(p.value ?? '')),
  h('div', { class:'metric-desc' }, p.desc)
] }) })
const ListCard = defineComponent({ props: ['title','loading','empty'], setup: (p,{slots}) => () => h(ElCard,{shadow:'never',class:'list-card'},{header:()=>h('div',{class:'card-header'},[h('span',p.title), h('div', slots.actions?.())]), default:()=>h(ElSkeleton,{loading:p.loading,animated:true,rows:5},{default:()=>p.empty?h('div',{class:'empty-box'},'暂无数据'):slots.default?.()})}) })
const DataTable = defineComponent({
  props: ['title', 'data', 'columns'],
  setup: (p, { slots }) => () => h(ElCard, { shadow: 'never', class: 'mt16 data-table-card' }, {
    header: () => h('div', { class: 'card-header' }, [
      h('span', p.title),
      slots.actions ? h('div', { class: 'dt-actions' }, slots.actions()) : null
    ]),
    default: () => h(ElTable, { data: p.data || [], stripe: true }, () => [
      ...(p.columns || []).map(c => c.render
        ? h(ElTableColumn, { prop: c.prop, label: c.label, width: c.width, minWidth: c.minWidth }, { default: ({ row }) => c.render(row) })
        : h(ElTableColumn, c)),
      slots['row-actions']
        ? h(ElTableColumn, { label: '操作', width: 140 }, { default: ({ row }) => slots['row-actions']({ row }) })
        : null
    ])
  })
})
const SimpleList = defineComponent({
  props: ['title','items','nameKey','descKey','icon'],
  emits: ['reload'],
  setup: (p, { emit, slots }) => () => h(ElCard, { shadow: 'never', class: 'simple-list-card' }, {
    header: () => h('div', { class: 'card-header' }, [
      h('span', p.title),
      h('div', [slots['header-actions']?.(), h(ElButton, { size: 'small', text: true, onClick: () => emit('reload') }, () => '刷新')])
    ]),
    default: () => (p.items || []).length === 0
      ? h(ElEmpty, { description: '暂无数据' })
      : h('div', { class: 'sl-list' }, (p.items || []).map(item => h('div', { class: 'sl-item' }, [
        p.icon ? h('div', { class: 'sl-icon' }, () => [h(p.icon, { style: { width: '14px', height: '14px', display: 'block', flexShrink: 0 } })]) : null,
        h('div', { class: 'sl-body' }, [
          h('div', { class: 'sl-title' }, item[p.nameKey] || item.title || item.name || `#${item.id}`),
        h('div', { class: 'sl-desc', domProps: { innerHTML: safeHtml(item[p.descKey] || item.content || item.description || '') } }),
        ]),
        h('div', { class: 'sl-actions' }, slots['item-actions']?.({ item }))
      ])))
  })
})

const route = useRoute(); const router = useRouter(); const loading = ref(false); const submitting = ref(false); const verifying = ref(false)
const vipStatus = ref({ active:false, communityUid:'', vipLevel:'free' }); const profile = ref({}); const wallet = ref({})
const topics = ref([]); const replies = ref([]); const orders = ref([]); const transactions = ref([]); const rechargePlans = ref([]); const categories = ref([])
const circles = ref([]); const joinedCircles = computed(() => (circles.value || []).filter(c => c.joined)); const myTopics = ref([]); const favorites = ref([]); const drafts = ref([]); const notifications = ref([]); const leaderboard = ref([]); const announcements = ref([])
const topicDialogVisible = ref(false); const currentTopic = ref(null); const replyText = ref(''); const topicQuery = ref({ keyword:'', category_id:'' }); const orderQuery = ref({ status:'', keyword:'' })
const topicEditorEl = ref(null); const replyEditorEl = ref(null); const topicEditor = ref(null); const replyEditor = ref(null)
const orderDialogVisible = ref(false); const currentOrder = ref(null); const paymentInfo = ref(''); const orderPayInfo = ref(null); const orderPayQr = ref(''); const orderPayRemainSeconds = ref(0); let orderPayTimer = null; let orderPayPollTimer = null; const rechargeChannel = ref('wechat')
const customRechargeAmount = ref(100)
// 通用充值兑换率(币/元):优先取后端 wallet.credit_exchange_rate,否则从充值套餐推导,兜底 100
const creditExchangeRate = computed(() => {
  const r = Number(wallet.value?.credit_exchange_rate || 0)
  if (r > 0) return r
  const plan = (rechargePlans.value || []).find(p => Number(p.price_cents || 0) > 0 && Number(p.coins || 0) > 0)
  if (plan) return Math.round((Number(plan.coins) * 100) / Number(plan.price_cents))
  return 100
})
function communityCreditAmountToCents(amount, rate) { const r = Number(rate || 100); return Math.ceil((Number(amount || 0) * 100) / r) }
const composer = ref({ title:'', content:'', circle_id:0, category_id:null, price:0, tagsText:'' }); const circleForm = ref({ name:'', description:'', join_policy:'free', join_price:0 })
const titleMap = { home:'社区首页', profile:'我的身份', benefits:'我的权益', bindings:'账户绑定', topics:'帖子广场', composer:'发布帖子', wallet:'社区钱包', orders:'支付订单', circles:'社区圈子', 'my-topics':'我的帖子', favorites:'我的收藏', drafts:'草稿箱', notifications:'社区通知', leaderboard:'排行榜', announcements:'社区公告' }
const section = computed(() => route.params.section || 'home'); const pageTitle = computed(() => titleMap[section.value] || '社区首页')
const globalCategories = computed(() => categories.value.filter(c => Number(c.circle_id || 0) === 0))
const filteredCategories = computed(() => {
  const circleId = Number(composer.value.circle_id || 0)
  return categories.value.filter(c => Number(c.circle_id || 0) === circleId)
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
const features = [ { title:'帖子广场', desc:'浏览、搜索、查看、回复、收藏、点赞社区帖子', path:'/app/community/topics', icon: ChatLineSquare }, { title:'发布帖子', desc:'发布教程、问题、资源，可设置售价和草稿', path:'/app/community/composer', icon: Edit }, { title:'社区钱包', desc:'查看余额、流水、扫码充值套餐和订单', path:'/app/community/wallet', icon: Wallet }, { title:'通知/排行', desc:'社区通知、排行榜、我的帖子与收藏', path:'/app/community/notifications', icon: Present } ]
const ORDER_STATUS_LABEL = { pending:'待支付', paid:'已支付', success:'已支付', expired:'已过期', cancelled:'已取消', closed:'已关闭', refund:'已退款' }
const ORDER_STATUS_TAG = { pending:'warning', paid:'success', success:'success', expired:'info', cancelled:'info', closed:'info', refund:'danger' }
const PAY_CHANNEL_LABEL = { alipay:'支付宝', wxpay:'微信支付', wechat:'微信支付', unionpay:'银联', paypal:'PayPal', stripe:'Stripe' }
const txColumns = [
  { prop:'created_at', label:'时间', width:180 },
  { prop:'currency_type', label:'币种', width:100, render: r => h('span',{class:'mono'}, String(r.currency_type||'').toUpperCase()) },
  { prop:'direction', label:'方向', width:100, render: r => h(ElTag,{type: r.direction==='income'?'success':'warning', size:'small', effect:'light'},()=> r.direction==='income'?'收入':'支出') },
  { prop:'amount', label:'数量', width:120, render: r => { const v = Number(r.amount||0); return h('span',{class: v>=0?'amount-pos':'amount-neg'}, (v>=0?'+':'') + Number(v).toLocaleString()) } },
  { prop:'description', label:'备注' }
]
const orderColumns = [
  { prop:'order_no', label:'订单号', minWidth:180, render: r => h('span',{class:'mono'}, r.order_no) },
  { prop:'plan_name', label:'套餐', minWidth:120 },
  { prop:'pay_channel', label:'渠道', width:120, render: r => h('span', PAY_CHANNEL_LABEL[r.pay_channel] || r.pay_channel || '-') },
  { prop:'pay_amount', label:'金额', width:120, render: r => h('span',{class:'amount'}, '¥' + (Number(r.pay_amount||0)/100).toFixed(2)) },
  { prop:'status', label:'状态', width:120, render: r => h(ElTag,{type: ORDER_STATUS_TAG[r.status] || 'info', size:'small', effect:'light'},()=> ORDER_STATUS_LABEL[r.status] || r.status || '未知') },
  { prop:'created_at', label:'创建时间', minWidth:160 }
]
const displayOrders = computed(() => {
  const list = orders.value || []
  const kw = String(orderQuery.value.keyword || '').trim().toLowerCase()
  const st = orderQuery.value.status
  return list.filter(o => (!st || o.status === st) && (!kw || String(o.order_no||'').toLowerCase().includes(kw)))
})

onMounted(reloadCurrent); onBeforeUnmount(() => { destroyEditors(); stopOrderPayTimers() })
watch(() => route.params.section, reloadCurrent)
watch(topicDialogVisible, async (visible) => { if (visible) await nextTick(initReplyEditor); else destroyReplyEditor() })
watch(() => composer.value.circle_id, (circleId) => {
  const available = categories.value.filter(c => Number(c.circle_id || 0) === Number(circleId || 0))
  if (!available.some(c => Number(c.id) === Number(composer.value.category_id))) {
    composer.value.category_id = available[0]?.id || null
  }
})
watch(categories, () => {
  const available = filteredCategories.value
  if (!available.some(c => Number(c.id) === Number(composer.value.category_id))) {
    composer.value.category_id = available[0]?.id || null
  }
})
async function reloadCurrent(){ await Promise.allSettled([loadVipStatus(), loadCategories()]); const s=section.value; if(s==='home') await Promise.allSettled([loadProfile(),loadWallet()]); else if(s==='topics') await loadTopics(); else if(s==='composer') { await Promise.allSettled([loadCategories(),loadCircles()]); await nextTick(initTopicEditor) } else { destroyTopicEditor(); if(s==='wallet') await Promise.allSettled([loadWallet(),loadTransactions(),loadRechargePlans()]); else if(s==='orders') await loadOrders(); else if(['profile','benefits'].includes(s)) await loadProfile(); else if(s==='bindings') await Promise.allSettled([loadProfile(), loadVipIdentity()]); else if(s==='circles') await loadCircles(); else if(s==='my-topics') await loadMyTopics(); else if(s==='favorites') await loadFavorites(); else if(s==='drafts') await loadDrafts(); else if(s==='notifications') await loadNotifications(); else if(s==='leaderboard') await loadLeaderboard(); else if(s==='announcements') await loadAnnouncements() } }
async function loadVipStatus(){ try{const r=await getVipStatus(); if(r.success) vipStatus.value=r.data||{}}catch(e){} }
async function loadVipIdentity(){ try{const r=await getVipIdentity(); if(r.success&&r.data){ vipStatus.value={...vipStatus.value,email:r.data.email||vipStatus.value.email,emailVerified:r.data.emailVerified??vipStatus.value.emailVerified,communityUid:r.data.communityUid||vipStatus.value.communityUid} }}catch(e){} }
async function verifyVip(){ verifying.value=true; try{const r=await verifyVipStatus(); if(r.success) { vipStatus.value=r.data||{}; ElMessage.success('权益校验完成') }}catch(e){ElMessage.error(e.message||'校验失败')}finally{verifying.value=false} }
async function loadCategories(){ try{categories.value=pickList(normalize(await communityGet('/categories'))); const available=filteredCategories.value; if(!available.some(c=>Number(c.id)===Number(composer.value.category_id))) composer.value.category_id=available[0]?.id||null}catch(e){} }
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
async function loadNotifications(){ try{notifications.value=pickList(normalize(await communityGet('/notifications')))}catch(e){} }
async function loadLeaderboard(){ try{leaderboard.value=pickList(normalize(await communityGet('/leaderboard')))}catch(e){} }
async function loadAnnouncements(){ try{announcements.value=pickList(normalize(await communityGet('/announcements')))}catch(e){} }
async function submitTopic(){ syncTopicEditorContent(); if(!composer.value.title||!stripHtml(composer.value.content)||!composer.value.category_id){ElMessage.warning('分类、标题和内容不能为空');return} submitting.value=true; try{await communityPost('/topics', topicPayload()); ElMessage.success('发布成功'); resetComposer(); router.push('/app/community/topics')}catch(e){ElMessage.error(e.message||'发布失败')}finally{submitting.value=false} }
async function saveDraft(){ syncTopicEditorContent(); submitting.value=true; try{await communityPost('/my/drafts', {...topicPayload(), tags: composer.value.tagsText}); ElMessage.success('草稿已保存')}catch(e){ElMessage.error(e.message||'保存失败')}finally{submitting.value=false} }
function openTopic(topic) {
  // 打开独立帖子详情页,正常渲染 Markdown/图片(不再使用弹窗)
  router.push(`/app/community/topic/${topic.id}`);
}
async function submitReply(){ syncReplyEditorContent(); if(!stripHtml(replyText.value)||!currentTopic.value)return; submitting.value=true; try{await communityPost(`/topics/${currentTopic.value.id}/replies`,{content:replyText.value,content_format:'html'}); replyText.value=''; replyEditor.value?.clear(); await openTopic(currentTopic.value); ElMessage.success('回复成功')}catch(e){ElMessage.error(e.message||'回复失败')}finally{submitting.value=false} }
async function toggleFavorite(t){ try{await communityPost(`/topics/${t.id}/favorite`,{}); t.favored=!t.favored; ElMessage.success('收藏状态已更新')}catch(e){ElMessage.error(e.message||'操作失败')} }
async function react(t,type){ if(t.liked){ ElMessage.info('已点过赞'); return } try{await communityPost(`/reactions/topic/${t.id}`,{reaction_type:type}); t.liked=true; t.like_count=(t.like_count||0)+1; ElMessage.success('已互动')}catch(e){ElMessage.error(e.message||'操作失败')} }
function topicTags(t){ const val=t.tags; if(!val) return []; return Array.isArray(val)?val:String(val).split(',').map(s=>s.trim()).filter(Boolean) }
async function purchaseTopic(t){ try{await ElMessageBox.confirm(`确认花费 ${t.price || 0} 社区币购买？`,'购买确认'); await communityPost(`/topics/${t.id}/purchase`,{}); t.purchased=true; ElMessage.success('购买成功'); await openTopic(t)}catch(e){ if(e !== 'cancel') ElMessage.error(e.message||'购买失败') } }
async function createCreditOrder(plan){ try{stopOrderPayTimers(); const r=normalize(await communityPost('/credit-orders',{plan_id:plan.id,pay_channel:rechargeChannel.value})); currentOrder.value=r.order || r; orders.value.unshift(currentOrder.value); ElMessage.success('充值订单已创建'); await initiateOrderPayment()}catch(e){ElMessage.error(e.message||'创建充值订单失败')} }
async function createCustomCreditOrder(){ const amount=Number(customRechargeAmount.value||0); if(amount<=0){ElMessage.warning('请输入充值社区币数量');return} try{stopOrderPayTimers(); const r=normalize(await communityPost('/credit-orders',{amount,pay_channel:rechargeChannel.value})); currentOrder.value=r.order || r; orders.value.unshift(currentOrder.value); ElMessage.success('充值订单已创建，请扫码支付'); await initiateOrderPayment()}catch(e){ElMessage.error(e.message||'创建充值订单失败')} }
async function openOrder(row){ stopOrderPayTimers(); currentOrder.value=row; paymentInfo.value=''; orderPayInfo.value=null; orderPayQr.value=''; orderPayRemainSeconds.value=0; orderDialogVisible.value=true; await refreshOrder(false) }
async function refreshOrder(manual=false){ if(!currentOrder.value?.id)return; submitting.value=true; try{currentOrder.value=normalize(await communityGet(`/credit-orders/${currentOrder.value.id}`)); const idx=orders.value.findIndex(o=>o.id===currentOrder.value.id); if(idx>=0) orders.value[idx]=currentOrder.value; if(currentOrder.value.status==='paid'){ stopOrderPayTimers(); await Promise.allSettled([loadWallet(),loadTransactions(),loadOrders()]); if(manual) ElMessage.success('支付成功，钱包已刷新') } else if(['timeout','cancelled','failed'].includes(currentOrder.value.status)){ stopOrderPayTimers(); orderPayRemainSeconds.value=0; if(manual) ElMessage.warning('订单已结束，请重新创建订单') } else if(manual){ ElMessage.info('订单尚未支付，系统会自动轮询') } }catch(e){ if(manual) ElMessage.error(e.message||'刷新失败') }finally{submitting.value=false} }
async function initiateOrderPayment(){ if(!currentOrder.value?.id)return; submitting.value=true; try{const channel=currentOrder.value.pay_channel || rechargeChannel.value; const r=normalize(await communityPost(`/payment/${channel}/${currentOrder.value.id}`,{})); await setOrderPayInfo(r); orderDialogVisible.value=true; ElMessage.success('支付二维码已生成')}catch(e){ElMessage.error(e.message||'发起支付失败')}finally{submitting.value=false} }
async function setOrderPayInfo(info){ stopOrderPayTimers(); orderPayInfo.value=info||null; paymentInfo.value=''; orderPayQr.value=''; orderPayRemainSeconds.value=Number(info?.expires_in||600); const qrText=info?.code_url||info?.pay_url||info?.h5_url||''; if(qrText){ orderPayQr.value=await QRCode.toDataURL(qrText,{width:260,margin:1}) } if(info?.provider==='alipay'||info?.provider==='wechat'){ startOrderPayTimers() } }
function stopOrderPayTimers(){ if(orderPayTimer) clearInterval(orderPayTimer); if(orderPayPollTimer) clearInterval(orderPayPollTimer); orderPayTimer=null; orderPayPollTimer=null }
function startOrderPayTimers(){ stopOrderPayTimers(); orderPayTimer=setInterval(()=>{ orderPayRemainSeconds.value=Math.max(0,orderPayRemainSeconds.value-1); if(orderPayRemainSeconds.value<=0) stopOrderPayTimers() },1000); orderPayPollTimer=setInterval(()=>refreshOrder(false),3000) }
function formatPayRemain(seconds){ const s=Math.max(0,Number(seconds||0)); const m=Math.floor(s/60); const r=s%60; return `${m}:${String(r).padStart(2,'0')}` }
async function createCircle(){ if(!circleForm.value.name){ElMessage.warning('请输入圈子名称');return} try{await communityPost('/circles', circleForm.value); ElMessage.success('圈子已创建，系统已自动初始化圈子分类'); circleForm.value={name:'',description:'',join_policy:'free',join_price:0}; await Promise.allSettled([loadCircles(),loadCategories()])}catch(e){ElMessage.error(e.message||'创建失败')} }
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
function topicPayload(){ return {title:composer.value.title,content:composer.value.content,content_format:'html',circle_id:composer.value.circle_id||0,category_id:composer.value.category_id,price:composer.value.price||0,tags:composer.value.tagsText?composer.value.tagsText.split(',').map(s=>s.trim()).filter(Boolean):[]} }
function resetComposer(){ composer.value={title:'',content:'',circle_id:0,category_id:globalCategories.value[0]?.id||null,price:0,tagsText:''}; topicEditor.value?.clear() }
function compact(obj){ return Object.fromEntries(Object.entries(obj).filter(([,v])=>v!==''&&v!==null&&v!==undefined)) }
function normalize(res){ return res?.data || res || {} } function pickList(data){ return Array.isArray(data)?data:(data.items||data.list||data.records||data.topics||data.orders||data.transactions||data.plans||data.circles||data.notifications||[]) } function stripHtml(text){ return String(text||'').replace(/<[^>]+>/g,'').slice(0,500) } function safeHtml(html){ return String(html || '')
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?>[\s\S]*?<\/style>/gi, '')
    .replace(/<iframe[\s\S]*?>[\s\S]*?<\/iframe>/gi, '')
    .replace(/<object[\s\S]*?>[\s\S]*?<\/object>/gi, '')
    .replace(/<embed[\s\S]*?>[\s\S]*?<\/embed>/gi, '')
    .replace(/<link[\s\S]*?>/gi, '')
    .replace(/<meta[\s\S]*?>/gi, '')
    // 移除事件属性 on*（防 <img onerror=...> 等存储型 XSS）
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    // 移除 javascript:/data: 危险协议
    .replace(/(javascript|vbscript|data):/gi, '$1&#58;')
 } function formatCents(cents){ return (Number(cents||0)/100).toFixed(2) }function parseLocalDateTime(str){ if(str === null || str === undefined) return null; let s = String(str).trim(); if(!s) return null; s = s.replace(/Z$/,'').replace(/([+-]\d{2}:?\d{2})$/,''); s = s.replace(/\.(\d{3})\d*/, '.$1'); const d = new Date(s); return isNaN(d.getTime()) ? null : d }
function formatDateTime(str){ const d = parseLocalDateTime(str); if(!d) return ''; const p = n => String(n).padStart(2,'0'); return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}` }
function toTimestamp(str){ const d = parseLocalDateTime(str); return d ? d.getTime() : null }
function normalizeFeatureArray(raw){ if(!raw) return []; let v = raw; if(typeof v === 'string'){ try{ v = JSON.parse(v) }catch{ return [] } } if(Array.isArray(v)) return v; if(v && Array.isArray(v.features)) return v.features; if(v && Array.isArray(v.value)) return v.value; return [] }
</script>

<style scoped>
.community-page { padding: 0; }
.community-page > * { margin-bottom: 16px; }
.community-page > *:last-child { margin-bottom: 0; }

/* 统一卡片圆角与边框（含嵌套卡片） */
.community-page .el-card { border-radius: 16px !important; border-color: var(--border) !important; }

/* Hero 头部 */
.hero-card { border-radius: 18px; padding: 22px 26px; background: linear-gradient(135deg, var(--el-color-primary-light-9), #fff7ed); border: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; box-shadow: 0 6px 20px rgba(79,70,229,.06); }
.hero-card .eyebrow { color: var(--brand); font-weight: 700; font-size: 13px; letter-spacing: .5px; margin-bottom: 6px; }
.hero-card h2 { margin: 0 0 8px; font-size: 24px; color: var(--text-1); }
.hero-card p { margin: 0; color: var(--text-2); line-height: 1.6; max-width: 640px; }
.hero-card .el-space { flex-wrap: wrap; }

/* 卡片标题区 */
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.dt-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

/* MetricCard 指标卡 */
.metric-card { transition: transform .2s ease, box-shadow .2s ease; }
.metric-card:hover { transform: translateY(-2px); box-shadow: 0 10px 24px rgba(79,70,229,.10); }
.metric-card :deep(.el-card__body) { padding: 18px 20px; display: flex; flex-direction: column; gap: 8px; }
.metric-top { display: flex; align-items: center; gap: 10px; }
.metric-icon { width: 24px; height: 24px; min-width: 24px; min-height: 24px; max-width: 24px; max-height: 24px; border-radius: 7px; display: inline-flex; align-items: center; justify-content: center; background: var(--el-color-primary-light-9); color: var(--brand); font-size: 14px; flex: 0 0 24px; line-height: 1; overflow: hidden; box-sizing: border-box; }
.metric-icon :deep(svg), .metric-icon :deep(.metric-svg) { width: 14px !important; height: 14px !important; min-width: 14px !important; min-height: 14px !important; max-width: 14px !important; max-height: 14px !important; display: block !important; flex: 0 0 14px !important; }
.metric-title { font-size: 13px; color: var(--text-2); font-weight: 500; }
.metric-value { font-size: 22px; font-weight: 800; color: var(--text-1); word-break: break-word; line-height: 1.25; }
.metric-desc { font-size: 12.5px; color: var(--text-3); line-height: 1.5; }

/* 功能闭环卡片 */
.feature-card :deep(.el-card__body) { padding: 18px 20px; }
.feature-card .el-row { row-gap: 12px; }
.feature-item { border: 1px solid var(--border); background: var(--bg-soft); border-radius: 14px; padding: 16px; min-height: 96px; display: flex; flex-direction: column; gap: 8px; cursor: pointer; transition: all .2s ease; }
.feature-item:hover { border-color: var(--brand); background: #fff; transform: translateY(-2px); box-shadow: 0 10px 22px rgba(79,70,229,.10); }
.feature-icon { width: 22px !important; height: 22px !important; min-width: 22px !important; min-height: 22px !important; max-width: 22px !important; max-height: 22px !important; font-size: 18px; color: var(--brand); line-height: 1; flex: 0 0 22px; overflow: hidden; }
.feature-icon :deep(svg), .feature-icon :deep(.feature-svg) { width: 18px !important; height: 18px !important; min-width: 18px !important; min-height: 18px !important; max-width: 18px !important; max-height: 18px !important; display: block !important; flex: 0 0 18px !important; }
.feature-item strong { font-size: 15px; color: var(--text-1); }
.feature-item span { color: var(--text-2); font-size: 13px; line-height: 1.5; }

/* 列表类卡片（帖子/表格） */
.list-card :deep(.el-card__body), .data-table-card :deep(.el-card__body) { padding: 12px 16px; }
.data-table-card :deep(.el-table) { font-size: 13px; }
.data-table-card :deep(.el-table) th.el-table__cell { background: var(--bg-soft); color: var(--text-2); font-weight: 600; }
.simple-list-card :deep(.el-card__body) { padding: 8px 12px; }

/* 帖子广场列表 */
.empty-box { text-align: center; color: var(--text-3); padding: 36px; }
.topics-card :deep(.el-card__body) { padding: 6px 4px; }
.topic-card { display: flex; align-items: flex-start; gap: 14px; padding: 16px 14px; border-bottom: 1px solid var(--border); cursor: pointer; transition: background .15s ease; }
.topic-card:last-child { border-bottom: none; }
.topic-card:hover { background: var(--bg-soft); }
.topic-avatar { width: 44px; height: 44px; min-width: 44px; border-radius: 12px; background: linear-gradient(135deg, var(--brand), #8b5cf6); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700; flex-shrink: 0; }
.topic-main { flex: 1; min-width: 0; }
.topic-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.topic-title { font-size: 16px; font-weight: 700; color: var(--text-1); line-height: 1.4; }
.topic-meta { display: flex; gap: 14px; color: var(--text-3); font-size: 12px; margin-bottom: 6px; flex-wrap: wrap; }
.topic-meta .t-author { color: var(--brand); font-weight: 600; }
.topic-summary, .topic-content { color: var(--text-2); line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.topic-card .topic-summary { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 13px; }
.topic-tags { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
.topic-side { display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; }
.reply-item { padding: 10px 0; border-bottom: 1px dashed var(--border); color: var(--text-1); }

/* 通用简单列表（我的帖子/收藏/草稿/兑换/排行榜等） */
.sl-list { display: flex; flex-direction: column; gap: 2px; }
.sl-item { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 10px; transition: background .15s ease; }
.sl-item:hover { background: var(--bg-soft); }
.sl-icon { width: 22px; height: 22px; min-width: 22px; min-height: 22px; max-width: 22px; max-height: 22px; border-radius: 6px; background: var(--el-color-primary-light-9); color: var(--brand); display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0; box-sizing: border-box; overflow: hidden; }
.sl-icon :deep(svg), .sl-icon :deep(.el-icon) { width: 14px !important; height: 14px !important; min-width: 14px !important; min-height: 14px !important; max-width: 14px !important; max-height: 14px !important; display: block !important; flex: 0 0 14px !important; }
.sl-body { flex: 1; min-width: 0; }
.sl-title { font-size: 14.5px; font-weight: 600; color: var(--text-1); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sl-desc { font-size: 12.5px; color: var(--text-3); margin-top: 3px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.sl-actions { display: flex; gap: 6px; flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; }
.circle-create-form .el-form-item { margin-bottom: 0; margin-right: 14px; }
.circle-create-form .el-button { margin-left: 2px; }

/* 充值套餐网格 */
.recharge-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(168px, 1fr)); gap: 14px; margin-top: 4px; }
.recharge-plan { border-radius: 14px; text-align: center; transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease; }
.recharge-plan:hover { transform: translateY(-2px); border-color: var(--brand); box-shadow: 0 10px 22px rgba(79,70,229,.10); }
.vip-plan-title { font-weight: 700; color: var(--text-1); }
.vip-plan-price { font-size: 22px; font-weight: 800; color: #ef4444; margin: 8px 0 4px; }
.recharge-plan .muted { color: var(--text-3); font-size: 12.5px; }
.recharge-plan .el-button { margin-top: 8px; }

/* 表格单元格：等宽字体 / 金额 / 收支 */
.mono { font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace); font-size: 12.5px; color: var(--text-2); }
.amount { font-weight: 700; color: var(--text-1); }
.amount-pos { color: var(--el-color-success); font-weight: 600; }
.amount-neg { color: var(--el-color-warning); font-weight: 600; }

/* 间距工具类 */
.mt16 { margin-top: 16px; }
.mb12 { margin-bottom: 12px; }
.muted { color: var(--text-3); font-size: 13px; line-height: 1.6; }

/* 编辑器 */
.community-editor { height: 360px; border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.community-editor :deep(.aieditor) { height: 100%; }
.community-reply-editor { min-height: 120px; border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }

/* 弹窗 */
.community-page .el-dialog { border-radius: 14px; }
.pay-json { background: #0f172a; color: #e5e7eb; padding: 12px; border-radius: 10px; white-space: pre-wrap; max-height: 260px; overflow: auto; margin-top: 12px; }
.topic-content { max-height: 420px; overflow: auto; }

/* 排行榜 */
.leaderboard-card :deep(.el-card__body) { padding: 8px 12px; }
.lb-list { display: flex; flex-direction: column; gap: 2px; }
.lb-item { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 10px; transition: background .15s ease; }
.lb-item:hover { background: var(--bg-soft); }
.lb-rank { width: 26px; height: 26px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 13px; background: var(--el-fill-color-light); color: var(--text-2); flex-shrink: 0; }
.lb-rank-top { color: #fff; }
.rank-1 .lb-rank { background: linear-gradient(135deg,#f59e0b,#fde68a); box-shadow: 0 2px 6px rgba(245,158,11,.40); }
.rank-2 .lb-rank { background: linear-gradient(135deg,#94a3b8,#e2e8f0); box-shadow: 0 2px 6px rgba(148,163,184,.40); }
.rank-3 .lb-rank { background: linear-gradient(135deg,#d97706,#fbbf24); box-shadow: 0 2px 6px rgba(217,119,6,.40); }
.lb-avatar { width: 36px; height: 36px; border-radius: 50%; background: var(--el-color-primary-light-9); color: var(--brand); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 15px; flex-shrink: 0; }
.lb-main { flex: 1; min-width: 0; }
.lb-name { font-size: 14px; font-weight: 600; color: var(--text-1); }
.lb-score { font-size: 12.5px; color: var(--text-3); margin-top: 2px; }

/* 圈子 */
.circle-create-card :deep(.el-card__body) { padding: 16px 18px; }
.circle-create-form .el-row { margin-bottom: 0; }
.circle-create-form .el-form-item { margin-bottom: 14px; }
.circles-card :deep(.el-card__body) { padding: 8px 12px; }
.circle-list { display: flex; flex-direction: column; gap: 2px; }
.circle-item { display: flex; align-items: flex-start; gap: 12px; padding: 14px 12px; border-radius: 10px; transition: background .15s ease; }
.circle-item:hover { background: var(--bg-soft); }
.circle-avatar { width: 40px; height: 40px; border-radius: 12px; background: var(--el-color-primary-light-9); color: var(--brand); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.circle-avatar-icon { font-size: 20px; }
.circle-avatar-icon :deep(svg) { width: 20px !important; height: 20px !important; }
.circle-main { flex: 1; min-width: 0; }
.circle-name-row { display: flex; align-items: center; gap: 8px; }
.circle-name { font-size: 15px; font-weight: 700; color: var(--text-1); }
.circle-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 6px; }
.circle-members, .circle-cat { font-size: 12.5px; color: var(--text-3); }
.circle-desc { font-size: 13px; color: var(--text-2); line-height: 1.6; margin-top: 6px; white-space: pre-wrap; }
.circle-actions { flex-shrink: 0; display: flex; gap: 8px; }
</style>
