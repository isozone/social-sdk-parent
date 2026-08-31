package cn.net.rjnetwork.xianyu.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openlist")
public class OpenListProperties {

    private String url = "http://127.0.0.1:5244";
    private String username = "admin";
    private String password = "openlist";
    private String dataDir = "./data/openlist";
    private String executableName = "openlist.exe";
    private int port = 5244;

    /**
     * 可选：直接指定已存在的 OpenList 二进制绝对路径（如 Docker 镜像内预置的 /opt/openlist/openlist）。
     * 非空时优先使用，跳过运行时下载。为空则按 dataDir 内解压的二进制解析。
     */
    private String binaryPath = "";

    /**
     * 容器启动后是否自动安装（若二进制缺失）并拉起 OpenList。
     * 默认 true，保证 docker compose up / 重启后功能可用，无需手动点按钮。
     */
    private boolean autoStart = true;

    /**
     * 对外分享链接使用的可访问基址（如 http://192.168.1.252:5244）。
     * 为空时回落到 url（默认 127.0.0.1:5244，容器外不可达）。
     */
    private String publicUrl = "";

    /**
     * 下载 base url 候选列表（按顺序尝试）。默认 GitHub latest；
     * 受限网络可经 env OPENLIST_DOWNLOAD_BASE_URLS 注入镜像（逗号分隔）。
     */
    private java.util.List<String> downloadBaseUrls =
            java.util.List.of("https://github.com/OpenListTeam/OpenList/releases/latest/download");

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public String getExecutableName() { return executableName; }
    public void setExecutableName(String executableName) { this.executableName = executableName; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getBinaryPath() { return binaryPath; }
    public void setBinaryPath(String binaryPath) { this.binaryPath = binaryPath; }
    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public java.util.List<String> getDownloadBaseUrls() { return downloadBaseUrls; }
    public void setDownloadBaseUrls(java.util.List<String> downloadBaseUrls) { this.downloadBaseUrls = downloadBaseUrls; }
}
