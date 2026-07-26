package cn.net.rjnetwork.xianyu.captcha.service;

/**
 * 滑块反检测注入脚本。
 * <p>脚本只做同账号稳定、跨账号隔离的低风险指纹补齐：隐藏 webdriver、补齐常见 Chrome 对象、
 * 为 canvas/WebGL/screen/hardware 信息注入 seed 派生噪声；不再伪造 event.isTrusted，避免暴露异常 monkey patch。</p>
 */
public final class SliderAntiDetect {

    /** 默认 seed（旧版全局兜底）。 */
    public static final long DEFAULT_SEED = 0L;

    private SliderAntiDetect() {}

    /** 完整的反检测 JS init script（无账号 seed 的兼容兜底）。 */
    public static final String INIT_SCRIPT = buildScript(DEFAULT_SEED);

    /**
     * 根据账号 seed 生成反检测 JS 脚本（per-account 指纹隔离）。
     * <p>脚本会从真实 UA 判断 OS，避免 Windows UA + macOS platform/WebGL 的混合指纹。</p>
     */
    public static String buildScript(long seed) {
        long canvasNoise = deriveNoise(seed, "canvas");
        long webglNoise = deriveNoise(seed, "webgl");
        int screenW = (int) (deriveNoise(seed, "screenW") % 200 + 1280);
        int screenH = (int) (deriveNoise(seed, "screenH") % 200 + 720);
        long hwConcurrency = (deriveNoise(seed, "hw") % 4) + 4;
        long deviceMemory = (long) Math.pow(2, (deriveNoise(seed, "mem") % 3) + 2);
        String macRenderer = pickMacRenderer(webglNoise);
        String winRenderer = pickWindowsRenderer(webglNoise);
        String linuxRenderer = pickLinuxRenderer(webglNoise);

        return ""
                + "// ====== Xianyu Slider Anti-Detect Init Script (seed=" + seed + ") ======\n"
                + "(() => {\n"
                + "  'use strict';\n"
                + "  const rawUA = String(navigator.userAgent || '');\n"
                + "  const sanitizedUA = rawUA.replace(/HeadlessChrome/gi, 'Chrome').replace(/Headless/gi, '');\n"
                + "  const isMac = /Macintosh|Mac OS X/i.test(sanitizedUA);\n"
                + "  const isWin = /Windows NT|Win64|Win32/i.test(sanitizedUA);\n"
                + "  const isLinux = !isMac && !isWin && /Linux/i.test(sanitizedUA);\n"
                + "  const platformName = isWin ? 'Windows' : (isMac ? 'macOS' : (isLinux ? 'Linux' : (navigator.userAgentData && navigator.userAgentData.platform) || ''));\n"
                + "  const navigatorPlatform = isWin ? 'Win32' : (isMac ? 'MacIntel' : (isLinux ? 'Linux x86_64' : (navigator.platform || '')));\n"
                + "  const webglRenderer = isWin ? '" + winRenderer + "' : (isMac ? '" + macRenderer + "' : '" + linuxRenderer + "');\n"
                + "\n"
                + "  try { Object.defineProperty(navigator, 'webdriver', { get: () => false }); delete navigator.__proto__.webdriver; } catch (e) {}\n"
                + "  try { Object.defineProperty(navigator, 'platform', { get: () => navigatorPlatform }); } catch (e) {}\n"
                + "\n"
                + "  try {\n"
                + "    window.chrome = window.chrome || {};\n"
                + "    window.chrome.runtime = window.chrome.runtime || {};\n"
                + "    window.chrome.app = window.chrome.app || {};\n"
                + "    window.chrome.csi = window.chrome.csi || function() { return {}; };\n"
                + "    window.chrome.loadTimes = window.chrome.loadTimes || function() { return {}; };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  try {\n"
                + "    const plugins = [\n"
                + "      { name: 'PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format', length: 1 },\n"
                + "      { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '', length: 1 },\n"
                + "      { name: 'Chromium PDF Viewer', filename: 'internal-pdf-viewer', description: '', length: 1 }\n"
                + "    ];\n"
                + "    plugins.item = (i) => plugins[i] || null; plugins.namedItem = (name) => plugins.find(p => p.name === name) || null; plugins.refresh = () => {};\n"
                + "    Object.defineProperty(navigator, 'plugins', { get: () => Object.setPrototypeOf(plugins, PluginArray.prototype) });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  try { Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en-US', 'en'] }); } catch (e) {}\n"
                + "\n"
                + "  try {\n"
                + "    const _toDataURL = HTMLCanvasElement.prototype.toDataURL;\n"
                + "    HTMLCanvasElement.prototype.toDataURL = function(type) {\n"
                + "      const ctx = this.getContext('2d');\n"
                + "      if (ctx && this.width > 10 && this.height > 10) {\n"
                + "        const imageData = ctx.getImageData(0, 0, this.width, this.height);\n"
                + "        if (imageData.data.length > 3) { const noise = " + canvasNoise + "; imageData.data[noise % imageData.data.length] ^= 1; imageData.data[(noise + 37) % imageData.data.length] ^= 1; }\n"
                + "        ctx.putImageData(imageData, 0, 0);\n"
                + "      }\n"
                + "      return _toDataURL.apply(this, arguments);\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  try {\n"
                + "    const _getParameter = WebGLRenderingContext.prototype.getParameter;\n"
                + "    WebGLRenderingContext.prototype.getParameter = function(parameter) {\n"
                + "      if (parameter === 37445) return 'Google Inc.';\n"
                + "      if (parameter === 37446) return webglRenderer;\n"
                + "      return _getParameter.call(this, parameter);\n"
                + "    };\n"
                + "    if (window.WebGL2RenderingContext) {\n"
                + "      const _getParameter2 = WebGL2RenderingContext.prototype.getParameter;\n"
                + "      WebGL2RenderingContext.prototype.getParameter = function(parameter) {\n"
                + "        if (parameter === 37445) return 'Google Inc.';\n"
                + "        if (parameter === 37446) return webglRenderer;\n"
                + "        return _getParameter2.call(this, parameter);\n"
                + "      };\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  try {\n"
                + "    const _query = window.navigator.permissions ? window.navigator.permissions.query : null;\n"
                + "    if (_query) { window.navigator.permissions.query = function(parameters) { return parameters && parameters.name === 'notifications' ? Promise.resolve({ state: Notification.permission, onchange: null }) : _query.call(this, parameters); }; }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  try {\n"
                + "    if (rawUA && rawUA !== sanitizedUA) Object.defineProperty(navigator, 'userAgent', { get: () => sanitizedUA });\n"
                + "    if (navigator.userAgentData) {\n"
                + "      const uaData = navigator.userAgentData;\n"
                + "      Object.defineProperty(navigator, 'userAgentData', { get: () => ({\n"
                + "        brands: (uaData.brands || []).map(b => ({ brand: String(b.brand || '').replace(/Headless/gi, ''), version: String(b.version || '') })),\n"
                + "        mobile: !!uaData.mobile,\n"
                + "        platform: platformName || uaData.platform || '',\n"
                + "        getHighEntropyValues: async (hints) => {\n"
                + "          const values = typeof uaData.getHighEntropyValues === 'function' ? await uaData.getHighEntropyValues(hints) : {};\n"
                + "          if (values && typeof values === 'object') { values.brands = (values.brands || []).map(b => ({ brand: String(b.brand || '').replace(/Headless/gi, ''), version: String(b.version || '') })); values.platform = platformName || values.platform || ''; }\n"
                + "          return values;\n"
                + "        }\n"
                + "      }) });\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  try {\n"
                + "    Object.defineProperty(screen, 'width', { get: () => " + screenW + " });\n"
                + "    Object.defineProperty(screen, 'height', { get: () => " + screenH + " });\n"
                + "    Object.defineProperty(screen, 'availWidth', { get: () => " + screenW + " });\n"
                + "    Object.defineProperty(screen, 'availHeight', { get: () => " + (screenH - 40) + " });\n"
                + "    Object.defineProperty(screen, 'colorDepth', { get: () => 24 });\n"
                + "    Object.defineProperty(screen, 'pixelDepth', { get: () => 24 });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  try { if (navigator.connection) Object.defineProperty(navigator.connection, 'rtt', { get: () => 50 }); } catch (e) {}\n"
                + "  try { Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => " + hwConcurrency + " }); Object.defineProperty(navigator, 'deviceMemory', { get: () => " + deviceMemory + " }); } catch (e) {}\n"
                + "})();\n";
    }

    /** 从 seed + label 派生长噪声值（SHA-256 派生）。 */
    public static long deriveNoise(long seed, String label) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((seed + ":" + label).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                value = (value << 8) | (hash[i] & 0xFFL);
            }
            return value == Long.MIN_VALUE ? 0L : Math.abs(value);
        } catch (java.security.NoSuchAlgorithmException e) {
            long value = seed * 31L + label.hashCode();
            return value == Long.MIN_VALUE ? 0L : Math.abs(value);
        }
    }

    public static String pickVendor(long noise) {
        return "Google Inc.";
    }

    public static String pickRenderer(long noise) {
        return pickWindowsRenderer(noise);
    }

    public static String pickWindowsRenderer(long noise) {
        String[] renderers = {
                "ANGLE (Intel, Intel(R) UHD Graphics 620 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (NVIDIA, NVIDIA GeForce GTX 1650 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (AMD, AMD Radeon RX 580 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)"
        };
        return renderers[(int) (noise % renderers.length)];
    }

    public static String pickMacRenderer(long noise) {
        String[] renderers = {
                "ANGLE (Apple, ANGLE Metal Renderer: Apple M1, Unspecified Version)",
                "ANGLE (Apple, ANGLE Metal Renderer: Apple M2, Unspecified Version)",
                "ANGLE (Intel Inc., Intel(R) Iris(TM) Plus Graphics OpenGL Engine, OpenGL 4.1)",
                "ANGLE (AMD, AMD Radeon Pro 5500M OpenGL Engine, OpenGL 4.1)"
        };
        return renderers[(int) (noise % renderers.length)];
    }

    public static String pickLinuxRenderer(long noise) {
        String[] renderers = {
                "ANGLE (Intel, Mesa Intel(R) UHD Graphics 620, OpenGL 4.6)",
                "ANGLE (AMD, AMD Radeon Graphics (RADV), OpenGL 4.6)",
                "ANGLE (NVIDIA, NVIDIA GeForce GTX 1650/PCIe/SSE2, OpenGL 4.6)"
        };
        return renderers[(int) (noise % renderers.length)];
    }

    /** 完整的反检测 JS init script — 保留为 buildScript(DEFAULT_SEED) 的别名。 */
    public static final String INIT_SCRIPT_INLINE = INIT_SCRIPT;

    /**
     * 默认桌面模式启动参数：只保留必要、相对常见的参数；Docker/headless 专用参数由 ChromeSession 按 headless 模式补充。
     */
    public static final String[] LAUNCH_ARGS = {
            "--disable-blink-features=AutomationControlled",
            "--no-first-run",
            "--no-default-browser-check",
            "--force-color-profile=srgb",
            "--lang=zh-CN"
    };
}
