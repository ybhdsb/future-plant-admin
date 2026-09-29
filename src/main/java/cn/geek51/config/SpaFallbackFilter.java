package cn.geek51.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 未命中静态文件的 /app/* history 路由，统一回退到 SPA index.html。
 * 避免刷新或直链 /app/devices 等时被登录拦截器当成后端页踢走。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SpaFallbackFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        if (!"GET".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String uri = req.getRequestURI();
        String ctx = req.getContextPath() == null ? "" : req.getContextPath();
        if (ctx.length() > 0 && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.isEmpty()) {
            uri = "/";
        }

        if (shouldForwardToSpa(uri)) {
            req.getRequestDispatcher("/app/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean shouldForwardToSpa(String uri) {
        if ("/app".equals(uri) || "/app/".equals(uri)) {
            return true;
        }
        if (!uri.startsWith("/app/")) {
            return false;
        }
        // 静态资源：/app/assets/**、带扩展名的文件
        if (uri.startsWith("/app/assets/") || uri.startsWith("/app/favicon")) {
            return false;
        }
        int lastSlash = uri.lastIndexOf('/');
        String last = lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
        if (last.contains(".")) {
            return false;
        }
        return true;
    }
}
