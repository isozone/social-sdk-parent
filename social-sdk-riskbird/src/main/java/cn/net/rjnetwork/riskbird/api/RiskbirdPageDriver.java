package cn.net.rjnetwork.riskbird.api;

import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Riskbird 页面驱动抽象：登录 / 查询 / 检索 / 搜索的浏览器能力最小接口。
 *
 * <p>默认实现 {@link ChromeRiskbirdDriver} 基于 social-sdk-chrome 的 {@code ChromeBrowser}
 * （每账号独立容器）；单元测试可注入 mock 实现（如 MockWebServer 模拟站点）验证业务逻辑。
 */
public interface RiskbirdPageDriver extends AutoCloseable {

    /**
     * 账号密码登录：打开登录页 → 填充表单 → 提交 → 等待登录态生效。
     *
     * @param username 账号
     * @param password 密码
     */
    RiskbirdLoginResult loginWithPassword(String username, String password) throws IOException, TimeoutException, InterruptedException;

    /**
     * 扫码登录：打开登录页，返回二维码图片（base64）与轮询会话；调用方展示二维码，
     * 后续通过 {@link #waitQrLogin(String)} 轮询登录结果。
     *
     * @return 二维码 base64（data URL 或裸 base64）
     */
    String prepareQrLogin() throws IOException, TimeoutException, InterruptedException;

    /**
     * 等待扫码登录完成（轮询登录态），超时返回失败。
     *
     * @param qrSession 由 {@link #prepareQrLogin()} 返回的会话标识（可为 null，实现自行跟踪）
     */
    RiskbirdLoginResult waitQrLogin(String qrSession) throws IOException, TimeoutException, InterruptedException;

    /**
     * Cookie 登录：直接把已登录 Cookie 注入浏览器会话（免登录）。
     *
     * @param cookieHeader cookie header 形式（k1=v1; k2=v2）
     */
    RiskbirdLoginResult loginWithCookie(String cookieHeader) throws IOException, TimeoutException;

    /**
     * 当前是否已登录（存在有效登录态）。
     */
    boolean isLoggedIn() throws IOException, TimeoutException;

    /**
     * 提取当前登录态 Cookie（供持久化复用）。
     */
    String extractCookieHeader() throws IOException, TimeoutException;

    /**
     * 关键词搜索：输入关键词 → 触发搜索 → 收集结果列表（API 优先 / DOM 兜底由实现决定）。
     *
     * @param keyword 关键词（企业名 / 法定代表人等）
     * @param page    页码（1 起）
     */
    RiskbirdSearchResult search(String keyword, int page) throws IOException, TimeoutException, InterruptedException;

    /**
     * 查询企业详情（按名称）。
     *
     * @param companyName 企业名称
     */
    RiskbirdCompany queryCompany(String companyName) throws IOException, TimeoutException, InterruptedException;

    /**
     * 检索（多页聚合）：连续搜索并聚合结果，直到 page 翻完或达到 maxPages。
     *
     * @param keyword  关键词
     * @param maxPages 最多翻页数（1 起）
     */
    RiskbirdSearchResult retrieve(String keyword, int maxPages) throws IOException, TimeoutException, InterruptedException;
}
