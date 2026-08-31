package cn.net.rjnetwork.xianyu.manager.clouddisk.service;

import cn.net.rjnetwork.xianyu.manager.config.OpenListProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 容器启动后自动安装（若二进制缺失）并拉起 OpenList。
 *
 * <p>原设计 OpenList 只能由前端按钮触发 /install、/start，导致 docker compose up
 * 或容器重启后 OpenList 进程根本不存在，后端所有调用连 127.0.0.1:5244 被拒，
 * 功能完全不可用。本组件在应用启动完成后于后台线程确保 OpenList 就绪，
 * 不打断主启动流程（healthcheck 不依赖 OpenList）。</p>
 *
 * <p>通过 {@code openlist.auto-start}（默认 true）开关；设为 false 则行为与旧版一致，
 * 仍需手动在页面点「启动」。</p>
 */
@Component
public class OpenListAutoStarter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenListAutoStarter.class);

    private final OpenListProperties properties;
    private final OpenListTaskService taskService;

    public OpenListAutoStarter(OpenListProperties properties, OpenListTaskService taskService) {
        this.properties = properties;
        this.taskService = taskService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoStart()) {
            log.info("[OpenList] auto-start 已关闭，跳过自动安装/启动（需手动在页面启动）");
            return;
        }

        // 后台线程执行，避免阻塞应用启动（下载可能耗时）
        Thread t = new Thread(this::bootstrap, "openlist-autostart");
        t.setDaemon(true);
        t.start();

        // 容器停止时尽量优雅关闭 OpenList 子进程
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                taskService.stopOpenList();
            } catch (Exception ignored) {
            }
        }));
    }

    private void bootstrap() {
        try {
            if (!taskService.isInstalled()) {
                log.info("[OpenList] 未检测到二进制，开始自动安装...");
                try {
                    taskService.startInstallAsync().get(15, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.error("[OpenList] 自动安装失败: {}", e.getMessage());
                    return;
                }
            }
            log.info("[OpenList] 自动启动 OpenList...");
            try {
                taskService.startOpenListAsync().get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("[OpenList] 自动启动失败: {}", e.getMessage());
                return;
            }
            log.info("[OpenList] 自动启动完成，状态: {}", taskService.getStatus().get("phase"));
        } catch (Exception e) {
            log.error("[OpenList] 自动启动异常: {}", e.getMessage());
        }
    }
}
