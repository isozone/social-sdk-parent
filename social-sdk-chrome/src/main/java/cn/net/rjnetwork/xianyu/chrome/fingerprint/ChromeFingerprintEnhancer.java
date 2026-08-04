package cn.net.rjnetwork.xianyu.chrome.fingerprint;

import cn.net.rjnetwork.xianyu.captcha.service.SliderAntiDetect;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;

/**
 * 增强反指纹脚本（补充 {@link SliderAntiDetect} 未覆盖的高危指纹维度）。
 *
 * <p>新增维度（全部按账号 seed 派生、跨账号隔离、同账号稳定）：
 * <ul>
 *   <li><b>时区</b> — 覆盖 {@code Date.prototype.getTimezoneOffset} 与
 *       {@code Intl.DateTimeFormat.prototype.resolvedOptions}，时区名与 UTC 偏移取自同一组池，保持自洽</li>
 *   <li><b>地理位置</b> — mock {@code navigator.geolocation} 返回 seed 派生的坐标</li>
 *   <li><b>字体</b> — 覆盖 {@code document.fonts.check}，规避字体枚举指纹</li>
 *   <li><b>音频指纹</b> — 给 {@code AnalyserNode.getFloatFrequencyData} 注入稳定噪声，破坏 AudioContext 指纹</li>
 *   <li><b>设备参数</b> — devicePixelRatio / maxTouchPoints / vendor 对齐</li>
 *   <li><b>语音合成</b> — 固定 voices 列表，规避 getVoices 枚举指纹</li>
 *   <li><b>覆盖参数</b> — 支持 {@link ChromeProfile.FingerprintOverride} 显式指定 webgl/screen/hardware 等</li>
 * </ul>
 *
 * <p>脚本带防重入标记（window.__enhancedAntiDetectApplied），重复注入不会二次 patch 原型链。
 * 用法：启动后通过 {@code Page.addScriptToEvaluateOnNewDocument} + {@code Runtime.evaluate}
 * 双通道注入（与 {@code SliderAntiDetect.buildScript} 一致，两者可叠加执行）。
 */
public final class ChromeFingerprintEnhancer {

    private ChromeFingerprintEnhancer() {}

    /** 可选时区池（与语言 zh-CN 组合尽量自洽）。 */
    private static final String[] TIMEZONES = {
            "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Taipei", "Asia/Singapore",
            "Asia/Tokyo", "Asia/Seoul", "Europe/London", "America/New_York"
    };

    /** 与 {@link #TIMEZONES} 一一对应的 UTC 偏移（分钟）。 */
    private static final long[] TZ_OFFSETS = {
            480, 480, 480, 480, 540, 540, 0, -300
    };

    /**
     * 生成增强反指纹脚本（仅按 seed 派生，无覆盖参数）。
     */
    public static String buildEnhancedScript(long seed) {
        return buildEnhancedScript(seed, null);
    }

    /**
     * 生成增强反指纹脚本。
     *
     * @param seed     账号指纹种子（同账号稳定、跨账号唯一）
     * @param override 可选覆盖参数（null 表示用 seed 派生）
     */
    public static String buildEnhancedScript(long seed, ChromeProfile.FingerprintOverride override) {
        long tzNoise = SliderAntiDetect.deriveNoise(seed, "enh.tz");
        long geoNoise = SliderAntiDetect.deriveNoise(seed, "enh.geo");
        long audioNoise = SliderAntiDetect.deriveNoise(seed, "enh.audio");
        long touchNoise = SliderAntiDetect.deriveNoise(seed, "enh.touch");
        long dprNoise = SliderAntiDetect.deriveNoise(seed, "enh.dpr");

        int tzIndex = (int) (tzNoise % TIMEZONES.length);
        String timezone = TIMEZONES[tzIndex];
        long offsetMinutes = TZ_OFFSETS[tzIndex];
        double lat = 18.0 + (geoNoise % 3500) / 100.0;      // 18.00 ~ 52.99
        double lng = 73.0 + (geoNoise % 6100) / 100.0;      // 73.00 ~ 133.99
        double dpr = 1.0 + (dprNoise % 2);                   // 1.0 / 2.0
        long maxTouchPoints = (touchNoise % 3) + 1;          // 1 ~ 3

        return "(() => {\n"
                + "  'use strict';\n"
                + "  // 防重入标记：不可枚举 + 隐蔽命名，避免被反检测脚本直接识别注入痕迹\n"
                + "  try { if (window._f7z9a) return; } catch (e) {}\n"
                + "  try { Object.defineProperty(window, '_f7z9a', { value: 1, enumerable: false, configurable: false }); } catch (e) {}\n"
                + "  const TZ = '" + timezone + "';\n"
                + "  const TZ_OFFSET_MIN = " + offsetMinutes + ";\n"
                + "  const LAT = " + lat + ";\n"
                + "  const LNG = " + lng + ";\n"
                + "  const DPR = " + dpr + ";\n"
                + "  const MAX_TOUCH = " + maxTouchPoints + ";\n"
                + "\n"
                + "  // ---- 时区（名称与偏移自洽） ----\n"
                + "  try {\n"
                + "    const _dtf = Intl.DateTimeFormat.prototype.resolvedOptions;\n"
                + "    Intl.DateTimeFormat.prototype.resolvedOptions = function() {\n"
                + "      const opts = _dtf.call(this);\n"
                + "      try { opts.timeZone = TZ; } catch (e) {}\n"
                + "      return opts;\n"
                + "    };\n"
                + "    Date.prototype.getTimezoneOffset = function() { return -TZ_OFFSET_MIN; };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // ---- 地理位置（mock geolocation） ----\n"
                + "  try {\n"
                + "    if (navigator.geolocation) {\n"
                + "      Object.defineProperty(navigator, 'geolocation', {\n"
                + "        get: () => ({\n"
                + "          getCurrentPosition: (success) => {\n"
                + "            const pos = { coords: { latitude: LAT, longitude: LNG, accuracy: 20, altitude: null, altitudeAccuracy: null, heading: null, speed: null }, timestamp: Date.now() };\n"
                + "            setTimeout(() => success && success(pos), 0);\n"
                + "          },\n"
                + "          watchPosition: (success) => {\n"
                + "            const pos = { coords: { latitude: LAT, longitude: LNG, accuracy: 20 }, timestamp: Date.now() };\n"
                + "            setTimeout(() => success && success(pos), 0);\n"
                + "            return Math.floor(Math.random() * 1e9);\n"
                + "          },\n"
                + "          clearWatch: () => {}\n"
                + "        })\n"
                + "      });\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // ---- 字体枚举（document.fonts.check 恒真） ----\n"
                + "  try {\n"
                + "    if (document.fonts && typeof document.fonts.check === 'function') {\n"
                + "      document.fonts.check = () => true;\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // ---- 音频指纹噪声（AnalyserNode 频率数据注入稳定偏移） ----\n"
                + "  try {\n"
                + "    const _getFloat = AnalyserNode.prototype.getFloatFrequencyData;\n"
                + "    AnalyserNode.prototype.getFloatFrequencyData = function(arr) {\n"
                + "      _getFloat.call(this, arr);\n"
                + "      const noise = " + (audioNoise % 3) + ";\n"
                + "      if (arr && arr.length > 4) {\n"
                + "        for (let i = 0; i < arr.length; i += 16) { arr[i] = arr[i] + (noise - 1) * 0.5; }\n"
                + "      }\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // ---- 设备参数 ----\n"
                + "  try { Object.defineProperty(window, 'devicePixelRatio', { get: () => DPR }); } catch (e) {}\n"
                + "  try { Object.defineProperty(navigator, 'maxTouchPoints', { get: () => MAX_TOUCH }); } catch (e) {}\n"
                + "  try { Object.defineProperty(navigator, 'vendor', { get: () => 'Google Inc.' }); } catch (e) {}\n"
                + "\n"
                + "  // ---- 语音合成 voices（规避枚举指纹） ----\n"
                + "  try {\n"
                + "    const voices = [\n"
                + "      { name: 'Google 美国英语', lang: 'en-US', localService: true, default: false, voiceURI: 'Google US English' },\n"
                + "      { name: 'Google 普通话（中国大陆）', lang: 'zh-CN', localService: true, default: true, voiceURI: 'Google 普通话' },\n"
                + "      { name: 'Ting-Ting', lang: 'zh-CN', localService: true, default: false, voiceURI: 'Ting-Ting' },\n"
                + "      { name: 'Google 日本語', lang: 'ja-JP', localService: true, default: false, voiceURI: 'Google 日本語' }\n"
                + "    ];\n"
                + "    const list = voices.map(v => ({ name: v.name, lang: v.lang, localService: v.localService, default: v.default, voiceURI: v.voiceURI, constructor: { name: 'SpeechSynthesisVoice' } }));\n"
                + "    if (typeof speechSynthesis !== 'undefined' && speechSynthesis.getVoices) {\n"
                + "      speechSynthesis.getVoices = () => list;\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // ---- 指纹覆盖参数（可选） ----\n"
                + "  try {\n"
                + "    const O = " + overrideJson(override) + ";\n"
                + "    if (O) {\n"
                + "      if (O.webglVendor || O.webglRenderer) {\n"
                + "        const _gp = WebGLRenderingContext.prototype.getParameter;\n"
                + "        WebGLRenderingContext.prototype.getParameter = function(p) {\n"
                + "          if (p === 37445 && O.webglVendor) return O.webglVendor;\n"
                + "          if (p === 37446 && O.webglRenderer) return O.webglRenderer;\n"
                + "          return _gp.call(this, p);\n"
                + "        };\n"
                + "      }\n"
                + "      if (O.screenWidth && O.screenHeight) {\n"
                + "        Object.defineProperty(screen, 'width', { get: () => O.screenWidth });\n"
                + "        Object.defineProperty(screen, 'height', { get: () => O.screenHeight });\n"
                + "        Object.defineProperty(screen, 'availWidth', { get: () => O.screenWidth });\n"
                + "        Object.defineProperty(screen, 'availHeight', { get: () => O.screenHeight - 40 });\n"
                + "      }\n"
                + "      if (O.hardwareConcurrency) {\n"
                + "        Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => Number(O.hardwareConcurrency) });\n"
                + "      }\n"
                + "      if (O.deviceMemory) {\n"
                + "        Object.defineProperty(navigator, 'deviceMemory', { get: () => Number(O.deviceMemory) });\n"
                + "      }\n"
                + "      if (O.platform) {\n"
                + "        Object.defineProperty(navigator, 'platform', { get: () => O.platform });\n"
                + "      }\n"
                + "      if (O.languages) {\n"
                + "        Object.defineProperty(navigator, 'languages', { get: () => O.languages.split(',') });\n"
                + "      }\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "})();\n";
    }

    /** 覆盖参数 → JSON 字面量（null 时输出 null）。 */
    private static String overrideJson(ChromeProfile.FingerprintOverride override) {
        if (override == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        first = appendField(sb, first, "webglVendor", quote(override.getWebglVendor()));
        first = appendField(sb, first, "webglRenderer", quote(override.getWebglRenderer()));
        first = appendField(sb, first, "canvasNoisePattern", quote(override.getCanvasNoisePattern()));
        first = appendField(sb, first, "screenWidth", override.getScreenWidth());
        first = appendField(sb, first, "screenHeight", override.getScreenHeight());
        first = appendField(sb, first, "colorDepth", override.getColorDepth());
        first = appendField(sb, first, "pixelDepth", override.getPixelDepth());
        first = appendField(sb, first, "languages", quote(override.getLanguages()));
        first = appendField(sb, first, "platform", quote(override.getPlatform()));
        first = appendField(sb, first, "hardwareConcurrency", quote(override.getHardwareConcurrency()));
        appendField(sb, first, "deviceMemory", quote(override.getDeviceMemory()));
        return sb.append("}").toString();
    }

    private static String quote(String s) {
        if (s == null) {
            return null;
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean appendField(StringBuilder sb, boolean first, String key, Object value) {
        if (value == null) {
            return first;
        }
        if (!first) {
            sb.append(',');
        }
        sb.append('"').append(key).append("\":").append(value);
        return false;
    }
}
