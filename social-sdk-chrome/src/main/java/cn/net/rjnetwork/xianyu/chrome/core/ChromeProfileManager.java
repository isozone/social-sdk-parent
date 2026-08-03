package cn.net.rjnetwork.xianyu.chrome.core;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.exception.ChromeException;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyException;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyPoolManager;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Chrome 容器管理器（核心编排层）。
 *
 * <p>负责：
 * <ul>
 *   <li>容器生命周期：创建、启动、停止、销毁</li>
 *   <li>代理绑定：为每个容器 {@link ProxyPoolManager#acquire(ProxyAcquireRequest)} 获取专一代理，实现 IP 隔离</li>
 *   <li>指纹隔离：每个账号分配唯一 seed（SHA-256 派生），基于 seed 在注入 JS 中为 canvas/WebGL 生成唯一的、稳定的噪声；
 *       同 seed 噪声一致（跨重启指纹不变），不同 seed 噪声不同（账号间指纹唯一）</li>
 *   <li>崩溃检测：{@link #healthCheck()} 定时检测进程存活+CDP 就绪，标记 CRASHED</li>
 *   <li>崩溃恢复：在 {@link ChromeConfig#getMaxCrashRecoveryAttempts()} 内自动重启容器</li>
 *   <li>端口绑定：通过 {@link ChromePortPool} 管理 CDP 端口分配与回收</li>
 * </ul>
 *
 * <p>容器与账号是一对一关系：{@code accountId ↔ profileDir ↔ proxyUrl ↔ seed ↔ cdpPort}。
 */
@Component
public class ChromeProfileManager {

    private static final Logger log = LoggerFactory.getLogger(ChromeProfileManager.class);

    private final ChromeConfig config;
    private final ChromePortPool portPool;
    private final ChromeSession session;
    private final ChromeHealthChecker healthChecker;

    /**
     * 代理池（可选 — 非 Spring Boot 环境可以为 null，此时不自动绑定代理）。
     */
    private volatile ProxyPoolManager proxyPoolManager;

    /** 活跃容器（accountId → ChromeProfile） */
    private final Map<Long, ChromeProfile> activeProfiles = new ConcurrentHashMap<>();

    /** 崩溃恢复时使用的后台线程 */
    private final ScheduledExecutorService recoveryScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "chrome-recovery");
                t.setDaemon(true);
                return t;
            });

    public ChromeProfileManager(ChromeConfig config,
                                ChromePortPool portPool,
                                ChromeSession session,
                                ChromeHealthChecker healthChecker) {
        this.config = config;
        this.portPool = portPool;
        this.session = session;
        this.healthChecker = healthChecker;
    }

    /**
     * 注入代理池管理器（Spring 环境中自动注入，或手动设置）。
     */
    public void setProxyPoolManager(ProxyPoolManager proxyPoolManager) {
        this.proxyPoolManager = proxyPoolManager;
    }

    // ==================== 容器生命周期 ====================

    /**
     * 为指定账号创建并启动 Chrome 容器。
     *
     * @param accountId   账号 ID
     * @param accountName 账号名称（展示用）
     * @return 创建的 ChromeProfile
     * @throws ChromeException 启动失败
     */
    public synchronized ChromeProfile launchAccount(long accountId, String accountName) {
        if (activeProfiles.containsKey(accountId)) {
            ChromeProfile existing = activeProfiles.get(accountId);
            if (existing.isAlive()) {
                existing.setLastAccessAt(LocalDateTime.now());
                log.info("[LAUNCH] 容器已在运行, accountId={}", accountId);
                return existing;
            } else {
                // 旧容器已崩溃，清理后重新创建
                stopAccount(accountId);
            }
        }

        // 1. 派生 seed
        long seed = deriveSeed(accountId);

        // 2. CDP 端口分配。通常先启动新容器再回收旧容器，避免新启动失败误杀可用账号；
        // 但端口池无备用端口时，必须先回收一个 LRU 容器释放端口，否则无法启动新容器。
        ensurePortAvailableForLaunch(accountId);
        int port = portPool.acquirePort();

        // 3. 绑定代理
        String proxyUrl = null;
        String proxyLeaseId = null;
        if (proxyPoolManager != null) {
            try {
                ProxyAcquireRequest req = ProxyAcquireRequest.defaultRequest(accountId);
                var lease = proxyPoolManager.acquire(req);
                ProxyInfo proxyInfo = lease.getProxy();
                proxyUrl = proxyInfo.toProxyUri();
                proxyLeaseId = lease.getLeaseId();
                log.info("[LAUNCH] 代理绑定, accountId={}, proxy={}", accountId, proxyUrl);
            } catch (ProxyException pe) {
                portPool.releasePort(port);
                throw ChromeException.proxyBindingFailed(accountId, pe.getMessage());
            }
        }

        // 4. 构建 profile
        ChromeProfile profile = ChromeProfile.builder()
                .accountId(accountId)
                .accountName(accountName)
                .profileDir(config.resolveProfileDir(accountId))
                .cdpPort(port)
                .proxyUrl(proxyUrl)
                .proxyLeaseId(proxyLeaseId)
                .seed(seed)
                .status(ChromeProfile.ContainerStatus.INITIALIZING)
                .lastAccessAt(LocalDateTime.now())
                .crashCount(0)
                .build();

        activeProfiles.put(accountId, profile);

        // 5. 启动 Chrome 进程
        try {
            session.launch(profile);
            log.info("[LAUNCH] 容器启动成功, accountId={}, port={}", accountId, port);
        } catch (ChromeException ce) {
            // 启动失败：清理资源
            cleanupFailedLaunch(profile);
            throw ce;
        }

        // 5.1 持久化端口标记（供应用重启后 reattachAccount 复用）
        persistPortMarker(profile);

        // 6. 启动成功后注入反检测 JS（应用 per-account seed 噪声，失败不影响启动））
        safeInjectFingerprint(profile);

        // 7. 新容器确认可用后再回收超额旧容器，避免新启动失败时误杀正在工作的账号；
        // 同时排除刚启动的账号，避免并发访问让新 profile 变成 LRU 后被立即回收。
        enforceProfileLimit(accountId);

        return profile;
    }

    private void safeInjectFingerprint(ChromeProfile profile) {
        try {
            injectFingerprintScript(profile);
        } catch (Exception e) {
            log.warn("[LAUNCH] 指纹注入失败（非关键）, accountId={}, err={}", profile.getAccountId(), e.getMessage());
        }
    }

    /**
     * 停止指定账号的 Chrome 容器（释放端口 + 释放代理 + 优雅退出进程）。
     *
     * @param accountId 账号 ID
     */
    public synchronized void stopAccount(long accountId) {
        ChromeProfile profile = activeProfiles.remove(accountId);
        if (profile == null) {
            log.debug("[STOP] 无对应容器, accountId={}", accountId);
            return;
        }

        // 0. 清理端口标记
        removePortMarker(profile);

        // 1. 停止 Chrome 进程
        session.shutdown(profile);

        // 2. 释放代理租约
        if (proxyPoolManager != null && profile.getProxyLeaseId() != null) {
            try {
                proxyPoolManager.release(profile.getProxyLeaseId());
            } catch (Exception e) {
                log.warn("[STOP] 释放代理异常, accountId={}, err={}", accountId, e.getMessage());
            }
        }

        // 3. 释放端口
        session.releasePort(profile);

        log.info("[STOP] 容器已停止, accountId={}", accountId);
    }

    /**
     * 销毁所有容器（应用关闭时调用）。
     */
    @PreDestroy
    public synchronized void shutdown() {
        log.info("[SHUTDOWN] 关闭所有 Chrome 容器, count={}", activeProfiles.size());
        List<Long> ids = new ArrayList<>(activeProfiles.keySet());
        for (Long accountId : ids) {
            try {
                stopAccount(accountId);
            } catch (Exception e) {
                log.error("[SHUTDOWN] 关闭容器异常, accountId={}", accountId, e);
            }
        }
        activeProfiles.clear();
        recoveryScheduler.shutdownNow();
    }

    // ==================== 指纹注入 ====================

    /**
     * 构造指纹 accountId → seed（SHA-256 派生）。
     * <p>同 accountId → 同 seed（跨重启指纹不变）；不同 accountId → 不同 seed（账号间指纹唯一）。
     */
    public long deriveSeed(long accountId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(String.valueOf(accountId).getBytes(StandardCharsets.UTF_8));
            long seed = 0L;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                seed = (seed << 8) | (hash[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            return accountId * 31L;
        }
    }

    /**
     * 向指定容器的 page target 注入反检测脚本（双通道）。
     *
     * <p>通过 {@link ChromeSession#injectFingerprintScript(int, java.util.function.LongSupplier)} 发送：
     * <ol>
     *   <li>{@code Page.addScriptToEvaluateOnNewDocument} — 持久化，每次 SPA 跳转/刷新都自动注入</li>
     *   <li>{@code Runtime.evaluate} — 立刻在当前页面生效</li>
     * </ol>
     *
     * <p>脚本由 {@link SliderAntiDetect#buildScript(long)} 按 seed 派生（per-account 指纹隔离）。
     */
    public void injectFingerprintScript(ChromeProfile profile) throws IOException, TimeoutException {
        log.info("[INJECT] 开始注入指纹脚本, accountId={}, seed={}",
                profile.getAccountId(), profile.getSeed());
        session.injectFingerprintScript(profile.getCdpPort(), profile::getSeed);
    }

    // ==================== 查询 API ====================

    /**
     * 获取指定账号的容器状态。
     */
    public Optional<ChromeProfile> getProfile(long accountId) {
        ChromeProfile profile = activeProfiles.get(accountId);
        if (profile != null) {
            profile.setLastAccessAt(LocalDateTime.now());
        }
        return Optional.ofNullable(profile);
    }

    /**
     * 获取所有活跃容器。
     */
    public Map<Long, ChromeProfile> listProfiles() {
        return Map.copyOf(activeProfiles);
    }

    /**
     * 获取指定账号的 CDP 端点。
     *
     * @return CDP 端点，如 http://127.0.0.1:9222
     */
    public Optional<String> getCdpEndpoint(long accountId) {
        return getProfile(accountId).map(ChromeProfile::getCdpEndpoint);
    }

    /**
     * 获取指定账号绑定的代理 URL。
     */
    public Optional<String> getProxyUrl(long accountId) {
        return getProfile(accountId).map(ChromeProfile::getProxyUrl);
    }

    /**
     * 获取指定账号的容器是否存活。
     */
    public boolean isAlive(long accountId) {
        ChromeProfile p = activeProfiles.get(accountId);
        if (p != null && p.isAlive()) {
            p.setLastAccessAt(LocalDateTime.now());
            return true;
        }
        return false;
    }

    /**
     * 获取活跃容器数量。
     */
    public int getActiveCount() {
        return activeProfiles.size();
    }

    /**
     * 获取空闲 CDP 端口数。
     */
    public int getAvailablePorts() {
        return portPool.availableCount();
    }

    // ==================== 崩溃恢复 ====================

    /**
     * 定时回收空闲 Chrome 容器，避免多账号长期常驻导致机器内存耗尽。
     */
    @Scheduled(fixedDelayString = "${chrome.idle-cleanup-delay-ms:300000}")
    public synchronized void cleanupIdleProfiles() {
        long idleTimeoutMs = Math.max(60_000L, config.getIdleTimeoutMs());
        LocalDateTime threshold = LocalDateTime.now().minusNanos(idleTimeoutMs * 1_000_000L);
        List<Long> idleIds = activeProfiles.values().stream()
                .filter(p -> p.getLastAccessAt() != null && p.getLastAccessAt().isBefore(threshold))
                .sorted(Comparator.comparing(ChromeProfile::getLastAccessAt))
                .map(ChromeProfile::getAccountId)
                .toList();
        for (Long accountId : idleIds) {
            log.info("[CLEANUP] 回收空闲 Chrome 容器, accountId={}, idleTimeoutMs={}", accountId, idleTimeoutMs);
            stopAccount(accountId);
        }
        enforceProfileLimit(-1L);
    }

    /**
     * 定时磁盘配额检查：user-data-dir 总大小超过 {@code chrome.disk-quota-mb} 时，
     * 按最久未使用优先回收容器，直到低于配额。
     */
    @Scheduled(fixedDelayString = "${chrome.disk-cleanup-delay-ms:600000}")
    public synchronized void enforceDiskQuota() {
        long quotaBytes = Math.max(64L, config.getDiskQuotaMb()) * 1024L * 1024L;
        long total = dirSize(Paths.get(config.getUserDataDirRoot()));
        if (total <= quotaBytes) {
            return;
        }
        log.warn("[DISK] user-data-dir 超出磁盘配额, total={}MB, quota={}MB, 开始按 LRU 回收容器",
                total / 1024 / 1024, config.getDiskQuotaMb());
        List<ChromeProfile> candidates = activeProfiles.values().stream()
                .sorted(Comparator.comparing(p -> p.getLastAccessAt() != null ? p.getLastAccessAt() : p.getLaunchedAt()))
                .toList();
        for (ChromeProfile profile : candidates) {
            if (dirSize(Paths.get(config.getUserDataDirRoot())) <= quotaBytes) {
                break;
            }
            log.warn("[DISK] 回收容器释放磁盘, accountId={}", profile.getAccountId());
            stopAccount(profile.getAccountId());
        }
    }

    /** 容器指标（供监控/运维面板展示）。 */
    public static final class ContainerMetric {
        public final Long accountId;
        public final String accountName;
        public final int cdpPort;
        public final ChromeProfile.ContainerStatus status;
        public final long launchedAtEpochMs;
        public final long lastAccessAtEpochMs;
        public final boolean alive;
        public final Long processPid;
        public final Long processMemoryBytes;
        public final Long profileDirBytes;

        private ContainerMetric(Long accountId, String accountName, int cdpPort,
                                ChromeProfile.ContainerStatus status, long launchedAtEpochMs,
                                long lastAccessAtEpochMs, boolean alive, Long processPid,
                                Long processMemoryBytes, Long profileDirBytes) {
            this.accountId = accountId;
            this.accountName = accountName;
            this.cdpPort = cdpPort;
            this.status = status;
            this.launchedAtEpochMs = launchedAtEpochMs;
            this.lastAccessAtEpochMs = lastAccessAtEpochMs;
            this.alive = alive;
            this.processPid = processPid;
            this.processMemoryBytes = processMemoryBytes;
            this.profileDirBytes = profileDirBytes;
        }

        @Override
        public String toString() {
            return String.format("ContainerMetric{accountId=%d, port=%d, status=%s, alive=%s, mem=%s, dir=%s}",
                    accountId, cdpPort, status, alive,
                    processMemoryBytes != null ? processMemoryBytes / 1024 / 1024 + "MB" : "n/a",
                    profileDirBytes != null ? profileDirBytes / 1024 / 1024 + "MB" : "n/a");
        }
    }

    /**
     * 采集所有活跃容器的指标（内存取自 ProcessHandle，尽力而为；磁盘取自 profile 目录）。
     */
    public List<ContainerMetric> collectMetrics() {
        List<ContainerMetric> list = new ArrayList<>();
        for (ChromeProfile p : activeProfiles.values()) {
            Long pid = null;
            Long memBytes = null;
            Process proc = p.getChromeProcess();
            if (proc != null && proc.isAlive()) {
                pid = proc.pid();
                try {
                    ProcessHandle.Info info = proc.toHandle().info();
                    memBytes = info.totalCpuDuration() != null ? null : null; // CPU 时长非内存
                    java.util.Optional<String> cmd = info.command();
                    if (cmd.isPresent()) {
                        // 内存占用无标准 API，这里留空由上层按需扩展；PID 已足够监控
                    }
                } catch (Exception ignored) {
                }
            }
            Long dirBytes = null;
            try {
                Path dir = Paths.get(p.getProfileDir());
                if (Files.isDirectory(dir)) {
                    dirBytes = dirSize(dir);
                }
            } catch (Exception ignored) {
            }
            list.add(new ContainerMetric(
                    p.getAccountId(), p.getAccountName(), p.getCdpPort(), p.getStatus(),
                    p.getLaunchedAt() != null ? p.getLaunchedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L,
                    p.getLastAccessAt() != null ? p.getLastAccessAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L,
                    p.isAlive(), pid, memBytes, dirBytes));
        }
        return list;
    }

    /** 递归统计目录占用字节数（不存在返回 0）。 */
    private static long dirSize(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0L;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(f -> {
                        try {
                            return Files.size(f);
                        } catch (Exception e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void ensurePortAvailableForLaunch(long incomingAccountId) {
        // 枯竭排队：端口池无空闲且无 LRU 可回收时，在 exhaustionWaitMs 内轮询等待
        // （并发启动时其他线程可能正在释放端口/代理），超时后返回，由 acquirePort 抛无端口异常。
        long deadline = System.currentTimeMillis() + Math.max(0L, config.getExhaustionWaitMs());
        while (true) {
            if (portPool.availableCount() > 0) {
                return;
            }
            Optional<ChromeProfile> victim = findLeastRecentlyUsed(incomingAccountId);
            if (victim.isPresent()) {
                log.info("[CLEANUP] CDP 端口池已满, 启动新容器前回收 LRU 容器 accountId={}", victim.get().getAccountId());
                stopAccount(victim.get().getAccountId());
                continue; // 回收后回到循环重新确认有空闲端口
            }
            if (System.currentTimeMillis() >= deadline) {
                return; // 排队超时，交给 acquirePort 抛 NO_PORT
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void enforceProfileLimit(long incomingAccountId) {
        int maxActive = Math.max(1, config.getMaxActiveProfiles());
        int needToStop = activeProfiles.size() - maxActive;
        if (needToStop <= 0) {
            return;
        }
        List<ChromeProfile> candidates = activeProfiles.values().stream()
                .filter(p -> !p.getAccountId().equals(incomingAccountId))
                .sorted(Comparator.comparing(p -> p.getLastAccessAt() != null ? p.getLastAccessAt() : p.getLaunchedAt()))
                .toList();
        for (ChromeProfile profile : candidates) {
            if (needToStop-- <= 0) break;
            log.info("[CLEANUP] 达到 Chrome 容器上限, 回收最久未使用容器 accountId={}, maxActive={}",
                    profile.getAccountId(), maxActive);
            stopAccount(profile.getAccountId());
        }
    }

    private Optional<ChromeProfile> findLeastRecentlyUsed(long excludedAccountId) {
        return activeProfiles.values().stream()
                .filter(p -> !p.getAccountId().equals(excludedAccountId))
                .min(Comparator.comparing(p -> p.getLastAccessAt() != null ? p.getLastAccessAt() : p.getLaunchedAt()));
    }

    /**
     * 定时健康检测（每 N 秒，由 Spring Scheduling 或外部调度器调用）。
     *
     * <p>检测每个容器的：1) 进程存活；2) CDP 就绪。
     * 标记 CRASHED 的容器，触发恢复。
     */
    public void healthCheck() {
        for (Map.Entry<Long, ChromeProfile> entry : activeProfiles.entrySet()) {
            ChromeProfile profile = entry.getValue();
            Long accountId = entry.getKey();

            if (profile.getStatus() == ChromeProfile.ContainerStatus.LAUNCHING) {
                continue; // 启动中，跳过
            }

            boolean healthy = healthChecker.isHealthy(profile, session);
            if (healthy) {
                profile.setLastHealthCheckAt(java.time.LocalDateTime.now());
                if (profile.getStatus() == ChromeProfile.ContainerStatus.CRASHED) {
                    // 容器之前被标记为崩溃但实际已恢复
                    profile.setStatus(ChromeProfile.ContainerStatus.RUNNING);
                }
                continue;
            }

            // 不健康 → 标记崩溃并尝试恢复
            healthChecker.recordCrash(profile);
            log.warn("[HEALTH] 容器不健康, accountId={}, crashCount={}",
                    accountId, profile.getCrashCount());

            if (healthChecker.canRecover(profile)) {
                recoveryScheduler.schedule(() -> attemptRecovery(accountId),
                        config.getCrashRecoveryCooldownMs(), TimeUnit.MILLISECONDS);
            } else {
                log.error("[HEALTH] 达到最大重启次数, 容器保持崩溃状态, accountId={}", accountId);
            }
        }
    }

    /**
     * 尝试恢复指定账号的容器。
     */
    private void attemptRecovery(long accountId) {
        ChromeProfile profile = activeProfiles.get(accountId);
        if (profile == null) return;

        log.info("[RECOVERY] 尝试恢复容器, accountId={}", accountId);
        try {
            // 先强制清理旧资源
            session.shutdown(profile);
            session.releasePort(profile);

            // 重新启动
            portPool.occupyPort(profile.getCdpPort()); // 尝试复用旧端口
            session.launch(profile);
            healthChecker.resetCrashCount(profile);
            injectFingerprintScript(profile);
            log.info("[RECOVERY] 容器恢复成功, accountId={}, port={}", accountId, profile.getCdpPort());
        } catch (Exception e) {
            log.error("[RECOVERY] 容器恢复失败, accountId={}", accountId, e);
            healthChecker.recordCrash(profile);
        }
    }

    // ==================== 内部方法 ====================

    /** profile 目录下的端口标记文件名（记录该账号容器的 CDP 端口，供重启后重连）。 */
    private static final String PORT_MARKER_FILE = ".cdp-port";

    /**
     * 把账号容器的 CDP 端口写入 profile 目录标记文件。
     * 应用重启后 {@link #reattachAccount(long)} 读取该标记，若端口 CDP 仍就绪则直接复用。
     */
    private void persistPortMarker(ChromeProfile profile) {
        try {
            Path marker = Paths.get(profile.getProfileDir(), PORT_MARKER_FILE);
            Files.writeString(marker, String.valueOf(profile.getCdpPort()), StandardCharsets.UTF_8);
            log.debug("[MARKER] 已写入端口标记, accountId={}, port={}", profile.getAccountId(), profile.getCdpPort());
        } catch (Exception e) {
            log.warn("[MARKER] 写入端口标记失败(忽略), accountId={}, err={}", profile.getAccountId(), e.getMessage());
        }
    }

    /** 删除端口标记文件。 */
    private void removePortMarker(ChromeProfile profile) {
        try {
            Path marker = Paths.get(profile.getProfileDir(), PORT_MARKER_FILE);
            Files.deleteIfExists(marker);
        } catch (Exception ignored) {
        }
    }

    /**
     * 尝试重连已运行但未纳入管理的 Chrome 容器（进程非本模块启动）。
     *
     * <p>流程：读取 profile 目录的 {@code .cdp-port} 标记 → 探测该端口 CDP 是否就绪 →
     * 就绪则 occupyPort + {@code ChromeSession.attach} 复用（保登录态、不重启浏览器）。
     *
     * @return true = 重连成功并纳入 activeProfiles；false = 无标记 / 端口不可达 / 已存在
     */
    public synchronized boolean reattachAccount(long accountId) {
        if (activeProfiles.containsKey(accountId)) {
            return true; // 已在管理
        }
        Path marker = Paths.get(config.resolveProfileDir(accountId), PORT_MARKER_FILE);
        if (!Files.exists(marker)) {
            log.debug("[REATTACH] 无端口标记, 无法重连, accountId={}", accountId);
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(Files.readString(marker, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            log.warn("[REATTACH] 端口标记解析失败(删除), accountId={}, err={}", accountId, e.getMessage());
            try {
                Files.deleteIfExists(marker);
            } catch (Exception ignored) {
            }
            return false;
        }
        if (!session.isCdpReady(port)) {
            log.info("[REATTACH] 端口无 CDP 服务, 不重连(下次启动将走全新启动), accountId={}, port={}", accountId, port);
            try {
                Files.deleteIfExists(marker);
            } catch (Exception ignored) {
            }
            return false;
        }
        if (!portPool.occupyPort(port)) {
            log.warn("[REATTACH] 端口已被占用, 无法重连, accountId={}, port={}", accountId, port);
            return false;
        }
        ChromeProfile profile = ChromeProfile.builder()
                .accountId(accountId)
                .accountName("account-" + accountId)
                .profileDir(config.resolveProfileDir(accountId))
                .cdpPort(port)
                .seed(deriveSeed(accountId))
                .status(ChromeProfile.ContainerStatus.INITIALIZING)
                .lastAccessAt(LocalDateTime.now())
                .crashCount(0)
                .build();
        if (!session.attach(profile)) {
            portPool.releasePort(port);
            return false;
        }
        activeProfiles.put(accountId, profile);
        log.info("[REATTACH] 重连已运行 Chrome 成功, accountId={}, port={}", accountId, port);
        return true;
    }

    /**
     * 孤儿清扫：清理所有残留端口标记与残留锁文件（应用启动时调用一次）。
     * <p>只处理「无 CDP 服务」的标记与 profile 目录内的 Singleton 锁文件，不误伤运行中容器。
     */
    public synchronized void cleanupOrphans() {
        File root = new File(config.getUserDataDirRoot());
        File[] dirs = root != null && root.isDirectory() ? root.listFiles(File::isDirectory) : null;
        if (dirs == null) {
            return;
        }
        int cleaned = 0;
        for (File dir : dirs) {
            Path marker = dir.toPath().resolve(PORT_MARKER_FILE);
            if (Files.exists(marker)) {
                try {
                    int port = Integer.parseInt(Files.readString(marker, StandardCharsets.UTF_8).trim());
                    if (!session.isCdpReady(port)) {
                        Files.deleteIfExists(marker);
                        cleaned++;
                    }
                } catch (Exception e) {
                    try {
                        Files.deleteIfExists(marker);
                        cleaned++;
                    } catch (Exception ignored) {
                    }
                }
            }
            // 残留锁文件（Chrome 异常退出遗留，会阻塞下次启动）
            for (String lockName : List.of("SingletonLock", "SingletonSocket", "SingletonCookie")) {
                Path lock = dir.toPath().resolve(lockName);
                try {
                    if (Files.exists(lock)) {
                        Files.deleteIfExists(lock);
                        cleaned++;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (cleaned > 0) {
            log.info("[ORPHAN] 孤儿清扫完成, 清理 {} 项, root={}", cleaned, config.getUserDataDirRoot());
        }
    }

    private void cleanupFailedLaunch(ChromeProfile profile) {
        try {
            if (profile.getChromeProcess() != null) {
                session.shutdown(profile);
            }
            session.releasePort(profile);
            if (proxyPoolManager != null && profile.getProxyLeaseId() != null) {
                proxyPoolManager.release(profile.getProxyLeaseId());
            }
        } catch (Exception e) {
            log.warn("[LAUNCH] 清理异常, accountId={}", profile.getAccountId(), e);
        }
        activeProfiles.remove(profile.getAccountId());
    }
}
