package cn.net.rjnetwork.xianyu.api;

/**
 * 闲鱼账号运行时基础指纹常量。
 * <p>同一账号运行链路（MTOP HTTP、IM WSS、Chrome/CDP）应尽量使用一致的 UA / Accept-Language。
 * 这里先统一 SDK HTTP/WSS 层；Chrome/CDP 层会根据真实 UA 推导 platform/WebGL，避免跨链路混合指纹。</p>
 */
public final class XianyuRuntimeFingerprint {

    private XianyuRuntimeFingerprint() {}

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";

    public static final String IM_USER_AGENT = USER_AGENT
            + " DingTalk(2.1.5) OS(Windows/10) Browser(Chrome/150.0.0.0) DingWeb/2.1.5 IMPaaS DingWeb/2.1.5";

    public static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7";
}
