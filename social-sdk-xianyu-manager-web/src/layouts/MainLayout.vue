<template>
  <el-container class="layout">
    <el-aside width="224px" class="sidebar">
      <div class="logo">
        <div class="logo-icon"><span>AI</span></div>
        <div class="logo-text">
          <div class="logo-title">AI鱼多宝</div>
          <div class="logo-tag">Xianyu Manager</div>
          <div class="logo-subtitle">智能运营平台</div>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="var(--text-2)"
        active-text-color="#fff"
      >
        <template v-if="isCommunityWorkspace">
          <el-menu-item index="/app/community/home">
            <span class="menu-icon-box"><el-icon><Connection /></el-icon></span>
            <span>社区首页</span>
          </el-menu-item>
          <el-menu-item index="/app/community/profile">
            <span class="menu-icon-box"><el-icon><User /></el-icon></span>
            <span>我的身份</span>
          </el-menu-item>
          <el-menu-item index="/app/community/benefits">
            <span class="menu-icon-box"><el-icon><Medal /></el-icon></span>
            <span>我的权益</span>
          </el-menu-item>
          <el-menu-item index="/app/community/bindings">
            <span class="menu-icon-box"><el-icon><Link /></el-icon></span>
            <span>账户绑定</span>
          </el-menu-item>
          <el-menu-item index="/app/community/topics">
            <span class="menu-icon-box"><el-icon><ChatLineSquare /></el-icon></span>
            <span>帖子广场</span>
          </el-menu-item>
          <el-menu-item index="/app/community/composer">
            <span class="menu-icon-box"><el-icon><Edit /></el-icon></span>
            <span>发布帖子</span>
          </el-menu-item>
          <el-menu-item index="/app/community/wallet">
            <span class="menu-icon-box"><el-icon><Wallet /></el-icon></span>
            <span>社区钱包</span>
          </el-menu-item>
          <el-menu-item index="/app/community/orders">
            <span class="menu-icon-box"><el-icon><Money /></el-icon></span>
            <span>支付订单</span>
          </el-menu-item>
          <el-menu-item index="/app/community/announcements">
            <span class="menu-icon-box"><el-icon><Bell /></el-icon></span>
            <span>社区公告</span>
          </el-menu-item>
          <el-menu-item index="/app/community/resources">
            <span class="menu-icon-box"><el-icon><Document /></el-icon></span>
            <span>资源中心</span>
          </el-menu-item>
          <el-menu-item index="/app/community/circles">
            <span class="menu-icon-box"><el-icon><Compass /></el-icon></span>
            <span>社区圈子</span>
          </el-menu-item>
          <el-menu-item index="/app/community/my-topics">
            <span class="menu-icon-box"><el-icon><Document /></el-icon></span>
            <span>我的帖子</span>
          </el-menu-item>
          <el-menu-item index="/app/community/favorites">
            <span class="menu-icon-box"><el-icon><Star /></el-icon></span>
            <span>我的收藏</span>
          </el-menu-item>
          <el-menu-item index="/app/community/drafts">
            <span class="menu-icon-box"><el-icon><EditPen /></el-icon></span>
            <span>草稿箱</span>
          </el-menu-item>
          <el-menu-item index="/app/community/purchases">
            <span class="menu-icon-box"><el-icon><ShoppingBag /></el-icon></span>
            <span>我的购买</span>
          </el-menu-item>
          <el-menu-item index="/app/community/exchange">
            <span class="menu-icon-box"><el-icon><Present /></el-icon></span>
            <span>兑换商城</span>
          </el-menu-item>
          <el-menu-item index="/app/community/notifications">
            <span class="menu-icon-box"><el-icon><Message /></el-icon></span>
            <span>社区通知</span>
          </el-menu-item>
          <el-menu-item index="/app/community/leaderboard">
            <span class="menu-icon-box"><el-icon><Trophy /></el-icon></span>
            <span>排行榜</span>
          </el-menu-item>
          <el-menu-item index="/app/community/support">
            <span class="menu-icon-box"><el-icon><Service /></el-icon></span>
            <span>工单支持</span>
          </el-menu-item>
          <el-menu-item index="/app/dashboard">
            <span class="menu-icon-box"><el-icon><Back /></el-icon></span>
            <span>返回运营管理</span>
          </el-menu-item>
        </template>
        <template v-else>
        <el-menu-item index="/app/dashboard">
          <span class="menu-icon-box"><el-icon><DataAnalysis /></el-icon></span>
          <span>仪表盘</span>
        </el-menu-item>

        <el-menu-item-group title="店铺管理">
          <el-menu-item index="/app/products">
            <span class="menu-icon-box"><el-icon><Goods /></el-icon></span>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item index="/app/orders">
            <span class="menu-icon-box"><el-icon><List /></el-icon></span>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item index="/app/messages">
            <span class="menu-icon-box"><el-icon><ChatDotRound /></el-icon></span>
            <span>消息管理</span>
          </el-menu-item>
          <el-menu-item index="/app/collect">
            <span class="menu-icon-box"><el-icon><Star /></el-icon></span>
            <span>收藏关注</span>
          </el-menu-item>
          <el-menu-item index="/app/reviews">
            <span class="menu-icon-box"><el-icon><Medal /></el-icon></span>
            <span>评价与信用</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="AI 智能">
          <el-menu-item index="/app/ai-ops">
            <span class="menu-icon-box"><el-icon><Promotion /></el-icon></span>
            <span>AI 运营</span>
          </el-menu-item>
          <el-menu-item index="/app/ai">
            <span class="menu-icon-box"><el-icon><Connection /></el-icon></span>
            <span>AI 厂商</span>
          </el-menu-item>
          <el-menu-item index="/app/ai-cs">
            <span class="menu-icon-box"><el-icon><Service /></el-icon></span>
            <span>AI 客服</span>
          </el-menu-item>
          <el-menu-item index="/app/polish">
            <span class="menu-icon-box"><el-icon><Sunrise /></el-icon></span>
            <span>商品擦亮</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="发货仓储">
          <el-menu-item index="/app/virtual-ship">
            <span class="menu-icon-box"><el-icon><Switch /></el-icon></span>
            <span>虚拟发货</span>
          </el-menu-item>
          <el-menu-item index="/app/cloud-storage">
            <span class="menu-icon-box"><el-icon><UploadFilled /></el-icon></span>
            <span>网盘存储</span>
          </el-menu-item>
          <el-menu-item index="/app/tasks">
            <span class="menu-icon-box"><el-icon><Timer /></el-icon></span>
            <span>监控任务</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="规则合规">
          <el-menu-item index="/app/rules">
            <span class="menu-icon-box"><el-icon><Setting /></el-icon></span>
            <span>规则管理</span>
          </el-menu-item>
          <el-menu-item index="/app/notify">
            <span class="menu-icon-box"><el-icon><Bell /></el-icon></span>
            <span>消息通知</span>
          </el-menu-item>
          <el-menu-item index="/app/audit">
            <span class="menu-icon-box"><el-icon><Document /></el-icon></span>
            <span>审计日志</span>
          </el-menu-item>
          <el-menu-item index="/app/reply-logs">
            <span class="menu-icon-box"><el-icon><ChatLineSquare /></el-icon></span>
            <span>自动回复日志</span>
          </el-menu-item>
          <el-menu-item index="/app/logs/cookies-refresh">
            <span class="menu-icon-box"><el-icon><Refresh /></el-icon></span>
            <span>Cookie 刷新日志</span>
          </el-menu-item>
          <el-menu-item index="/app/logs/login-renew">
            <span class="menu-icon-box"><el-icon><Avatar /></el-icon></span>
            <span>登录续期日志</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="数据资产">
          <el-menu-item index="/app/wallet">
            <span class="menu-icon-box"><el-icon><Money /></el-icon></span>
            <span>钱包资产</span>
          </el-menu-item>
          <el-menu-item index="/app/monitor">
            <span class="menu-icon-box"><el-icon><Monitor /></el-icon></span>
            <span>监控面板</span>
          </el-menu-item>
          <el-menu-item index="/app/market">
            <span class="menu-icon-box"><el-icon><Compass /></el-icon></span>
            <span>市场情报</span>
          </el-menu-item>
          <el-menu-item index="/app/buyer">
            <span class="menu-icon-box"><el-icon><User /></el-icon></span>
            <span>买家画像</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item index="/app/accounts">
          <span class="menu-icon-box"><el-icon><UserFilled /></el-icon></span>
          <span>账号管理</span>
        </el-menu-item>
        <el-menu-item index="/app/chrome">
          <span class="menu-icon-box"><el-icon><Setting /></el-icon></span>
          <span>浏览器配置</span>
        </el-menu-item>
        <el-menu-item index="/app/proxy">
          <span class="menu-icon-box"><el-icon><Connection /></el-icon></span>
          <span>代理管理</span>
        </el-menu-item>
        <el-menu-item index="/app/circuit-breaker">
          <span class="menu-icon-box"><el-icon><Warning /></el-icon></span>
          <span>熔断器管理</span>
        </el-menu-item>
        <el-menu-item index="/app/logs">
          <span class="menu-icon-box"><el-icon><Delete /></el-icon></span>
          <span>日志管理</span>
        </el-menu-item>

        <el-menu-item-group title="系统">
          <el-menu-item index="/app/profile">
            <span class="menu-icon-box"><el-icon><User /></el-icon></span>
            <span>个人中心</span>
          </el-menu-item>
        </el-menu-item-group>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-icon-box">
            <el-icon :size="15"><component :is="headerIconComponent" /></el-icon>
          </span>
          <div class="page-title-area">
            <div class="page-title">{{ currentTitle }}</div>
            <div class="page-breadcrumb">
              <span v-for="(crumb, idx) in currentBreadcrumb" :key="idx">
                <span class="crumb">{{ crumb }}</span>
                <span v-if="idx < currentBreadcrumb.length - 1" class="crumb-sep">/</span>
              </span>
            </div>
          </div>
        </div>
        <div class="header-right">
          <el-button text circle @click="openBrowserConfig" title="浏览器配置">
            <el-icon :size="18"><Setting /></el-icon>
          </el-button>
          <el-button text circle @click="openDataBoard" title="实时大屏（新窗口）">
            <el-icon :size="18"><FullScreen /></el-icon>
          </el-button>
          <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-badge">
            <el-button text circle @click="openInbox" title="站内通知">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-dropdown v-if="vipHeader.state === 'active' || vipHeader.state === 'expired'" @command="handleCommunityCommand">
            <el-button class="vip-entry active" round>
              <el-icon><Connection /></el-icon>
              {{ vipHeader.label || 'I 社区' }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="enter">进入 I 社区</el-dropdown-item>
                <el-dropdown-item command="profile">我的身份</el-dropdown-item>
                <el-dropdown-item command="benefits">我的权益</el-dropdown-item>
                <el-dropdown-item command="bindings">账户绑定</el-dropdown-item>
                <el-dropdown-item command="orders">支付订单</el-dropdown-item>
                <el-dropdown-item divided command="renew">续费升级</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-else class="vip-entry" round @click="openVipDialog">
            <el-icon><Lock /></el-icon>
            {{ vipHeader.label || '解锁 VIP' }}
          </el-button>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <span class="avatar">{{ (authStore.user?.displayName || '管').charAt(0) }}</span>
              <span class="user-name">{{ authStore.user?.displayName || '管理员' }}</span>
              <el-icon class="avatar-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>

      <el-footer class="app-footer">
        <span class="coop-label">商务合作</span>
        <button class="coop-item" type="button" @click="openWechatQr" title="点击查看二维码">
          <svg class="coop-ico wechat" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M8.5 3C4.9 3 2 5.6 2 8.8c0 1.8 1 3.4 2.6 4.4L4 15.5l2.7-1.4c.8.2 1.6.3 2.5.3h.4c-.2-.7-.3-1.4-.3-2.1 0-3.1 2.9-5.5 6.5-5.5h.5C15.6 4.9 12.4 3 8.5 3z" />
            <path d="M21.5 13.2c0-2.5-2.5-4.5-5.6-4.5S10.3 10.7 10.3 13.2s2.5 4.5 5.6 4.5c.7 0 1.4-.1 2-.3l2.4 1.2-.5-1.9c1.2-.7 1.8-1.8 1.8-3z" />
          </svg>
          <span>worker_680</span>
        </button>
        <a class="coop-item" href="tel:18268185209">
          <svg class="coop-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2 4.1 2 2 0 0 1 4 2h3a2 2 0 0 1 2 1.7c.1 1 .3 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7A2 2 0 0 1 22 16.9z" />
          </svg>
          <span>18268185209</span>
        </a>
        <span class="footer-divider">·</span>
        <span class="copyright">© 2026 AI鱼多宝</span>
      </el-footer>

      <!-- 站内通知收件箱抽屉 -->
      <el-drawer v-model="inboxVisible" title="站内通知" direction="rtl" size="380px">
        <div class="inbox-head">
          <span v-if="unread > 0">有 <strong>{{ unread }}</strong> 条未读</span>
          <span v-else>全部已读</span>
          <el-button text type="primary" :disabled="unread === 0" @click="readAll">全部已读</el-button>
        </div>
        <el-empty v-if="!messages.length" description="暂无通知" />
        <div
          v-for="m in messages"
          :key="m.id"
          class="inbox-item"
          :class="{ unread: !m.isRead }"
          @click="readMsg(m)"
        >
          <div class="inbox-title">
            <span class="inbox-dot" v-if="!m.isRead" />
            {{ m.title }}
          </div>
          <div class="inbox-content">{{ m.content }}</div>
          <div class="inbox-meta">{{ scenarioLabel(m.scenario) }} · {{ m.createdAt }}</div>
        </div>
      </el-drawer>

      <!-- I 社区接入弹窗 -->
      <el-dialog v-model="vipDialogVisible" title="I 社区接入" width="680px" class="vip-dialog">
        <el-steps :active="vipStep" align-center finish-status="success" class="vip-steps">
          <el-step title="绑定邮箱" :description="vipIdentity.emailVerified ? vipIdentity.email : '用于查询与恢复'" />
          <el-step title="校验权益" description="查询付费与有效期" />
          <el-step title="解锁 VIP" description="下发密钥并接入" />
        </el-steps>

        <!-- 阶段一：绑定邮箱（入口） -->
        <template v-if="!vipIdentity.emailVerified">
          <el-alert type="info" :closable="false" class="vip-stage-guide">
            <template #title>第一步：绑定邮箱</template>
            绑定邮箱后即可查询该账户的 I 社区付费状态与套餐有效期；重装 / 换机时也能凭此邮箱恢复接入密钥与 VIP 身份。
          </el-alert>
          <el-form class="vip-email-form" inline>
            <el-form-item label="邮箱">
              <el-input v-model="vipEmailForm.email" placeholder="user@example.com" style="width:240px" />
            </el-form-item>
            <el-form-item label="验证码">
              <el-input v-model="vipEmailForm.code" maxlength="6" placeholder="6 位验证码" style="width:140px" />
            </el-form-item>
            <el-form-item>
              <el-button :disabled="emailCooldown > 0" :loading="emailSending" @click="sendVipCode">
                {{ emailCooldown > 0 ? emailCooldown + 's 后重发' : '发送验证码' }}
              </el-button>
              <el-button type="primary" :loading="emailVerifying" @click="verifyVipCode">验证并绑定</el-button>
            </el-form-item>
          </el-form>
        </template>

        <!-- 阶段二/三：已绑邮箱，按权益状态分支 -->
        <template v-else>
          <!-- 已激活 / 已恢复：返回密钥并解锁 -->
          <template v-if="vipUnlocked">
            <div class="vip-hero">
              <div>
                <h3>{{ vipRecovered ? '已通过邮箱恢复 I 社区接入' : 'I 社区已接入' }}</h3>
                <p>接入密钥有效，VIP 与社区全部功能已解锁。</p>
              </div>
              <el-tag type="success" size="large">已激活</el-tag>
            </div>
            <el-descriptions :column="2" border class="vip-identity-alert">
              <el-descriptions-item label="接入状态">生效中</el-descriptions-item>
              <el-descriptions-item label="社区身份">{{ vipIdentity.communityUid || '-' }}</el-descriptions-item>
              <el-descriptions-item label="接入 AppId">{{ accessAppId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="接入有效期">{{ formatExpiry(accessExpiredAt) }}</el-descriptions-item>
              <el-descriptions-item label="VIP 等级">{{ vipIdentity.vipLevel || 'pro' }}</el-descriptions-item>
            </el-descriptions>
            <el-alert class="vip-identity-alert" type="success" :closable="false" :title="vipRecovered ? '已凭邮箱恢复本部署接入密钥' : '接入密钥已由 I 社区下发并自动生效'" />
          </template>

          <!-- 未付费 / 已过期：展示套餐 + 原因 -->
          <template v-else>
            <el-alert :type="vipPayReason === 'expired' ? 'warning' : 'info'" :closable="false" class="vip-stage-guide">
              <template #title>{{ vipPayReason === 'expired' ? '接入已过期' : '尚未购买 I 社区接入' }}</template>
              <template v-if="vipPayReason === 'expired'">
                <span v-if="accessConfigured && !accessKeyValid()">本部署的接入密钥已于 {{ formatExpiry(accessExpiredAt) }} 到期，请续费以继续使用 VIP 功能。</span>
                <span v-else>该邮箱账户（{{ vipIdentity.email }}）的 I 社区套餐已到期，请续费以继续使用 VIP 功能。</span>
              </template>
              <template v-else>
                该邮箱账户（{{ vipIdentity.email }}）尚未付费，请选择下方套餐完成支付，支付成功后接入密钥将自动下发。
              </template>
            </el-alert>

            <div v-loading="plansLoading" class="access-plans">
              <el-card v-for="plan in accessPlans" :key="plan.id" shadow="hover" class="access-plan" :class="{ active: applyPlanId === plan.id }">
                <div class="access-plan-name">{{ plan.name }}</div>
                <div class="access-plan-price">¥{{ formatCents(plan.price_cents) }}</div>
                <div class="access-plan-meta">有效期 {{ plan.duration_days || '-' }} 天</div>
                <el-radio v-model="applyPlanId" :value="plan.id" class="access-plan-radio">选择</el-radio>
              </el-card>
            </div>
            <el-form class="vip-email-form" inline v-if="accessPlans.length">
              <el-form-item label="支付渠道">
                <el-radio-group v-model="applyChannel">
                  <el-radio-button label="wechat">微信</el-radio-button>
                  <el-radio-button label="alipay">支付宝</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :disabled="!applyPlanId" :loading="applyLoading" @click="submitAccessApply">创建订单并支付</el-button>
              </el-form-item>
            </el-form>
            <el-empty v-if="!plansLoading && accessPlans.length === 0" description="暂无可用套餐，请联系管理员在 new-api 后台配置 app_access 套餐" />
          </template>
        </template>

        <!-- 支付中：展示二维码并轮询取密钥 -->
        <el-dialog
          v-model="payVisible"
          title="扫码支付接入密钥"
          width="420px"
          :close-on-click-modal="false"
          append-to-body
          @closed="stopAccessPoll"
        >
          <div class="access-pay">
            <div v-if="payQr" class="access-pay-qr"><img :src="payQr" alt="pay qr" /></div>
            <div v-else class="access-pay-hint">{{ payHint || '正在生成支付二维码…' }}</div>
            <div class="access-pay-tip">支付成功后密钥将自动下发并生效，无需手动填写。</div>
          </div>
          <template #footer>
            <el-button @click="payVisible = false">关闭</el-button>
            <el-button type="primary" :loading="credentialLoading" @click="fetchAccessCredential">我已支付，获取密钥</el-button>
          </template>
        </el-dialog>

        <template #footer>
          <el-button @click="vipDialogVisible = false">关闭</el-button>
          <el-button v-if="vipUnlocked" type="primary" @click="handleCommunityCommand('enter')">进入 I 社区</el-button>
        </template>
      </el-dialog>

      <!-- 浏览器配置抽屉 -->
      <el-drawer
        v-model="browserConfigVisible"
        title="浏览器配置"
        direction="rtl"
        size="540px"
        :before-close="handleBrowserConfigClose"
      >
        <div class="browser-config-drawer">
          <div class="bc-summary">
            <div class="bc-summary-card" :class="browserSummary.status">
              <div class="bc-summary-icon">
                <el-icon :size="22"><Monitor /></el-icon>
              </div>
              <div class="bc-summary-info">
                <div class="bc-summary-title">{{ browserSummary.title }}</div>
                <div class="bc-summary-desc">{{ browserSummary.desc }}</div>
              </div>
            </div>
            <el-button type="primary" :loading="bcDetecting" @click="handleBrowserDetect">
              <el-icon><Search /></el-icon>
              重新探测
            </el-button>
            <el-button :loading="bcDownloading" @click="handleBrowserDownload">
              <el-icon><Download /></el-icon>
              自动下载
            </el-button>
          </div>

          <el-form ref="browserFormRef" :model="browserForm" label-position="top" class="bc-form">
            <el-divider content-position="left">浏览器路径</el-divider>
            <el-form-item>
              <template #label>
                <span>可执行文件路径</span>
                <el-tooltip content="Chrome、Chromium、Edge、Brave 均可。留空则自动探测。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <div class="bc-input-row">
                <el-input v-model="browserForm.executablePath" placeholder="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" clearable />
                <el-button @click="handleBrowserValidate">校验</el-button>
              </div>
              <div class="bc-form-hint" v-if="browserForm.detected.path">
                <el-icon><Check /></el-icon>
                已探测到 {{ browserForm.detected.type }}：{{ browserForm.detected.path }}
                <el-button type="primary" link size="small" @click="useDetected">使用</el-button>
              </div>
              <div class="bc-form-hint muted" v-else>未发现浏览器，请手动指定或下载</div>
            </el-form-item>

            <el-divider content-position="left">运行模式</el-divider>
            <el-form-item>
              <template #label>
                <span>启动模式</span>
                <el-tooltip content="有界面模式兼容性更好、滑块成功率更高；无头模式适合无显示器服务器。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-radio-group v-model="browserForm.headless">
                <el-radio :value="false"><span>有界面模式</span><span class="bc-recommend">(推荐)</span></el-radio>
                <el-radio :value="true">无头模式</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="browserForm.headless">
              <template #label>
                <span>无头模式版本</span>
                <el-tooltip content="Chrome 112+ 推荐新版 headless，旧版兼容性更好。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-radio-group v-model="browserForm.headlessMode">
                <el-radio value="new">新版 headless（推荐）</el-radio>
                <el-radio value="legacy">旧版 --headless</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-divider content-position="left">高级配置</el-divider>
            <el-form-item>
              <template #label>
                <span>窗口尺寸</span>
                <el-tooltip content="模拟屏幕分辨率，影响指纹采集。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <div class="bc-size-row">
                <el-input-number v-model="browserForm.windowWidth" :min="800" :max="3840" />
                <span class="bc-size-sep">×</span>
                <el-input-number v-model="browserForm.windowHeight" :min="600" :max="2160" />
                <span class="bc-form-hint muted">推荐 1366×768</span>
              </div>
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>CDP 端口范围</span>
                <el-tooltip content="每个账号独占一个 CDP 端口，段宽需 ≥ 账号数量。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <div class="bc-range-row">
                <el-input-number v-model="browserForm.portRangeStart" :min="1024" :max="65535" />
                <span class="bc-size-sep">至</span>
                <el-input-number v-model="browserForm.portRangeEnd" :min="1024" :max="65535" />
              </div>
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>用户数据目录</span>
                <el-tooltip content="Chrome Profile 根目录，每个账号在此下创建独立子目录。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input v-model="browserForm.userDataDirRoot" placeholder="./chrome-profiles" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>多账号指纹噪声</span>
                <el-tooltip content="按账号 seed 生成唯一 canvas/WebGL 噪声，避免账号间指纹关联被封。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-switch v-model="browserForm.perAccountSeedNoise" active-text="启用" inactive-text="关闭" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>启动超时</span>
                <el-tooltip content="Chrome 容器 IPC 启动最长等待时间，超时触发崩溃恢复。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input-number v-model="browserForm.launchTimeoutSeconds" :min="5" :max="120" :step="5" />
              <span class="bc-form-hint muted">　秒</span>
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>崩溃恢复次数</span>
                <el-tooltip content="Chrome 异常退出后自动重启的次数上限。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input-number v-model="browserForm.maxCrashRecoveryAttempts" :min="0" :max="10" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>自定义启动参数</span>
                <el-tooltip content="每行一个参数，保存后将覆盖默认反检测参数。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="browserForm.customLaunchArgsText"
                type="textarea"
                :rows="4"
                placeholder="--disable-gpu&#10;--no-sandbox&#10;--mute-audio"
              />
            </el-form-item>
          </el-form>

          <div class="bc-actions">
            <el-button type="primary" :loading="bcSaving" @click="handleBrowserSave">保存配置</el-button>
            <el-button @click="handleBrowserReset">重置</el-button>
          </div>
        </div>
      </el-drawer>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  DataAnalysis, User, Goods, ChatDotRound, List, Money, Star,
  Promotion, UploadFilled, Monitor, Document, Bell, UserFilled, ArrowDown, FullScreen,
  Medal, Connection, Service, Sunrise, Switch, Timer, Setting, Compass,
  Search, Download, Check, InfoFilled, Warning, Lock, SwitchButton,
  Refresh, Avatar, ChatLineSquare, Link, Back, Edit, Wallet,
  EditPen, ShoppingBag, Present, Message, Trophy
} from '@element-plus/icons-vue'
import * as notify from '@/api/notification'
import { getChromeConfig, detectChrome, saveChromeConfig, downloadChrome, validateChromePath } from '@/api/chrome'
import { getVipHeaderStatus, getVipIdentity, sendVipEmailCode, verifyVipEmail, getAccessConfig, getAccessPlans, applyAccessPlan, getAccessCredential } from '@/api/vip'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const breadcrumbMap = {
  '/app/dashboard': ['仪表盘'],
  '/app/products': ['店铺管理', '商品管理'],
  '/app/orders': ['店铺管理', '订单管理'],
  '/app/messages': ['店铺管理', '消息管理'],
  '/app/collect': ['店铺管理', '收藏关注'],
  '/app/reviews': ['店铺管理', '评价与信用'],
  '/app/ai-ops': ['AI 智能', 'AI 运营'],
  '/app/ai': ['AI 智能', 'AI 厂商'],
  '/app/ai-cs': ['AI 智能', 'AI 客服'],
  '/app/polish': ['AI 智能', '商品擦亮'],
  '/app/virtual-ship': ['发货仓储', '虚拟发货'],
  '/app/cloud-storage': ['发货仓储', '网盘存储'],
  '/app/tasks': ['发货仓储', '监控任务'],
  '/app/rules': ['规则合规', '规则管理'],
  '/app/notify': ['规则合规', '消息通知'],
  '/app/audit': ['规则合规', '审计日志'],
  '/app/reply-logs': ['规则合规', '自动回复日志'],
  '/app/wallet': ['数据资产', '钱包资产'],
  '/app/monitor': ['数据资产', '监控面板'],
  '/app/market': ['数据资产', '市场情报'],
  '/app/buyer': ['数据资产', '买家画像'],
  '/app/accounts': ['账号管理'],
  '/app/circuit-breaker': ['数据资产', '熔断器管理'],
  '/app/profile': ['系统', '个人中心']
}

const headerIconMap = {
  '/app/dashboard': 'DataAnalysis',
  '/app/products': 'Goods',
  '/app/orders': 'List',
  '/app/messages': 'ChatDotRound',
  '/app/collect': 'Star',
  '/app/reviews': 'Medal',
  '/app/ai-ops': 'Promotion',
  '/app/ai': 'Connection',
  '/app/ai-cs': 'Service',
  '/app/polish': 'Sunrise',
  '/app/virtual-ship': 'Switch',
  '/app/cloud-storage': 'UploadFilled',
  '/app/tasks': 'Timer',
  '/app/rules': 'Setting',
  '/app/notify': 'Bell',
  '/app/audit': 'Document',
  '/app/wallet': 'Money',
  '/app/monitor': 'Monitor',
  '/app/market': 'Compass',
  '/app/buyer': 'User',
  '/app/circuit-breaker': 'Warning',
  '/app/accounts': 'UserFilled',
  '/app/profile': 'User'
}

const titleMap = {
  '/app/dashboard': '仪表盘',
  '/app/accounts': '账号管理',
  '/app/products': '商品管理',
  '/app/messages': '消息管理',
  '/app/orders': '订单管理',
  '/app/rules': '规则管理',
  '/app/wallet': '钱包资产',
  '/app/collect': '收藏关注',
  '/app/ai': 'AI 厂商',
  '/app/ai-ops': 'AI 运营',
  '/app/virtual-ship': '虚拟发货',
  '/app/cloud-storage': '网盘存储',
  '/app/monitor': '监控面板',
  '/app/audit': '审计日志',
  '/app/notify': '消息通知',
  '/app/market': '市场情报',
  '/app/buyer': '买家画像',
  '/app/ai-cs': 'AI 客服',
  '/app/tasks': '监控任务',
  '/app/reviews': '评价与信用',
  '/app/polish': '商品擦亮',
  '/app/profile': '个人中心'
}

const isCommunityWorkspace = computed(() => route.path.startsWith('/app/community'))
const currentBreadcrumb = computed(() => {
  if (isCommunityWorkspace.value) return ['I 社区', titleMap[route.path] || '社区工作区']
  return breadcrumbMap[route.path] || ['管理后台']
})
const headerIconComponent = computed(() => isCommunityWorkspace.value ? 'Connection' : (headerIconMap[route.path] || 'Document'))
const currentTitle = computed(() => isCommunityWorkspace.value ? 'I 社区' : (titleMap[route.path] || '管理后台'))

function openDataBoard() { window.open('/data-board', '_blank') }

// ===== VIP / I 社区 =====
const vipHeader = ref({ state: 'locked', label: '解锁 VIP', vipLevel: 'free' })
const vipDialogVisible = ref(false)
const vipIdentity = ref({ email: '', emailVerified: false, hasActiveVip: false })
const vipEmailForm = ref({ email: '', code: '' })
const emailSending = ref(false)
const emailVerifying = ref(false)
const emailCooldown = ref(0)
let emailCooldownTimer = null
// 接入密钥：付费后由 I 社区自动下发，不再手填 app-id/secret
const accessAppId = ref('')
const accessConfigured = ref(false)
// 接入密钥有效期（Unix 秒，0 表示无有效期限制）；用于强制到期锁定，不影响其他 tentacle 业务
const accessExpiredAt = ref(0)
const accessPlans = ref([])
const plansLoading = ref(false)
const applyPlanId = ref(null)
const applyChannel = ref('wechat')
const applyLoading = ref(false)
const payVisible = ref(false)
const payQr = ref('')
const payHint = ref('')
const payOrderNo = ref('')
const credentialLoading = ref(false)
let accessPollTimer = null
// 本次验证是否“凭邮箱恢复了已付费部署的接入密钥”（重装/换机场景）
const vipRecovered = ref(false)

// 接入密钥是否仍在有效期内（无有效期限制或尚未到期）
function accessKeyValid() {
  const e = accessExpiredAt.value
  if (!e || e <= 0) return true
  return e > Math.floor(Date.now() / 1000)
}
// 已解锁：接入密钥已落地且未过期（已付费或已恢复）或 VIP 权益有效
const vipUnlocked = computed(() => (accessConfigured.value && accessKeyValid()) || vipIdentity.value.hasActiveVip)
// 步骤指示：0 绑定邮箱 → 1 校验权益（未解锁）→ 2 解锁完成
const vipStep = computed(() => {
  if (!vipIdentity.value.emailVerified) return 0
  if (vipUnlocked.value) return 2
  return 1
})
// 未解锁时的原因：unpaid（从未付费）/ expired（曾付费但已到期）
// 接入密钥到期 或 社区 VIP 订阅到期 均归为 expired
const vipPayReason = computed(() => {
  if (vipIdentity.value.emailVerified && !vipUnlocked.value) {
    const accessExpired = accessConfigured.value && !accessKeyValid()
    return (vipIdentity.value.expiredAt || accessExpired) ? 'expired' : 'unpaid'
  }
  return ''
})
// 格式化 Unix 秒为日期文本（接入密钥有效期展示）
function formatExpiry(ts) {
  if (!ts || ts <= 0) return '长期有效'
  const d = new Date(ts * 1000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function loadVipHeader() {
  try {
    const res = await getVipHeaderStatus()
    if (res.success && res.data) vipHeader.value = res.data
  } catch (e) {}
}

async function openVipDialog() {
  vipDialogVisible.value = true
  vipRecovered.value = false
  // 邮箱优先：先确认邮箱是否已验证、是否已有活跃权益
  await loadVipIdentity()
  await loadAccessConfig()
  // 仅当尚未解锁时才需要拉套餐让用户付费
  if (!vipUnlocked.value) await loadAccessPlans()
}

function handleCommunityCommand(cmd) {
  if (cmd === 'renew') openVipDialog()
  else if (cmd === 'enter') router.push('/app/community/home')
  else router.push(`/app/community/${cmd}`)
}

// ===== 接入密钥配置（只读展示；密钥由 new-api 付费后自动下发并落地） =====
async function loadAccessConfig() {
  try {
    const res = await getAccessConfig()
    if (res.success && res.data) {
      accessAppId.value = res.data.appId || ''
      accessConfigured.value = res.data.configured === true
      accessExpiredAt.value = Number(res.data.expiredAt) || 0
    }
  } catch (e) {}
}

function formatCents(cents) {
  return (Number(cents || 0) / 100).toFixed(2)
}

// 拉取 app_access 套餐（new-api 托管）
async function loadAccessPlans() {
  plansLoading.value = true
  try {
    const res = await getAccessPlans()
    if (res.success && res.data) {
      accessPlans.value = res.data.plans || []
    } else {
      accessPlans.value = []
    }
  } catch (e) {
    accessPlans.value = []
  } finally {
    plansLoading.value = false
  }
}

// 创建接入密钥订单并发起支付
async function submitAccessApply() {
  if (!applyPlanId.value) { ElMessage.warning('请选择套餐'); return }
  applyLoading.value = true
  try {
    const res = await applyAccessPlan({ planId: applyPlanId.value, channel: applyChannel.value })
    if (res.success && res.data) {
      // 已激活（幂等）：密钥已落地，无需支付
      if (res.data.activated) {
        ElMessage.success('接入密钥已生效')
        await loadAccessConfig()
        await loadVipHeader()
        return
      }
      payOrderNo.value = res.data.order_no || ''
      const info = res.data.pay_info || {}
      const qrText = info.code_url || info.pay_url || info.h5_url || ''
      payHint.value = info.message || ''
      if (qrText) {
        try { payQr.value = await QRCode.toDataURL(qrText, { width: 240, margin: 1 }) } catch (e) { payQr.value = '' }
      } else {
        payQr.value = ''
      }
      payVisible.value = true
      startAccessPoll()
    } else {
      ElMessage.error(res.message || '创建订单失败')
    }
  } catch (e) {
    ElMessage.error('创建接入密钥订单失败：' + (e.message || e))
  } finally {
    applyLoading.value = false
  }
}

function startAccessPoll() {
  stopAccessPoll()
  accessPollTimer = setInterval(() => { fetchAccessCredential(true) }, 4000)
}
function stopAccessPoll() {
  if (accessPollTimer) { clearInterval(accessPollTimer); accessPollTimer = null }
}

// 凭订单号取密钥：后端校验已支付后落地并动态生效；未支付时静默继续轮询
async function fetchAccessCredential(silent = false) {
  if (!payOrderNo.value) return
  credentialLoading.value = true
  try {
    const res = await getAccessCredential(payOrderNo.value)
    if (res.success) {
      stopAccessPoll()
      payVisible.value = false
      payQr.value = ''
      accessConfigured.value = true
      ElMessage.success('接入密钥已获取并生效')
      await loadAccessConfig()
      await loadVipHeader()
      await loadVipIdentity()
      vipRecovered.value = false
    } else if (!silent) {
      ElMessage.warning(res.message || '尚未支付，请完成支付后重试')
    }
  } catch (e) {
    if (!silent) ElMessage.error('获取接入密钥失败：' + (e.message || e))
  } finally {
    credentialLoading.value = false
  }
}

// ===== 邮箱绑定（密钥恢复） =====
async function loadVipIdentity() {
  try {
    const res = await getVipIdentity()
    if (res.success && res.data) {
      vipIdentity.value = res.data
      if (res.data.email) vipEmailForm.value.email = res.data.email
      if (res.data.hasActiveVip) {
        await loadVipHeader()
      }
    }
  } catch (e) {}
}

function startEmailCooldown(seconds = 60) {
  emailCooldown.value = seconds
  if (emailCooldownTimer) clearInterval(emailCooldownTimer)
  emailCooldownTimer = setInterval(() => {
    emailCooldown.value -= 1
    if (emailCooldown.value <= 0) {
      clearInterval(emailCooldownTimer)
      emailCooldownTimer = null
      emailCooldown.value = 0
    }
  }, 1000)
}

async function sendVipCode() {
  const email = (vipEmailForm.value.email || '').trim()
  if (!email) { ElMessage.warning('请输入邮箱'); return }
  emailSending.value = true
  try {
    const res = await sendVipEmailCode({ email, scene: 'vip_bind' })
    if (res.success) {
      ElMessage.success('验证码已发送，请查收邮箱')
      startEmailCooldown(res.data?.cooldown || 60)
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (e) {
    ElMessage.error('发送验证码失败：' + (e.message || e))
  } finally {
    emailSending.value = false
  }
}

async function verifyVipCode() {
  const email = (vipEmailForm.value.email || '').trim()
  const code = (vipEmailForm.value.code || '').trim()
  if (!email || !code) { ElMessage.warning('请输入邮箱和验证码'); return }
  emailVerifying.value = true
  try {
    const res = await verifyVipEmail({ email, code, scene: 'vip_bind' })
    if (res.success && res.data) {
      // 验证前是否已有密钥：验证后若密钥落地，说明本次为“邮箱恢复重装/换机”
      const wasConfigured = accessConfigured.value
      vipIdentity.value = res.data
      vipEmailForm.value.email = res.data.email || email
      vipEmailForm.value.code = ''
      ElMessage.success('邮箱验证成功，可用于恢复接入密钥')
      // 重新拉取接入密钥落地状态（恢复场景会变为已配置）与头部 VIP 状态（解锁）
      await loadAccessConfig()
      await loadVipHeader()
      vipRecovered.value = !wasConfigured && accessConfigured.value
      // 仍未解锁（未付费 / 已过期）→ 拉套餐引导支付
      if (!vipUnlocked.value) await loadAccessPlans()
    } else {
      ElMessage.error(res.message || '验证失败')
    }
  } catch (e) {
    ElMessage.error('验证邮箱失败：' + (e.message || e))
  } finally {
    emailVerifying.value = false
  }
}

function handleCommand(cmd) {
  if (cmd === 'profile') router.push('/app/profile')
  else if (cmd === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' }).then(() => {
      authStore.logout(); router.push('/login')
    })
  }
}

// ===== 商务合作 =====
const wechatId = 'worker_680'
const qrSrc = '/wechat.jpg'
const qrVisible = ref(false)
function openWechatQr() { qrVisible.value = true }
function copyWechat() {
  const ok = () => ElMessage.success('微信号已复制：' + wechatId)
  const fallback = () => {
    const ta = document.createElement('textarea')
    ta.value = wechatId; ta.style.position = 'fixed'; ta.style.opacity = '0'
    document.body.appendChild(ta); ta.select()
    try { document.execCommand('copy'); ok() }
    catch (e) { ElMessage.error('复制失败，请手动复制：' + wechatId) }
    document.body.removeChild(ta)
  }
  if (navigator.clipboard && navigator.clipboard.writeText)
    navigator.clipboard.writeText(wechatId).then(ok).catch(fallback)
  else fallback()
}

// ===== 站内通知 =====
const inboxVisible = ref(false)
const messages = ref([])
const unread = ref(0)
const scenarioMap = ref({})

function scenarioLabel(s) { return scenarioMap.value[s] || s }
async function loadScenarios() {
  try {
    const res = await notify.listScenarios()
    if (res.success) {
      scenarioMap.value = {}
      ;(res.data || []).forEach(it => { scenarioMap.value[it.scenario] = it.label })
    }
  } catch (e) {}
}
async function loadUnread() {
  try {
    const res = await notify.unreadCount()
    if (res.success) unread.value = res.data?.unread || 0
  } catch (e) {}
}
async function loadMessages() {
  try {
    const res = await notify.listMessages({ page: 1, size: 30 })
    if (res.success) messages.value = res.data?.records || []
  } catch (e) {}
}
async function openInbox() { inboxVisible.value = true; await loadMessages() }
async function readMsg(m) {
  if (!m.isRead) { try { await notify.markRead(m.id); m.isRead = true; await loadUnread() } catch (e) {} }
}
async function readAll() {
  try {
    const res = await notify.markAllRead()
    if (res.success) { messages.value.forEach(m => { m.isRead = true }); unread.value = 0; ElMessage.success('已全部标记为已读') }
  } catch (e) {}
}

let pollTimer = null
onMounted(() => { loadScenarios(); loadUnread(); loadVipHeader(); pollTimer = setInterval(() => { loadUnread(); loadVipHeader() }, 30000) })
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (emailCooldownTimer) clearInterval(emailCooldownTimer)
  if (accessPollTimer) clearInterval(accessPollTimer)
})

// ===== 浏览器配置 =====
const browserConfigVisible = ref(false)
const bcDetecting = ref(false)
const bcDownloading = ref(false)
const bcSaving = ref(false)
const browserFormRef = ref(null)
const browserForm = ref({
  executablePath: '', headless: false, headlessMode: 'new',
  portRangeStart: 9222, portRangeEnd: 9322, userDataDirRoot: './chrome-profiles',
  windowWidth: 1366, windowHeight: 768, perAccountSeedNoise: true,
  launchTimeoutSeconds: 30, maxCrashRecoveryAttempts: 3,
  customLaunchArgsText: '', detected: { found: false, path: '', type: '' }
})

const browserSummary = computed(() => {
  const f = browserForm.value
  if (!f.executablePath && !f.detected.path)
    return { status: 'warning', title: '未配置浏览器', desc: '请指定路径或先探测系统浏览器' }
  if (f.detected.found)
    return { status: 'success', title: '浏览器可用', desc: `${f.detected.type} · ${f.detected.path}` }
  return { status: 'success', title: '浏览器路径已设置', desc: f.executablePath }
})

function openBrowserConfig() {
  browserConfigVisible.value = true
  if (!browserForm.value.executablePath && !browserForm.value.detected.path) loadBrowserConfig()
}
function handleBrowserConfigClose(done) { done() }

async function loadBrowserConfig() {
  try {
    const res = await getChromeConfig()
    if (res.success && res.data) {
      const d = res.data
      browserForm.value = {
        executablePath: d.executablePath || '', headless: d.headless ?? false,
        headlessMode: d.headlessMode || 'new', portRangeStart: d.portRangeStart || 9222,
        portRangeEnd: d.portRangeEnd || 9322, userDataDirRoot: d.userDataDirRoot || './chrome-profiles',
        windowWidth: d.windowWidth || 1366, windowHeight: d.windowHeight || 768,
        perAccountSeedNoise: d.perAccountSeedNoise ?? true,
        launchTimeoutSeconds: d.launchTimeoutSeconds || 30,
        maxCrashRecoveryAttempts: d.maxCrashRecoveryAttempts || 3,
        customLaunchArgsText: (d.customLaunchArgs || []).join('\n'),
        detected: { found: d.detected?.found || false, path: d.detected?.path || '', type: d.detected?.type || '' }
      }
    }
  } catch (e) { ElMessage.error('加载浏览器配置失败：' + (e.message || e)) }
}

async function handleBrowserDetect() {
  bcDetecting.value = true
  try {
    const res = await detectChrome()
    if (res.success && res.data) {
      browserForm.value.detected = { found: res.data.found, path: res.data.path || '', type: res.data.type || '' }
      if (res.data.found) ElMessage.success(`发现浏览器：${res.data.type}`)
      else ElMessage.warning('未发现浏览器，请手动指定或下载')
    }
  } catch (e) { ElMessage.error('探测失败：' + (e.message || e)) }
  finally { bcDetecting.value = false }
}

function useDetected() {
  if (browserForm.value.detected.path) browserForm.value.executablePath = browserForm.value.detected.path
}

async function handleBrowserValidate() {
  if (!browserForm.value.executablePath) { ElMessage.warning('请先输入路径'); return }
  try {
    const res = await validateChromePath(browserForm.value.executablePath)
    if (res.success && res.data) {
      if (res.data.valid) ElMessage.success('路径校验通过，可执行')
      else ElMessage.warning('路径不可用：' + (res.data.reason || '不存在或无执行权限'))
    }
  } catch (e) { ElMessage.error('校验失败：' + (e.message || e)) }
}

async function handleBrowserDownload() {
  bcDownloading.value = true
  try {
    const res = await downloadChrome()
    if (res.success && res.data) ElMessage.success(res.data.message || '下载完成')
    else ElMessage.warning(res.message || res.data?.message || '下载失败，请使用系统包管理器安装')
  } catch (e) { ElMessage.error('下载失败：' + (e.message || e)) }
  finally { bcDownloading.value = false }
}

async function handleBrowserSave() {
  bcSaving.value = true
  try {
    const args = browserForm.value.customLaunchArgsText.split('\n').map(s => s.trim()).filter(Boolean)
    const payload = { ...browserForm.value, customLaunchArgs: args.length ? args : null }
    const res = await saveChromeConfig(payload)
    if (res.success) ElMessage.success('浏览器配置已保存')
    else ElMessage.error(res.message || '保存失败')
  } catch (e) { ElMessage.error('保存失败：' + (e.message || e)) }
  finally { bcSaving.value = false }
}

async function handleBrowserReset() {
  if (!browserConfigVisible.value) return
  browserConfigVisible.value = false
  await loadBrowserConfig()
  browserConfigVisible.value = true
  ElMessage.info('已重置为当前配置')
}
</script>

<style scoped>
/* ========== 全局布局 ========== */
.layout { height: 100vh; }
.sidebar {
  background: var(--color-white, #fff);
  overflow-y: auto;
  border-right: 1px solid var(--border);
  flex-shrink: 0;
}

/* ========== Logo ========== */
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid var(--bg-soft);
  gap: 10px;
  flex-shrink: 0;
}
.logo-icon {
  width: 34px;
  height: 34px;
  background: var(--brand-gradient);
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);
}
.logo-text { display: flex; flex-direction: column; line-height: 1.25; min-width: 0; }
.logo-title { font-size: 15px; font-weight: 700; color: var(--text-1); letter-spacing: 0.3px; }
.logo-tag { font-size: 10px; color: var(--text-3); letter-spacing: 0.5px; font-weight: 500; }
.logo-subtitle { font-size: 11px; color: var(--text-3); margin-top: 2px; }

/* ========== Header ========== */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
  padding: 0 24px;
  z-index: 10;
}
.header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.page-icon-box {
  width: 30px; height: 30px;
  background: var(--el-color-primary-light-9);
  border-radius: 7px;
  display: flex; align-items: center; justify-content: center;
  color: var(--brand); font-size: 15px; flex-shrink: 0;
}
.page-title-area { display: flex; flex-direction: column; line-height: 1.3; }
.page-title { font-size: 16px; font-weight: 600; color: var(--text-1); }
.page-breadcrumb { font-size: 11px; color: var(--text-3); display: flex; align-items: center; gap: 4px; }
.crumb { color: var(--text-3); }
.crumb-sep { color: var(--text-3); margin: 0 2px; }

.header-right { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.header-right .el-button { color: var(--text-2); }
.header-right .el-button:hover { color: var(--brand); }
.bell-badge { margin-right: 2px; }
.bell-badge :deep(.el-button) { color: var(--text-2); }
.vip-entry {
  border: 0;
  color: #fff !important;
  font-weight: 700;
  background: linear-gradient(135deg, #f97316, #ef4444);
  box-shadow: 0 8px 18px rgba(239, 68, 68, 0.22);
}
.vip-entry:hover { color: #fff !important; transform: translateY(-1px); }
.vip-entry.active {
  background: linear-gradient(135deg, #4f46e5, #06b6d4);
  box-shadow: 0 8px 18px rgba(79, 70, 229, 0.22);
}
.vip-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 18px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #fff7ed, #eef2ff);
  margin-bottom: 14px;
}
.vip-hero h3 { margin: 0 0 8px; font-size: 20px; color: var(--text-1); }
.vip-hero p { margin: 0; color: var(--text-3); line-height: 1.6; }
.vip-steps { margin-bottom: 18px; }
.vip-stage-guide { margin-bottom: 14px; line-height: 1.7; }
.vip-stage-guide :deep(.el-alert__content) { line-height: 1.7; }
.vip-benefits { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 12px 0; }
.vip-benefit { padding: 10px 12px; border-radius: var(--radius-md); background: var(--bg-soft); color: var(--text-2); font-size: 13px; }
.vip-plans { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.vip-plan { border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 14px; cursor: pointer; transition: all .2s; background: #fff; }
.vip-plan:hover, .vip-plan.selected { border-color: var(--brand); box-shadow: var(--shadow-hover); transform: translateY(-1px); }
.vip-plan-title { font-weight: 700; color: var(--text-1); }
.vip-plan-price { font-size: 22px; font-weight: 800; color: var(--color-danger); margin: 8px 0 4px; }
.vip-plan-tag { font-size: 12px; color: var(--text-3); }
.vip-channels { margin-bottom: 14px; }
.pay-info-box { border: 1px solid var(--border); background: #0f172a; color: #e5e7eb; border-radius: var(--radius-lg); padding: 12px; }
.pay-info-title { font-weight: 700; margin-bottom: 8px; color: #fff; }
.pay-info-box pre { white-space: pre-wrap; word-break: break-word; font-size: 12px; max-height: 180px; overflow: auto; }

/* VIP 弹窗窄屏适配：套餐网格与权益列表降列 */
@media (max-width: 640px) {
  .vip-plans { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .vip-benefits { grid-template-columns: 1fr; }
  .vip-hero { flex-direction: column; }
  .vip-email-form { display: flex; flex-direction: column; align-items: stretch; gap: 8px; }
  .vip-email-form .el-form-item { margin-bottom: 0; }
}

/* 接入密钥套餐 + 支付 */
.access-plans { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.access-plan {
  position: relative; text-align: center; cursor: pointer;
  border: 1px solid var(--border-color, #e5e7eb); transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.access-plan.active { border-color: var(--brand, #4f46e5); box-shadow: 0 0 0 2px rgba(79,70,229,0.15); }
.access-plan-name { font-weight: 600; color: var(--text-1); }
.access-plan-price { font-size: 20px; font-weight: 700; color: #ef4444; margin: 6px 0 2px; }
.access-plan-meta { color: var(--text-3); font-size: 12px; }
.access-plan-radio { margin-top: 8px; }
.access-pay { text-align: center; }
.access-pay-qr { display: flex; justify-content: center; }
.access-pay-qr img { width: 240px; height: 240px; }
.access-pay-hint { color: var(--text-2); padding: 24px 0; }
.access-pay-tip { color: var(--text-3); font-size: 12px; margin-top: 12px; }

/* 用户头像下拉 */
.user-info {
  display: flex; align-items: center; gap: 7px;
  cursor: pointer; color: var(--text-2); font-size: 13px;
  padding: 4px 8px; border-radius: 8px;
  transition: background 0.15s ease;
}
.user-info:hover { background: var(--bg-soft); }
.avatar {
  width: 30px; height: 30px;
  background: var(--bg-soft);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: var(--text-2); font-size: 12px; font-weight: 600;
}
.user-name { max-width: 80px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.avatar-arrow { font-size: 12px; }

/* ========== 主内容区 ========== */
.main-content {
  background: var(--bg-soft);
  padding: 16px !important;
  overflow: auto;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  min-height: 0;
}

/* ========== 页面切换过渡 ========== */
.fade-slide-enter-active, .fade-slide-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(12px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-8px); }
.fade-slide-enter-to, .fade-slide-leave-from { opacity: 1; transform: translateY(0); }

/* ========== 侧边栏菜单 ========== */
.sidebar-menu { border-right: none !important; padding: 8px 0 20px; }
.sidebar-menu :deep(.el-menu-item) {
  margin: 2px 10px;
  border-radius: 8px;
  height: 42px; line-height: 42px;
  color: var(--text-2);
  font-size: 13px;
  padding: 0 12px !important;
  transition: all 0.2s ease;
}
.sidebar-menu :deep(.el-menu-item:hover) { background: var(--bg-soft) !important; color: var(--text-1); }
.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--brand-gradient) !important;
  color: #fff !important;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);
}
.sidebar-menu :deep(.el-menu-item-group__title) {
  padding: 16px 20px 6px 14px;
  font-size: 10.5px;
  color: var(--text-3);
  font-weight: 600;
  letter-spacing: 0.8px;
  text-transform: uppercase;
}
.menu-icon-box {
  display: inline-flex;
  align-items: center; justify-content: center;
  width: 22px; height: 22px;
  margin-right: 8px;
  border-radius: 5px;
  background: transparent;
  color: var(--text-2);
  flex-shrink: 0;
  transition: background 0.2s ease, color 0.2s ease;
}
.sidebar-menu :deep(.el-menu-item:hover) .menu-icon-box { background: rgba(0,0,0,0.04); color: var(--text-1); }
.sidebar-menu :deep(.el-menu-item.is-active) .menu-icon-box {
  background: rgba(255,255,255,0.2); color: #fff;
}

/* ========== 页脚 ========== */
.app-footer {
  display: flex; align-items: center; justify-content: center;
  flex-wrap: wrap; gap: 6px 18px;
  padding: 10px 16px;
  background: var(--bg-soft);
  border-top: 1px solid var(--border);
  color: var(--text-3); font-size: 12.5px;
}
.coop-label { color: var(--text-2); font-weight: 600; letter-spacing: 1px; }
.coop-item {
  display: inline-flex; align-items: center; gap: 5px;
  color: var(--text-2); font-size: 12.5px; text-decoration: none;
  background: none; border: none; cursor: pointer;
  padding: 3px 9px; border-radius: 8px;
  transition: color 0.2s, background 0.2s;
}
.coop-item:hover { color: var(--brand); background: rgba(79, 70, 229, 0.08); }
.coop-ico { width: 14px; height: 14px; }
.coop-ico.wechat { color: #07c160; }
.footer-divider { color: var(--text-3); }
.copyright { color: var(--text-3); }

/* ========== 通知抽屉 ========== */
.inbox-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px; color: var(--text-2); font-size: 13px;
}
.inbox-item {
  padding: 12px; border-radius: 8px;
  border: 1px solid var(--border); margin-bottom: 10px;
  cursor: pointer; transition: background 0.2s, border-color 0.2s;
}
.inbox-item:hover { background: var(--bg-soft); }
.inbox-item.unread { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.inbox-title { font-weight: 600; font-size: 14px; color: var(--text-1); display: flex; align-items: center; gap: 6px; }
.inbox-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--brand); flex: 0 0 auto; }
.inbox-content { margin-top: 6px; color: var(--text-2); font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; }
.inbox-meta { margin-top: 6px; color: var(--text-3); font-size: 12px; }

/* ========== 浏览器配置抽屉 ========== */
.browser-config-drawer { display: flex; flex-direction: column; gap: 16px; padding-bottom: 16px; }
.bc-summary { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.bc-summary-card {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-radius: 8px;
  background: var(--bg-soft); border: 1px solid var(--border);
  flex: 1; min-width: 200px;
}
.bc-summary-card.success { background: rgba(16, 185, 129, 0.08); border-color: rgba(16, 185, 129, 0.25); color: var(--color-success); }
.bc-summary-card.warning { background: rgba(245, 158, 11, 0.08); border-color: rgba(245, 158, 11, 0.25); color: var(--color-warning); }
.bc-summary-icon { display: flex; align-items: center; justify-content: center; }
.bc-summary-info { flex: 1; }
.bc-summary-title { font-size: 14px; font-weight: 600; color: var(--text-1); }
.bc-summary-desc { font-size: 12px; color: var(--text-3); margin-top: 2px; word-break: break-all; }
.bc-form { background: var(--bg-soft); border-radius: 8px; padding: 16px 16px 4px; }
.bc-form :deep(.el-form-item__label) { display: flex; align-items: center; gap: 6px; font-weight: 500; }
.bc-tip-icon { color: var(--text-3); cursor: help; font-size: 14px; }
.bc-form-hint { margin-top: 6px; font-size: 12px; color: var(--color-success); display: flex; align-items: center; gap: 4px; }
.bc-form-hint.muted { color: var(--text-3); }
.bc-input-row { display: flex; gap: 8px; }
.bc-input-row :deep(.el-input) { flex: 1; }
.bc-recommend { font-size: 12px; color: var(--color-success); margin-left: 4px; }
.bc-size-row, .bc-range-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.bc-size-sep { color: var(--text-3); }
.bc-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 16px; border-top: 1px solid var(--border); }
</style>
