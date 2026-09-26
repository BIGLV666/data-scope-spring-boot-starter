package io.github.biglv666.datascope.spi;

import io.github.biglv666.datascope.model.ScopeContext;

/**
 * 登录上下文来源 SPI：组件用它构造 {@link ScopeContext}。
 * <p>默认实现为 auth-kit 桥接（类路径存在 auth-kit 时自动装配，取 AuthKit 登录态
 * 与 PermissionProvider 角色）；业务方可注册自己的 Bean 全量替换
 * （如接入其他认证组件、追加租户信息等）。</p>
 */
@FunctionalInterface
public interface ScopeContextProvider {

    /**
     * 构造当前请求的登录上下文。
     *
     * @return 当前上下文；未登录时应抛出认证异常（如 auth-kit 的 NotLoginException），
     *         让 401 语义保持一致——数据权限拦截器绝不为匿名请求返回数据
     */
    ScopeContext provide();
}
