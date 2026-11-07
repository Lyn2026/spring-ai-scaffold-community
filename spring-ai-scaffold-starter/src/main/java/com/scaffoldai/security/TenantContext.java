package com.scaffoldai.security;

import com.scaffoldai.common.AiException;

/**
 * 请求级身份上下文。由 TenantFilter 写入并在请求结束时清理。
 */
public final class TenantContext {

    private static final ThreadLocal<TenantPrincipal> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantPrincipal principal) {
        HOLDER.set(principal);
    }

    public static TenantPrincipal current() {
        return HOLDER.get();
    }

    public static TenantPrincipal require() {
        TenantPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw AiException.unauthorized("缺少调用身份，请通过 Authorization 或 X-Tenant-Id 传入");
        }
        return principal;
    }

    public static String tenantId() {
        return require().tenantId();
    }

    public static String userId() {
        return require().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
