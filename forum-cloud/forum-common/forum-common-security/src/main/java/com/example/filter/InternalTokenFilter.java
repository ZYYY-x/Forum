package com.example.filter;

import com.example.entity.RestBean;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /internal/** 的简单内部令牌校验。
 *
 * <p>网关不路由 /internal/**；该过滤器用于防止服务端口被直接访问时泄露内部数据。
 * 生产环境仍应配合网络隔离/安全组。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTokenFilter extends OncePerRequestFilter {

    @Value("${forum.internal.token:}")
    private String expectedToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String actualToken = request.getHeader("X-Internal-Token");
        if (!StringUtils.hasText(expectedToken) || !expectedToken.equals(actualToken)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(RestBean.forbidden("内部接口禁止访问").asJsonString());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
