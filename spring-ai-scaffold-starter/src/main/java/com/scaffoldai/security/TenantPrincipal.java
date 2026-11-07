package com.scaffoldai.security;

import java.util.Set;

/**
 * 调用方身份：租户 + 用户 + 角色，贯穿整个请求链路（记忆隔离、检索隔离、审计）。
 */
public record TenantPrincipal(String tenantId, String userId, Set<String> roles) {

    public TenantPrincipal {
        roles = roles == null || roles.isEmpty() ? Set.of("user") : Set.copyOf(roles);
    }

    public static TenantPrincipal of(String tenantId, String userId, Set<String> roles) {
        return new TenantPrincipal(tenantId, userId, roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
