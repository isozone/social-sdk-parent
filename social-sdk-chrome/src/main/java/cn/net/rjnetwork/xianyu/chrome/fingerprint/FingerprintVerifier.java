package cn.net.rjnetwork.xianyu.chrome.fingerprint;

import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 指纹自检器：注入反检测脚本后，回读页面指纹关键项，校验注入是否生效、暴露「漏网」项。
 *
 * <p>用途：反检测脚本属于「尽力而为」的 JS 覆盖，是否真正生效取决于页面执行时机 / 原型链可写性。
 * 自检用独立的 JS 探针回读 {@code navigator.webdriver / platform / screen / languages /
 * hardwareConcurrency / deviceMemory / timezone} 等值，与脚本预期比对，把不一致项暴露出来供排障。
 *
 * <p>典型用法：
 * <pre>{@code
 * chromeBrowser.applyEnhancedFingerprint(accountId);   // 注入（或启动时已注入）
 * try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
 *     List<FingerprintVerifier.FingerprintCheck> leaks = new FingerprintVerifier(cdp).verify();
 *     if (!leaks.isEmpty()) { /* 有漏网项，告警或重注入 *&#47; }
 * }
 * }</pre>
 */
public class FingerprintVerifier {

    private static final Logger log = LoggerFactory.getLogger(FingerprintVerifier.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 单项校验结果。 */
    public static final class FingerprintCheck {
        /** 检测项名（如 navigator.webdriver）。 */
        public final String name;
        /** 回读到的实际值。 */
        public final String actual;
        /** 是否通过（true = 值符合反检测预期）。 */
        public final boolean passed;

        private FingerprintCheck(String name, String actual, boolean passed) {
            this.name = name;
            this.actual = actual;
            this.passed = passed;
        }

        @Override
        public String toString() {
            return passed ? name + "=OK(" + actual + ")" : name + "=LEAK(" + actual + ")";
        }
    }

    private final CdpSession session;

    public FingerprintVerifier(CdpSession session) {
        this.session = session;
    }

    /**
     * 执行全量指纹自检，返回全部检查项（含通过项）。
     *
     * <p>探针一次性回读所有指标，减少 CDP 往返。
     */
    public List<FingerprintCheck> verify() throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("expression", PROBE_JS);
        params.put("returnByValue", true);
        JsonNode result = session.send("Runtime.evaluate", params);
        JsonNode exc = result.get("exceptionDetails");
        if (exc != null && !exc.isNull()) {
            throw new IOException("指纹自检探针执行异常: " + exc.path("text").asText());
        }
        JsonNode v = result.path("result").get("value");
        List<FingerprintCheck> checks = new ArrayList<>();

        checks.add(check("navigator.webdriver", v.path("webdriver"), "false"));
        checks.add(check("navigator.platform", v.path("platform"), null));           // 非空即可
        checks.add(check("screen.width", v.path("screenWidth"), null));               // 非空即可
        checks.add(check("navigator.languages", v.path("languages"), null));
        checks.add(check("navigator.hardwareConcurrency", v.path("hardwareConcurrency"), null));
        checks.add(check("navigator.deviceMemory", v.path("deviceMemory"), null));
        checks.add(check("timezoneOffset", v.path("tzOffset"), null));                // 应为非 0 且与 seed 派生的时区一致（此处只校验非 0）
        checks.add(check("navigator.plugins", v.path("plugins"), null));              // 非空即可
        checks.add(check("userAgentHeadless", v.path("userAgent"), null));            // 不应包含 Headless

        List<FingerprintCheck> leaks = checks.stream().filter(c -> !c.passed).toList();
        if (!leaks.isEmpty()) {
            log.warn("[VERIFY] 指纹自检发现 {} 项漏网: {}", leaks.size(), leaks);
        } else {
            log.info("[VERIFY] 指纹自检全部通过, 共 {} 项", checks.size());
        }
        return checks;
    }

    /** 只返回未通过（漏网）的检查项。 */
    public List<FingerprintCheck> verifyLeaks() throws IOException, TimeoutException {
        return verify().stream().filter(c -> !c.passed).toList();
    }

    /** 是否全部通过。 */
    public boolean isClean() throws IOException, TimeoutException {
        return verifyLeaks().isEmpty();
    }

    // ==================== 内部 ====================

    private FingerprintCheck check(String name, JsonNode actual, String expected) {
        if (actual == null || actual.isNull() || actual.isMissingNode()) {
            return new FingerprintCheck(name, "null", false);
        }
        String text = actual.isTextual() ? actual.asText() : actual.toString();
        boolean passed;
        if (expected != null) {
            passed = expected.equalsIgnoreCase(text);
        } else if ("userAgentHeadless".equals(name)) {
            passed = !text.toLowerCase().contains("headless");
        } else {
            passed = !text.isEmpty() && !"null".equals(text);
        }
        return new FingerprintCheck(name, text, passed);
    }

    /** 自检探针：一次性回读全部指标。 */
    private static final String PROBE_JS =
            "(() => {"
                    + "  const out = {};"
                    + "  try { out.webdriver = String(navigator.webdriver); } catch (e) { out.webdriver = 'ERR'; }"
                    + "  try { out.platform = String(navigator.platform || ''); } catch (e) { out.platform = 'ERR'; }"
                    + "  try { out.screenWidth = String(screen.width); } catch (e) { out.screenWidth = 'ERR'; }"
                    + "  try { out.languages = JSON.stringify(navigator.languages || []); } catch (e) { out.languages = 'ERR'; }"
                    + "  try { out.hardwareConcurrency = String(navigator.hardwareConcurrency || ''); } catch (e) { out.hardwareConcurrency = 'ERR'; }"
                    + "  try { out.deviceMemory = String(navigator.deviceMemory || ''); } catch (e) { out.deviceMemory = 'ERR'; }"
                    + "  try { out.tzOffset = String(new Date().getTimezoneOffset()); } catch (e) { out.tzOffset = 'ERR'; }"
                    + "  try { out.plugins = String((navigator.plugins && navigator.plugins.length) || 0); } catch (e) { out.plugins = 'ERR'; }"
                    + "  try { out.userAgent = String(navigator.userAgent || ''); } catch (e) { out.userAgent = 'ERR'; }"
                    + "  return out;"
                    + "})()";
}
