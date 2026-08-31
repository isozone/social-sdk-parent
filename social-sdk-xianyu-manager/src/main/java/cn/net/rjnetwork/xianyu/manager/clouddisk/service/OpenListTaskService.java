package cn.net.rjnetwork.xianyu.manager.clouddisk.service;

import cn.net.rjnetwork.xianyu.manager.config.OpenListProperties;
import cn.net.rjnetwork.xianyu.manager.clouddisk.mapper.OpenlistInstanceMapper;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.OpenlistInstance;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class OpenListTaskService {

    private final OpenListProperties properties;
    private final OpenListInstallerService installerService;
    private final OpenlistInstanceMapper instanceMapper;
    private final Path dataDir;
    private String osName;
    private String arch;
    private String downloadUrl;

    private volatile String currentTaskId;
    private volatile double progress; // 0.0 - 1.0
    private volatile String currentPhase; // idle, downloading, extracting, starting, running, failed, stopped
    private volatile String currentMessage;

    private volatile Process process;

    // 抓取到的初始账号密码
    private volatile String initialUsername;
    private volatile String initialPassword;
    private volatile boolean initialCredsCaptured;

    public static final long SSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final int MAX_SSE_EMITTERS = 32;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public OpenListTaskService(OpenListProperties properties, OpenListInstallerService installerService, OpenlistInstanceMapper instanceMapper) {
        this.properties = properties;
        this.installerService = installerService;
        this.instanceMapper = instanceMapper;
        this.dataDir = installerService.getDataDir();

        this.currentPhase = "idle";
        this.currentMessage = "空闲";

        detectSystem();
    }

    private void detectSystem() {
        this.osName = System.getProperty("os.name").toLowerCase();
        String rawArch = System.getProperty("os.arch").toLowerCase();
        this.arch = rawArch.contains("amd64") || rawArch.contains("x86_64") ? "amd64"
                   : rawArch.contains("arm64") || rawArch.contains("aarch64") ? "arm64"
                   : "amd64"; // fallback

        String osPart = osName.contains("win") ? "windows" : osName.contains("mac") ? "darwin" : "linux";
        this.downloadUrl = String.format(
            "https://github.com/OpenListTeam/OpenList/releases/latest/download/openlist-%s-%s%s",
            osPart,
            arch,
            osName.contains("win") ? ".zip" : ".tar.gz"
        );
    }

    public boolean isInstalled() {
        try {
            Path p = installerService.getExecutablePath();
            return p != null && Files.isRegularFile(p);
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("installed", Files.exists(installerService.getExecutablePath()));
        s.put("running", process != null && process.isAlive());
        s.put("version", "latest");
        s.put("port", properties.getPort());
        s.put("url", properties.getUrl());
        s.put("username", properties.getUsername());
        s.put("password", properties.getPassword());
        s.put("downloadUrl", downloadUrl);
        s.put("localBinaryName", installerService.getExecutablePath().getFileName().toString());
        s.put("execPath", installerService.getExecutablePath().toString());
        s.put("osName", osName);
        s.put("arch", arch);
        s.put("phase", currentPhase);
        s.put("progress", progress);
        s.put("message", currentMessage);
        // 暴露首次启动自动生成的账号密码（优先内存，否则从数据库恢复）
        String finalUsername = initialUsername;
        String finalPassword = initialPassword;
        boolean finalCaptured = initialCredsCaptured;
        if (!initialCredsCaptured) {
            try {
                com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OpenlistInstance> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
                wrapper.eq("port", properties.getPort());
                OpenlistInstance instance = instanceMapper.selectOne(wrapper);
                if (instance != null && instance.getInitialUsername() != null) {
                    finalUsername = instance.getInitialUsername();
                    finalPassword = instance.getInitialPassword();
                    finalCaptured = true;
                }
            } catch (Exception e) {
                System.err.println("[OpenList] 查询数据库账号失败: " + e.getMessage());
            }
        }
        s.put("initialUsername", finalUsername);
        s.put("initialPassword", finalPassword);
        s.put("initialCredsCaptured", finalCaptured);
        return s;
    }

    public synchronized CompletableFuture<Void> startInstallAsync() {
        // 允许从 failed/stopped 状态重试安装，避免安装失败后永久卡死
        if (!"idle".equals(currentPhase) && !"failed".equals(currentPhase) && !"stopped".equals(currentPhase)) {
            throw new IllegalStateException("当前状态: " + currentPhase);
        }
        currentTaskId = UUID.randomUUID().toString();
        progress = 0.0;
        currentPhase = "downloading";
        currentMessage = "正在下载 OpenList...";

        return CompletableFuture.runAsync(this::doInstall);
    }

    private void doInstall() {
        try {
            // 下载 + 解压 + 解析真实二进制（含镜像回退），统一交由 OpenListInstallerService 处理
            updatePhase("downloading", "正在下载 OpenList...", 0.1);
            Files.createDirectories(dataDir);
            if (!Files.isWritable(dataDir)) {
                throw new IOException("数据目录不可写: " + dataDir + " (Permission denied)，请检查目录属主/权限后重试");
            }
            installerService.install();
            updatePhase("idle", "安装完成", 1.0);
        } catch (Exception e) {
            updatePhase("failed", "安装失败: " + e.getMessage(), 0.0);
        }
    }

    private void downloadFile(String urlString, Path target) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        long totalSize = conn.getContentLengthLong();
        long downloaded = 0;

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(target.toFile())) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                downloaded += n;
                if (totalSize > 0) {
                    progress = 0.1 + (downloaded / (double) totalSize) * 0.6;
                }
            }
        }
    }

    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String fileName = new File(entry.getName()).getName();
                    Path outPath = targetDir.resolve(fileName).normalize();
                    if (!outPath.startsWith(targetDir)) continue;
                    Files.createDirectories(outPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public synchronized CompletableFuture<Void> startOpenListAsync() {
        if (!"idle".equals(currentPhase) && !"stopped".equals(currentPhase)) {
            throw new IllegalStateException("当前状态: " + currentPhase);
        }
        currentTaskId = UUID.randomUUID().toString();
        progress = 0.0;
        currentPhase = "starting";
        currentMessage = "正在启动 OpenList...";

        return CompletableFuture.runAsync(this::doStart);
    }

    private void doStart() {
        try {
            Path execPath = installerService.getExecutablePath();
            if (!Files.exists(execPath)) {
                updatePhase("failed", "OpenList 未安装", 0.0);
                return;
            }

            // mac/linux 确保有执行权限
            if (!osName.contains("win")) {
                try {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
                    Files.setPosixFilePermissions(execPath, perms);
                } catch (UnsupportedOperationException ignored) {}
            }

            // 统一使用绝对路径作为 OpenList 数据目录：
            // 避免相对路径与 pb.directory 叠加算出嵌套错误路径，导致 config.json 找不到。
            Path dataAbs = dataDir.toAbsolutePath().normalize();
            boolean firstRun = !Files.exists(dataAbs.resolve("data.db"));

            // 确保 dataDir 存在并写入 config.json 配置端口
            Files.createDirectories(dataAbs);
            writeConfigJson(dataAbs);

            // OpenList server 不支持 --port flag，端口通过 config.json 的 scheme.http_port 配置。
            // --data 必须用绝对路径，否则相对路径以进程工作目录为基准会导致定位错乱。
            ProcessBuilder pb = new ProcessBuilder(
                execPath.toString(),
                "server",
                "--data", dataAbs.toString()
            );
            pb.directory(dataAbs.toFile());
            pb.redirectErrorStream(true);

            this.process = pb.start();

            // 读取输出，抓取初始密码
            final Process p = this.process;
            Thread logThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[OpenList] " + line);
                        // 抓取 OpenList 自动生成的初始账号密码
                        // 典型日志: "INFO ... admin user has been initialized randomly, username: xxx, password: xxx"
                        // 或: "INFO ... Initial username: xxx password: xxx"
                        captureInitialCreds(line);
                        if (line.contains(" Listening ") || line.contains("listen") || line.contains("started")) {
                            updatePhase("running", "启动成功，正在运行...", 1.0);
                        }
                    }
                } catch (IOException e) {}
            }, "openlist-log");
            logThread.setDaemon(true);
            logThread.start();

            // 等待启动
            Thread.sleep(3000);
            if (p.isAlive()) {
                // 首次初始化（尚无 data.db）时把配置的账号密码应用到 OpenList，
                // 否则 OpenList 会忽略 config.json 里的 admin 字段并每次随机生成。
                if (firstRun) {
                    applyConfiguredAdminCreds(execPath, dataAbs);
                }
                updatePhase("running", "启动成功", 1.0);
            } else {
                updatePhase("failed", "启动失败", 0.0);
            }

        } catch (Exception e) {
            updatePhase("failed", "启动失败: " + e.getMessage(), 0.0);
        }
    }

    /**
     * OpenList 的 config.json 并不支持 admin_username/admin_password 字段（会被忽略），
     * admin 密码只能通过 `openlist admin set <password>` 在启动后设定。
     * 首次初始化（数据目录尚无 data.db）时执行一次，确保配置的账号密码(如 huzhenjie)生效。
     */
    private void applyConfiguredAdminCreds(Path execPath, Path dataAbs) {
        String configuredPassword = properties.getPassword();
        if (configuredPassword == null || configuredPassword.isBlank()) {
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                execPath.toString(),
                "admin", "set", configuredPassword,
                "--data", dataAbs.toString()
            );
            pb.directory(dataAbs.toFile());
            pb.redirectErrorStream(true);
            Process admin = pb.start();
            boolean finished = admin.waitFor(15, TimeUnit.SECONDS);
            if (finished && admin.exitValue() == 0) {
                System.out.println("[OpenList] 已应用配置的 admin 密码");
                // 同步内存与数据库，让前端“初始账号/密码”展示一致
                this.initialUsername = properties.getUsername();
                this.initialPassword = configuredPassword;
                this.initialCredsCaptured = true;
                persistCredentialsToDb(properties.getUsername(), configuredPassword);
            } else {
                System.out.println("[OpenList] 应用 admin 密码失败，将使用 OpenList 随机密码");
            }
        } catch (Exception e) {
            System.err.println("[OpenList] 应用 admin 密码异常: " + e.getMessage());
        }
    }

    /**
     * 从日志行中抓取 OpenList 自动生成的初始账号密码。
     */
    private void captureInitialCreds(String line) {
        if (initialCredsCaptured || line == null) return;
        String lower = line.toLowerCase();
        if (!lower.contains("username") && !lower.contains("password")) return;

        String username = null;
        String password = null;

        java.util.regex.Pattern uPat = java.util.regex.Pattern.compile(
            "(?:username|user)\\s*[:=]\\s*(\\S+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern pPat = java.util.regex.Pattern.compile(
            "(?:password|passwd)\\s*[:=]\\s*(\\S+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher uMat = uPat.matcher(line);
        java.util.regex.Matcher pMat = pPat.matcher(line);
        if (uMat.find()) username = uMat.group(1).replaceAll("[^a-zA-Z0-9_@-]", "");
        if (pMat.find()) password = pMat.group(1).replaceAll("[,;\"']", "");

        if (username == null && lower.contains("admin user has been initialized")) {
            java.util.regex.Pattern altPat = java.util.regex.Pattern.compile(
                "admin user has been initialized.*?username[:=\\s]+([^\\s,;]+).*?password[:=\\s]+([^\\s,;]+)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher altMat = altPat.matcher(line);
            if (altMat.find()) {
                username = altMat.group(1);
                password = altMat.group(2);
            }
        }

        if (username != null && password != null) {
            this.initialUsername = username;
            this.initialPassword = password;
            this.initialCredsCaptured = true;
            System.out.println("[OpenList] 已抓取初始账号: " + username);
            persistCredentialsToDb(username, password);
        }
    }

    /**
     * 将抓取到的初始账号密码持久化到数据库。
     */
    private void persistCredentialsToDb(String username, String password) {
        try {
            // 查找是否已有记录
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OpenlistInstance> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.eq("port", properties.getPort());
            OpenlistInstance instance = instanceMapper.selectOne(wrapper);

            LocalDateTime now = LocalDateTime.now();
            if (instance == null) {
                instance = new OpenlistInstance();
                instance.setPort(properties.getPort());
                instance.setUrl("http://127.0.0.1:" + properties.getPort());
                instance.setDataDir(dataDir.toString());
                instance.setInstallPath(installerService.getExecutablePath() != null ? installerService.getExecutablePath().toString() : null);
                instance.setOsName(osName);
                instance.setArch(arch);
                instance.setInstalled(1);
                instance.setRunning(1);
                instance.setFirstStartedAt(now);
                instance.setLastStartedAt(now);
                instance.setCreatedAt(now);
                instance.setUpdatedAt(now);
            }
            instance.setInitialUsername(username);
            instance.setInitialPassword(password);
            instance.setRunning(1);
            instance.setLastStartedAt(now);
            instance.setUpdatedAt(now);

            if (instance.getId() == null) {
                instanceMapper.insert(instance);
            } else {
                instanceMapper.updateById(instance);
            }
            System.out.println("[OpenList] 初始账号已持久化到数据库");
        } catch (Exception e) {
            System.err.println("[OpenList] 持久化账号失败: " + e.getMessage());
        }
    }

    /**
     * 写入 config.json：配置监听端口、数据库与各数据目录（均为绝对路径）。
     *
     * <p>注意：OpenList 的配置结构不含 admin_username/admin_password 字段（会被静默忽略），
     * admin 密码需在启动后通过 `openlist admin set` 设定（见 applyConfiguredAdminCreds）。</p>
     */
    private void writeConfigJson(Path dataAbs) {
        Path configFile = dataAbs.resolve("config.json");
        try {
            String dbFile = dataAbs.resolve("data.db").toString();
            String tempDir = dataAbs.resolve("temp").toString();
            String bleveDir = dataAbs.resolve("bleve").toString();
            String logName = dataAbs.resolve("log").resolve("log.log").toString();
            String config = String.format(
                "{\n" +
                "  \"force\": true,\n" +
                "  \"scheme\": {\n" +
                "    \"address\": \"0.0.0.0\",\n" +
                "    \"http_port\": %d,\n" +
                "    \"https_port\": -1,\n" +
                "    \"force_https\": false,\n" +
                "    \"cert_file\": \"\",\n" +
                "    \"key_file\": \"\"\n" +
                "  },\n" +
                "  \"jwt_secret\": \"%s\",\n" +
                "  \"database\": {\n" +
                "    \"type\": \"sqlite3\",\n" +
                "    \"db_file\": \"%s\",\n" +
                "    \"table_prefix\": \"x_\"\n" +
                "  },\n" +
                "  \"temp_dir\": \"%s\",\n" +
                "  \"bleve_dir\": \"%s\",\n" +
                "  \"log\": {\n" +
                "    \"enable\": true,\n" +
                "    \"name\": \"%s\"\n" +
                "  }\n" +
                "}\n",
                properties.getPort(),
                UUID.randomUUID().toString().replace("-", ""),
                dbFile,
                tempDir,
                bleveDir,
                logName
            );
            Files.writeString(configFile, config);
        } catch (IOException e) {
            System.err.println("[OpenList] 写入 config.json 失败: " + e.getMessage());
        }
    }

    public synchronized void stopOpenList() {
        try {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            // 兜底：用系统命令杀
            if (osName.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"taskkill", "/F", "/IM", "openlist.exe"});
            } else {
                Runtime.getRuntime().exec(new String[]{"pkill", "-f", "openlist"});
            }
            updatePhase("stopped", "已停止", 0.0);
        } catch (Exception e) {
            updatePhase("failed", "停止失败: " + e.getMessage(), 0.0);
        } finally {
            process = null;
        }
    }

    public CompletableFuture<Void> restartOpenListAsync() {
        stopOpenList();
        try { Thread.sleep(1000); } catch (Exception e) {}
        return startOpenListAsync();
    }

    private void updatePhase(String phase, String message, double pr) {
        this.currentPhase = phase;
        this.currentMessage = message;
        this.progress = pr;
        // 推送给所有 SSE 监听者；发送失败立即移除，避免坏连接长期占用内存。
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(Map.of(
                    "phase", phase,
                    "message", message,
                    "progress", pr
                ));
            } catch (Exception e) {
                emitters.remove(id);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        });
    }

    public synchronized void subscribe(SseEmitter emitter) {
        if (emitters.size() >= MAX_SSE_EMITTERS) {
            String oldest = emitters.keys().hasMoreElements() ? emitters.keys().nextElement() : null;
            if (oldest != null) {
                SseEmitter removed = emitters.remove(oldest);
                if (removed != null) {
                    try { removed.complete(); } catch (Exception ignored) {}
                }
            }
        }
        String id = UUID.randomUUID().toString();
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(e -> emitters.remove(id));
    }

    public Map<String, Object> getCurrentProgress() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("taskId", currentTaskId);
        p.put("phase", currentPhase);
        p.put("progress", progress);
        p.put("message", currentMessage);
        return p;
    }
}
