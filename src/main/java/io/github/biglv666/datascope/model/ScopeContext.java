package io.github.biglv666.datascope.model;

import java.util.Collections;
import java.util.Set;

/**
 * 当前请求的登录上下文快照，供 {@link io.github.biglv666.datascope.spi.ScopeResolver} 决策用。
 * <p>由组件构造（默认经 auth-kit 桥接取登录态与角色），ScopeResolver 无需感知具体认证来源。</p>
 */
public class ScopeContext {

    private final String userId;
    private final Set<String> roles;

    public ScopeContext(String userId, Set<String> roles) {
        this.userId = userId;
        this.roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
    }

    /** 当前登录用户 id；未登录场景由组件先行抛出，正常到达 ScopeResolver 时非 null */
    public String getUserId() {
        return userId;
    }

    /** 当前用户角色集合（可能为空集，不保证有角色体系） */
    public Set<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
