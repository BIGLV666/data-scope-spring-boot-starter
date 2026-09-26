package io.github.biglv666.datascope.config;

import io.github.biglv666.authkit.AuthKit;
import io.github.biglv666.authkit.exception.NotLoginException;
import io.github.biglv666.authkit.exception.NotLoginReason;
import io.github.biglv666.authkit.spi.PermissionProvider;
import io.github.biglv666.datascope.model.ScopeContext;
import io.github.biglv666.datascope.spi.ScopeContextProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * auth-kit 桥接（类路径存在 auth-kit 时自动装配）：默认的登录上下文来源。
 * <p>userId 取自 AuthKit 登录态；角色取自 PermissionProvider（未配置则空集，
 * 由业务方 ScopeResolver 自行决定无角色时的范围语义）。
 * 未登录时抛 auth-kit 的 {@link NotLoginException}，保持 401 异常语义，
 * 由现有异常处理器统一转响应。</p>
 * <p>业务方可注册自己的 {@link ScopeContextProvider} Bean 全量替换。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(AuthKit.class)
class AuthKitScopeBridgeConfig {

    @Bean
    @ConditionalOnMissingBean(ScopeContextProvider.class)
    public ScopeContextProvider authKitScopeContextProvider(ObjectProvider<PermissionProvider> permissionProvider) {
        return () -> {
            if (!AuthKit.isLogin()) {
                // 与拦截链 401 语义对齐：匿名请求不给数据权限决策机会
                throw new NotLoginException(NotLoginReason.NO_TOKEN);
            }
            String userId = AuthKit.getLoginId();
            PermissionProvider provider = permissionProvider.getIfAvailable();
            Set<String> roles = provider == null ? Set.of() : provider.getRoles(userId);
            return new ScopeContext(userId, roles);
        };
    }
}
