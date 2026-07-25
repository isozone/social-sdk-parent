package cn.net.rjnetwork.xianyu.manager.miniprogram.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 将 /api/mini/{路由} 透明改写为 /api/{路由}，使现有 Controller 零改动即可被小程序调用。
 * <p>
 * 设计决策：
 * - 在 SecurityFilterChain 之前执行（MIN_VALUE），让 Spring Security 匹配重写后的路径
 * - 对 /api/mini/auth/login 等公开端点，剥离后变成 /api/auth/login → 命中 /api/auth/** permitAll
 * - 对其他端点，剥离后变成 /api/xxx → 命中 /api/** authenticated
 * - 不在安全上下文中单独放通 /api/mini/**，避免规则膨胀
 * </p>
 */
@Component
@Order(Integer.MIN_VALUE)
public class MiniProgramPrefixStripFilter implements Filter {

    private static final String MINI_PREFIX = "/api/mini/";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpReq)) {
            chain.doFilter(request, response);
            return;
        }
        String uri = httpReq.getRequestURI();
        if (!uri.startsWith(MINI_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        // /api/mini/auth/login → /api/auth/login
        // /api/mini/monitor/dashboard → /api/monitor/dashboard
        String rewritten = "/api" + uri.substring("/api/mini".length());
        chain.doFilter(new MiniProgramRequestWrapper(httpReq, rewritten), response);
    }

    /** 重写 servletPath / requestURI / contextPath，保留其他请求信息不变。 */
    private static final class MiniProgramRequestWrapper extends HttpServletRequestWrapper {
        private final String targetUri;

        MiniProgramRequestWrapper(HttpServletRequest original, String targetUri) {
            super(original);
            this.targetUri = targetUri;
        }

        @Override
        public String getRequestURI() { return targetUri; }

        @Override
        public String getServletPath() { return targetUri; }

        @Override
        public String getPathInfo() { return null; }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = super.getRequestURL();
            if (url != null) {
                int idx = url.indexOf(super.getRequestURI());
                if (idx >= 0) {
                    url.replace(idx, idx + super.getRequestURI().length(), targetUri);
                }
            }
            return url;
        }
    }
}
