package cn.net.rjnetwork.xianyu.manager.account.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProfileApiService;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeProfileManager;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountEditRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginResponse;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.vip.service.VipService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final AccountMapper accountMapper;
    private final VipService vipService;
    private final Map<String, XianyuLoginApiService> qrLoginServices = new ConcurrentHashMap<>();
    /** 缓存每个二维码会话对应的创建请求（含 accountName / remark）*/
    private final Map<String, QrLoginRequest> qrLoginRequests = new ConcurrentHashMap<>();

    /** Chrome 容器管理器（可选，非 Chrome 环境为 null） */
    private ChromeProfileManager chromeProfileManager;

    /** 最近一次 Chrome 容器启动失败原因（accountId → error message） */
    private final Map<Long, String> chromeLaunchErrors = new ConcurrentHashMap<>();

    @Autowired
    private cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 与账号强关联、删除账号时必须级联清理的数据表（均含 account_id 列）。
     * 新功能若新增按 accountId 存储的实体，请同步补充到此处。
     */
    private static final List<String> ACCOUNT_RELATED_TABLES = List.of(
            "xianyu_product", "local_product", "xianyu_order", "xianyu_message",
            "xianyu_collect", "xianyu_wallet", "xianyu_wallet_transaction",
            "xianyu_auto_reply_config", "xianyu_auto_reply_log", "xianyu_keyword_rule",
            "item_reply", "comment_templates", "delivery_rules", "delivery_block_rule",
            "delivery_log", "virtual_ship_config", "virtual_ship_task",
            "ai_cs_knowledge", "ai_cs_policy", "ai_cs_session",
            "ai_ops_task", "ai_ops_suggestion",
            "monitor_task", "market_snapshot",
            "notify_message", "cookie_refresh_schedule", "login_renew_schedule",
            "im_token_cache", "risk_control_log", "cloud_storage_account",
            "auto_rate_config", "red_flower_config",
            "scheduled_close_notice_log", "scheduled_polish_log");

    public AccountService(AccountMapper accountMapper, VipService vipService) {
        this.accountMapper = accountMapper;
        this.vipService = vipService;
    }

    /** Spring 自动注入 ChromeProfileManager（如果容器中存在）。 */
    @Autowired(required = false)
    public void setChromeProfileManager(ChromeProfileManager chromeProfileManager) {
        this.chromeProfileManager = chromeProfileManager;
    }

    @Transactional
    public XianyuAccount login(AccountLoginRequest request) {
        String cookie = request.getCookieHeader();
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("Cookie is required");
        }

        vipService.assertAccountCreateAllowed(accountMapper.selectCount(new LambdaQueryWrapper<XianyuAccount>()));

        XianyuAccount account = new XianyuAccount();
        account.setAccountName(request.getAccountName());
        account.setCookieHeader(cookie);
        account.setStatus("ACTIVE");
        account.setRemark(request.getRemark());
        account.setLastLoginAt(LocalDateTime.now());
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        accountMapper.insert(account);
        return account;
    }

    /**
     * 创建二维码登录会话
     */
    public QrLoginResponse createQrLoginSession(QrLoginRequest request) {
        String accountName = request.getAccountName();
        if (accountName == null || accountName.isBlank()) {
            QrLoginResponse resp = new QrLoginResponse();
            resp.setSuccess(false);
            resp.setMessage("Account name is required");
            return resp;
        }

        // 创建临时的 SDK 登录服务（使用任意 cookie 即可，仅用于初始化 httpClient）
        XianyuLoginApiService loginService = new XianyuLoginApiService("");
        XianyuLoginApiService.QrLoginResult sdkResult = loginService.createQrLoginSession();

        QrLoginResponse resp = new QrLoginResponse();
        resp.setSuccess(sdkResult.success);
        resp.setSessionId(sdkResult.sessionId);
        resp.setStatus(sdkResult.status);
        resp.setQrCodeDataUrl(sdkResult.qrCodeDataUrl);
        resp.setMessage(sdkResult.message);

        // 修复：只要有 sessionId 和 qrCodeDataUrl 就说明创建成功，不管 status 是 WAITING 还是其他
        if (sdkResult.sessionId != null && sdkResult.qrCodeDataUrl != null) {
            qrLoginServices.put(sdkResult.sessionId, loginService);
            qrLoginRequests.put(sdkResult.sessionId, request);
            System.err.println("[ACCOUNT-SERVICE] Session stored in map: " + sdkResult.sessionId);
        } else {
            System.err.println("[ACCOUNT-SERVICE] Failed to store session: sessionId=" + sdkResult.sessionId + ", qrCodeDataUrl=" + sdkResult.qrCodeDataUrl);
        }

        return resp;
    }

    /**
     * 轮询二维码登录状态
     */
    public QrLoginResponse pollQrLoginStatus(String sessionId) {
        System.err.println("[ACCOUNT-SERVICE] pollQrLoginStatus called, sessionId=" + sessionId);
        System.err.println("[ACCOUNT-SERVICE] Current sessions in map: " + qrLoginServices.keySet());
        System.err.println("[ACCOUNT-SERVICE] Map size: " + qrLoginServices.size());

        XianyuLoginApiService loginService = qrLoginServices.get(sessionId);
        if (loginService == null) {
            System.err.println("[ACCOUNT-SERVICE] Session NOT FOUND for sessionId: " + sessionId);
            QrLoginResponse resp = new QrLoginResponse();
            resp.setSuccess(false);
            resp.setStatus("NOT_FOUND");
            resp.setMessage("QR login session not found");
            return resp;
        }

        System.err.println("[ACCOUNT-SERVICE] Session FOUND, calling SDK pollQrStatus...");
        XianyuLoginApiService.QrLoginResult sdkResult = loginService.pollQrStatus(sessionId);

        QrLoginResponse resp = new QrLoginResponse();
        resp.setSuccess(sdkResult.success);
        resp.setSessionId(sdkResult.sessionId);
        resp.setStatus(sdkResult.status);
        resp.setQrCodeDataUrl(sdkResult.qrCodeDataUrl);
        resp.setMessage(sdkResult.message);

        // 登录成功：保存 Cookie 到数据库
        if ("SUCCESS".equals(sdkResult.status) && sdkResult.cookieHeader != null) {
            QrLoginRequest createReq = qrLoginRequests.get(sessionId);
            XianyuAccount account;
            if (createReq != null && createReq.getAccountId() != null) {
                // 重新登录场景：更新现有账号的 Cookie
                account = updateAccountCookie(createReq.getAccountId(), sdkResult.cookieHeader, sdkResult.unb);
            } else {
                // 新建账号场景
                account = saveAccountFromCookie(sdkResult.cookieHeader, sdkResult.unb, createReq);
            }
            if (account != null) {
                resp.setAccount(convertToAccountInfo(account));
            }
            // 清理会话
            qrLoginServices.remove(sessionId);
            qrLoginRequests.remove(sessionId);
        } else if ("EXPIRED".equals(sdkResult.status) || "CANCELLED".equals(sdkResult.status)
                || "ERROR".equals(sdkResult.status)) {
            qrLoginServices.remove(sessionId);
            qrLoginRequests.remove(sessionId);
        }

        return resp;
    }

    /**
     * 重新登录场景：用新 Cookie 更新现有账号。
     * 重置登录时间、清错误、状态置回 ACTIVE，并尝试刷新 profile。
     */
    private XianyuAccount updateAccountCookie(Long accountId, String cookieHeader, String unb) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            logger.warn("updateAccountCookie: account not found, id={}", accountId);
            return null;
        }

        account.setCookieHeader(cookieHeader);
        account.setStatus("ACTIVE");
        account.setLastError(null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        // 尝试拉取最新 profile（失败不阻断登录）
        try {
            XianyuLoginApiService tempService = new XianyuLoginApiService(cookieHeader);
            XianyuLoginApiService.LoginStatusResult statusResult = tempService.checkLoginStatus(cookieHeader);
            if (statusResult != null) {
                if (account.getDisplayName() == null && statusResult.nickname != null) {
                    account.setDisplayName(statusResult.nickname);
                }
                if (statusResult.userId != null) {
                    account.setUserId(statusResult.userId);
                }
            }
        } catch (Exception e) {
            logger.warn("updateAccountCookie: refresh profile failed for accountId={}, err={}",
                    accountId, e.getMessage());
        }

        accountMapper.updateById(account);
        logger.info("Account cookie updated via QR re-login, accountId={}, accountName={}",
                accountId, account.getAccountName());

        // 重启 Chrome 容器以加载新 Cookie
        launchChromeContainer(account);

        return account;
    }

    /**
     * 根据 Cookie 保存账号
     */
    private XianyuAccount saveAccountFromCookie(String cookieHeader, String unb, QrLoginRequest createReq) {
        // 通过 SDK 获取用户信息
        XianyuLoginApiService tempService = new XianyuLoginApiService(cookieHeader);
        XianyuLoginApiService.LoginStatusResult statusResult = tempService.checkLoginStatus(cookieHeader);

        XianyuAccount account = new XianyuAccount();
        // account_name 在数据库中是 NOT NULL，必须赋值
        String accountName = createReq != null && createReq.getAccountName() != null && !createReq.getAccountName().isBlank()
                ? createReq.getAccountName()
                : (statusResult.nickname != null && !statusResult.nickname.isBlank()
                    ? statusResult.nickname
                    : (unb != null ? "unb_" + unb : "xianyu_" + System.currentTimeMillis()));
        account.setAccountName(accountName);
        account.setUserId(statusResult.userId);
        account.setDisplayName(statusResult.nickname);
        account.setCookieHeader(cookieHeader);
        account.setStatus("ACTIVE");
        account.setRemark(createReq != null ? createReq.getRemark() : null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        // 获取更详细的个人信息
        try {
            XianyuMtopApiClient mtopClient = xianyuMtopClientFactory.create(cookieHeader, account.getId());
            XianyuProfileApiService profileApi = new XianyuProfileApiService(mtopClient);

            JsonNode navData = profileApi.getUserPageNav();
            if (navData != null && navData.has("data")) {
                JsonNode data = navData.get("data");
                if (data.has("module")) {
                    JsonNode module = data.get("module");
                    if (module.has("base")) {
                        JsonNode base = module.get("base");
                        if (account.getDisplayName() == null) {
                            account.setDisplayName(getText(base, "displayName"));
                        }
                        account.setAvatar(getText(base, "avatar"));
                        account.setPurchaseCount(getInt(base, "purchaseCount"));
                        account.setSoldCount(getInt(base, "soldCount"));
                        account.setFollowers(getInt(base, "followers"));
                        account.setFollowing(getInt(base, "following"));
                        account.setCollectionCount(getInt(base, "collectionCount"));
                    }
                }
            }

            JsonNode headData = profileApi.getUserPageHead(true);
            if (headData != null && headData.has("data")) {
                JsonNode data = headData.get("data");
                if (data.has("module")) {
                    JsonNode module = data.get("module");
                    if (module.has("base")) {
                        JsonNode base = module.get("base");
                        account.setIntroduction(getText(base, "introduction"));
                        account.setIpLocation(getText(base, "ipLocation"));
                    }
                    if (module.has("shop")) {
                        JsonNode shop = module.get("shop");
                        account.setShopLevel(getText(shop, "level"));
                        account.setCreditScore(getInt(shop, "score"));
                        account.setReviewNum(getInt(shop, "reviewNum"));
                    }
                    if (module.has("tabs")) {
                        JsonNode tabs = module.get("tabs");
                        if (tabs.has("item")) {
                            account.setOnSaleCount(getInt(tabs.get("item"), "number"));
                        }
                    }
                    if (module.has("social")) {
                        JsonNode social = module.get("social");
                        if (account.getFollowers() == null) {
                            account.setFollowers(getInt(social, "followers"));
                        }
                        if (account.getFollowing() == null) {
                            account.setFollowing(getInt(social, "following"));
                        }
                    }
                }
            }

            account.setProfileSyncedAt(LocalDateTime.now());
        } catch (Exception e) {
            // 获取 profile 失败不影响登录，仅记录错误
            System.err.println("[ACCOUNT-SERVICE] Failed to fetch profile after login: " + e.getMessage());
        }

        vipService.assertAccountCreateAllowed(accountMapper.selectCount(new LambdaQueryWrapper<XianyuAccount>()));
        accountMapper.insert(account);

        // Chrome 容器：为账号启动独占 Chrome 容器
        launchChromeContainer(account);

        return account;
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asText();
    }

    private Integer getInt(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asInt();
    }

    private QrLoginResponse.AccountInfo convertToAccountInfo(XianyuAccount account) {
        QrLoginResponse.AccountInfo info = new QrLoginResponse.AccountInfo();
        info.setId(account.getId());
        info.setAccountName(account.getAccountName());
        info.setUserId(account.getUserId());
        info.setDisplayName(account.getDisplayName());
        info.setStatus(account.getStatus());
        return info;
    }

    /**
     * 清理过期的二维码会话（定时任务调用）
     */
    public void cleanupExpiredQrSessions() {
        Iterator<Map.Entry<String, XianyuLoginApiService>> it = qrLoginServices.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, XianyuLoginApiService> entry = it.next();
            try {
                entry.getValue().cleanupExpiredSessions();
            } catch (Exception ignore) {
                it.remove();
            }
        }
    }

    @Transactional
    public XianyuAccount updateStatus(Long id, AccountStatusUpdateRequest request) {
        XianyuAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        account.setStatus(request.getStatus());
        account.setRemark(request.getRemark());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);

        return account;
    }

    /**
     * 编辑账号（更换 Cookie、备注、账号名称等）。
     * 更换 Cookie 后重置登录时间与状态，便于触发后续刷新流程。
     */
    @Transactional
    public XianyuAccount updateAccount(Long id, AccountEditRequest request) {
        XianyuAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        if (request.getAccountName() != null && !request.getAccountName().isBlank()) {
            account.setAccountName(request.getAccountName());
        }
        if (request.getRemark() != null) {
            account.setRemark(request.getRemark());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            account.setStatus(request.getStatus());
        }
        // 更换 Cookie：清空旧登录态，重置 lastLoginAt，状态置回 ACTIVE
        if (request.getCookieHeader() != null && !request.getCookieHeader().isBlank()) {
            account.setCookieHeader(request.getCookieHeader());
            account.setLastLoginAt(LocalDateTime.now());
            if (account.getStatus() == null || "COOKIE_EXPIRED".equals(account.getStatus())) {
                account.setStatus("ACTIVE");
            }
        }

        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
        return account;
    }

    public List<XianyuAccount> listAll() {
        return accountMapper.selectList(null);
    }

    public Optional<XianyuAccount> findByName(String accountName) {
        LambdaQueryWrapper<XianyuAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuAccount::getAccountName, accountName);
        XianyuAccount account = accountMapper.selectOne(wrapper);
        return Optional.ofNullable(account);
    }

    public XianyuAccount getById(Long id) {
        return accountMapper.selectById(id);
    }

    @Transactional
    public void removeById(Long id) {
        XianyuAccount account = accountMapper.selectById(id);
        if (account == null) {
            return;
        }
        // 关闭 Chrome 容器（如果存在）
        stopChromeContainer(id);
        // 删除该账号的 Chrome profile 目录（如 ./chrome-profiles/account-{id}）
        deleteChromeProfileDir(account);
        // 级联删除所有与账号关联的数据表
        for (String table : ACCOUNT_RELATED_TABLES) {
            try {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE account_id = ?", id);
            } catch (Exception e) {
                // 个别表缺 account_id 列时跳过，不影响其他表与账号本身的删除
                logger.warn("[ACCOUNT-DELETE] 清理表 {} 失败(可能无 account_id 列), accountId={}: {}",
                        table, id, e.getMessage());
            }
        }
        // 最后删除账号本身
        accountMapper.deleteById(id);
        logger.info("[ACCOUNT-DELETE] 账号 {} 及其关联数据已删除", id);
    }

    /**
     * 删除账号对应的 Chrome profile 目录（幂等：目录不存在时静默跳过）。
     * 文件系统操作不可回滚，失败仅记录日志，不影响数据库删除。
     */
    private void deleteChromeProfileDir(XianyuAccount account) {
        String profileDir = account.getChromeProfilePath();
        if (profileDir == null || profileDir.isBlank()) {
            profileDir = "./chrome-profiles/account-" + account.getId();
        }
        Path path = Paths.get(profileDir);
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    logger.warn("[ACCOUNT-DELETE] 删除文件失败: {} ({})", p, e.getMessage());
                }
            });
            logger.info("[ACCOUNT-DELETE] 已删除账号 {} 的 Chrome profile 目录: {}", account.getId(), profileDir);
        } catch (IOException e) {
            logger.warn("[ACCOUNT-DELETE] 删除 Chrome profile 目录失败, accountId={}: {}", account.getId(), e.getMessage());
        }
    }

    // ==================== Chrome 容器生命周期集成 ====================

    /**
     * 为账号启动独立的 Chrome 容器。
     * ChromeProfileManager 不可用时（如非 Chrome 环境）静默跳过。
     */
    public boolean launchChromeContainer(XianyuAccount account) {
        if (chromeProfileManager == null) {
            chromeLaunchErrors.put(account.getId(), "Chrome 容器管理器不可用");
            return false;
        }
        try {
            ChromeProfile profile = chromeProfileManager.launchAccount(account.getId(), account.getAccountName());

            // 将容器信息回填到数据库
            account.setChromeProfilePath(profile.getProfileDir());
            account.setCdpPort(profile.getCdpPort());
            account.setProxyUrl(profile.getProxyUrl());
            account.setChromeSeed(profile.getSeed());
            account.setChromeStatus(profile.getStatus().name());
            account.setChromeCrashCount(0);
            account.setChromeLaunchedAt(profile.getLaunchedAt());
            account.setUpdatedAt(LocalDateTime.now());
            accountMapper.updateById(account);
            chromeLaunchErrors.remove(account.getId());
            return true;
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            chromeLaunchErrors.put(account.getId(), message);
            logger.warn("[ACCOUNT-SERVICE] 启动 Chrome 容器失败, accountId={}, err={}", account.getId(), message, e);
            // 不阻断登录，仅记录错误
            return false;
        }
    }

    /**
     * 获取指定账号最近一次 Chrome 容器启动失败原因。
     */
    public Optional<String> getLastChromeLaunchError(long accountId) {
        return Optional.ofNullable(chromeLaunchErrors.get(accountId));
    }

    /**
     * 关闭账号的 Chrome 容器。
     */
    public boolean stopChromeContainer(long accountId) {
        if (chromeProfileManager == null) {
            return false;
        }
        try {
            chromeProfileManager.stopAccount(accountId);
            return true;
        } catch (Exception e) {
            System.err.println("[ACCOUNT-SERVICE] 关闭 Chrome 容器失败, accountId=" + accountId + ", err=" + e.getMessage());
            return false;
        }
    }

    /**
     * 获取账号的 Chrome 容器状态。
     */
    public Optional<ChromeProfile> getChromeProfile(long accountId) {
        if (chromeProfileManager == null) {
            return Optional.empty();
        }
        return chromeProfileManager.getProfile(accountId);
    }

    /**
     * 判断账号是否有存活的 Chrome 容器。
     */
    public boolean isChromeAlive(long accountId) {
        if (chromeProfileManager == null) {
            return false;
        }
        return chromeProfileManager.isAlive(accountId);
    }

    /**
     * 获取 ChromeProfileManager 实例（用于外部直接调用）。
     */
    public ChromeProfileManager getChromeProfileManager() {
        return chromeProfileManager;
    }
}
