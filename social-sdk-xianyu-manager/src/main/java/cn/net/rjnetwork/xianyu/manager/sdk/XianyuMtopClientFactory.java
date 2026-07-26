package cn.net.rjnetwork.xianyu.manager.sdk;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyPoolManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 统一创建闲鱼 MTOP SDK Client。
 * <p>所有管理端业务都应通过该工厂创建 client，确保 MTOP HTTP 与 IM WSS 能读取同一个
 * ProxyPoolManager/accountId，并复用账号绑定代理，避免 Chrome / MTOP / IM 出口不一致。</p>
 */
@Component
public class XianyuMtopClientFactory {

    private final ProxyPoolManager proxyPoolManager;

    public XianyuMtopClientFactory(@Autowired(required = false) ProxyPoolManager proxyPoolManager) {
        this.proxyPoolManager = proxyPoolManager;
    }

    public XianyuMtopApiClient create(XianyuAccount account) {
        if (account == null) {
            return create(null, null, null);
        }
        return create(account.getCookieHeader(), account.getId(), account.getImCookieHeader());
    }

    public XianyuMtopApiClient create(String cookieHeader, Long accountId) {
        return create(cookieHeader, accountId, null);
    }

    public XianyuMtopApiClient create(String cookieHeader, Long accountId, String imCookieHeader) {
        XianyuMtopApiClient client = new XianyuMtopApiClient(cookieHeader, proxyPoolManager, accountId);
        if (imCookieHeader != null && !imCookieHeader.isBlank()) {
            client.setImCookieHeader(imCookieHeader);
        }
        return client;
    }
}
