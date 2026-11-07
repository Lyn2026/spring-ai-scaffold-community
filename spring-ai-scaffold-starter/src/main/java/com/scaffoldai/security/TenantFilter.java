package com.scaffoldai.security;

import com.scaffoldai.common.TextUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户解析过滤器（header 模式）。
 *
 * <p>从 X-Tenant-Id / X-User-Id / X-Roles 透传租户、用户与角色，写入 {@link TenantContext}，
 * 请求结束后清理，避免线程复用串租户。适用于内网部署或网关已做过统一鉴权的场景；
 * 需要按 API Key 鉴权、按角色做后台权限控制的能力属于企业版。</p>
 */
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private final String tenantHeader;
    private final String userHeader;
    private final String rolesHeader;
    private final String defaultTenant;
    private final String defaultUser;
    private final String defaultRoles;

    public TenantFilter(String tenantHeader, String userHeader, String rolesHeader,
                        String defaultTenant, String defaultUser, String defaultRoles) {
        this.tenantHeader = TextUtils.defaultIfBlank(tenantHeader, "X-Tenant-Id");
        this.userHeader = TextUtils.defaultIfBlank(userHeader, "X-User-Id");
        this.rolesHeader = TextUtils.defaultIfBlank(rolesHeader, "X-Roles");
        this.defaultTenant = TextUtils.defaultIfBlank(defaultTenant, "public");
        this.defaultUser = TextUtils.defaultIfBlank(defaultUser, "anonymous");
        this.defaultRoles = TextUtils.defaultIfBlank(defaultRoles, "user");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.set(resolve(request));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private TenantPrincipal resolve(HttpServletRequest request) {
        String tenantId = TextUtils.defaultIfBlank(request.getHeader(tenantHeader), defaultTenant);
        String userId = TextUtils.defaultIfBlank(request.getHeader(userHeader), defaultUser);
        Set<String> roles = parseRoles(request.getHeader(rolesHeader));
        if (roles.isEmpty()) {
            roles = parseRoles(defaultRoles);
        }
        if (log.isDebugEnabled()) {
            log.debug("租户上下文 tenant={} user={} roles={} uri={}", tenantId, userId, roles, request.getRequestURI());
        }
        return new TenantPrincipal(tenantId, userId, roles);
    }

    private Set<String> parseRoles(String raw) {
        if (!TextUtils.hasText(raw)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(TextUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
