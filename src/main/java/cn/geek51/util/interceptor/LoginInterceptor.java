package cn.geek51.util.interceptor;

import cn.geek51.util.UserContext;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 登录检查：页面请求未登录时重定向；API 请求返回 401 JSON，避免 SPA 收到 HTML 后误报「请求失败」。
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (UserContext.getCurrentUser() != null) {
            return true;
        }

        if (isApiRequest(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或会话已过期\",\"data\":null}");
            return false;
        }

        response.sendRedirect("/login");
        return false;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        if (uri.contains("/api/") || uri.startsWith("/auths") || uri.startsWith("/device/api")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String xhr = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(xhr);
    }
}
