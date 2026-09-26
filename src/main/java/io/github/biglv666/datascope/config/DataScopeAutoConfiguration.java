package io.github.biglv666.datascope.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.github.biglv666.datascope.annotation.DataScope;
import io.github.biglv666.datascope.mybatisplus.DataScopePermissionHandler;
import io.github.biglv666.datascope.spi.ScopeContextProvider;
import io.github.biglv666.datascope.spi.ScopeResolver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * data-scope 自动装配：独立 starter，零 ORM 强依赖。
 * <p>装配规则（两条 fail-fast 都对齐 auth-kit 的 6.8 管理端惯例）：</p>
 * <ul>
 *   <li>{@code data-scope.enabled=true}（显式）：必须注册 {@link ScopeResolver} Bean，
 *       缺失则启动失败；拦截器注册进 {@link MybatisPlusInterceptor} 队首
 *       （必须先于分页插件，count 才能拿到过滤后的正确总数）；</li>
 *   <li>{@code data-scope.enabled=false}（默认）：不装配任何拦截器；但启动期扫描
 *       已注册 Mapper，发现 @DataScope 注解即启动失败——防止业务写了注解却被
 *       静默忽略，形成"以为有数据权限"的漏洞。</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(DataScopeProperties.class)
public class DataScopeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataScopeAutoConfiguration.class);

    /**
     * 未启用时的防呆守卫：类路径有 MyBatis-Plus 且业务 Mapper 带 @DataScope → 启动失败。
     * 无 MyBatis-Plus 时（其他 ORM 实现接入前的项目）无 Mapper 可扫，自然跳过。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "data-scope", name = "enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnClass(SqlSessionFactory.class)
    static class DisabledGuardConfig {

        @Bean
        public SmartInitializingSingleton dataScopeDisabledGuard(ObjectProvider<SqlSessionFactory> factories) {
            return () -> {
                for (SqlSessionFactory factory : factories) {
                    for (Class<?> mapper : factory.getConfiguration().getMapperRegistry().getMappers()) {
                        if (hasDataScopeAnnotation(mapper)) {
                            throw new IllegalStateException("Mapper [" + mapper.getName()
                                    + "] 存在 @DataScope 注解，但 data-scope.enabled=false——数据权限将静默失效，拒绝启动。"
                                    + "请设置 data-scope.enabled=true 并注册 ScopeResolver Bean");
                        }
                    }
                }
            };
        }
    }

    /**
     * 启用后的 MyBatis-Plus 装配：handler + 拦截器队首注册。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "data-scope", name = "enabled", havingValue = "true")
    @ConditionalOnClass(MybatisPlusInterceptor.class)
    static class MybatisPlusScopeConfig {

        /** 业务方必配 Bean：缺失即启动失败（fail-fast，与 OAuth2UserBinder 同一惯例） */
        @Bean
        public DataScopePermissionHandler dataScopePermissionHandler(
                ObjectProvider<ScopeResolver> resolverProvider,
                ObjectProvider<ScopeContextProvider> contextProvider) {
            ScopeResolver resolver = resolverProvider.getIfAvailable();
            if (resolver == null) {
                throw new IllegalStateException(
                        "data-scope.enabled=true 必须注册 ScopeResolver Bean（当前用户数据范围的决策逻辑）");
            }
            ScopeContextProvider provider = contextProvider.getIfAvailable();
            if (provider == null) {
                throw new IllegalStateException(
                        "data-scope.enabled=true 需要登录上下文来源：注册 ScopeContextProvider Bean"
                                + "（类路径含 auth-kit 时自动桥接，无需手动配置）");
            }
            return new DataScopePermissionHandler(resolver, provider);
        }

        /** 业务方没有 MybatisPlusInterceptor（没用分页等插件）时补一个，保证拦截链存在 */
        @Bean
        @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
        public MybatisPlusInterceptor mybatisPlusInterceptor() {
            return new MybatisPlusInterceptor();
        }

        /**
         * 把数据权限拦截器插到 InnerInterceptor 链队首：
         * 分页插件的 count 在 willDoQuery 阶段执行，数据权限若排在其后，count 统计的是未过滤总数。
         * MP 未提供头插公开 API，这里反射操作 interceptors 字段（3.5.x 稳定字段）。
         */
        @Bean
        public static InnerInterceptorHeadInjector innerInterceptorHeadInjector(
                ObjectProvider<DataScopePermissionHandler> handlerProvider) {
            return new InnerInterceptorHeadInjector(handlerProvider);
        }
    }

    /** BeanPostProcessor：static 注册，避免过早依赖其他 Bean；handler 到时才解析 */
    static class InnerInterceptorHeadInjector implements org.springframework.beans.factory.config.BeanPostProcessor {

        private final ObjectProvider<DataScopePermissionHandler> handlerProvider;

        InnerInterceptorHeadInjector(ObjectProvider<DataScopePermissionHandler> handlerProvider) {
            this.handlerProvider = handlerProvider;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof MybatisPlusInterceptor interceptor) {
                DataScopePermissionHandler handler = handlerProvider.getIfAvailable();
                if (handler == null) {
                    // handler 依赖 ScopeResolver，缺失时启动已在 dataScopePermissionHandler 处失败，此处防御
                    return bean;
                }
                insertFirst(interceptor, new DataPermissionInterceptor(handler));
                log.info("[data-scope] 数据权限拦截器已注册至 MybatisPlusInterceptor 队首");
            }
            return bean;
        }

        private static void insertFirst(MybatisPlusInterceptor interceptor, InnerInterceptor inner) {
            try {
                Field field = ReflectionUtils.findField(MybatisPlusInterceptor.class, "interceptors");
                if (field == null) {
                    throw new IllegalStateException("未找到 MybatisPlusInterceptor.interceptors 字段");
                }
                ReflectionUtils.makeAccessible(field);
                @SuppressWarnings("unchecked")
                List<InnerInterceptor> interceptors = (List<InnerInterceptor>) field.get(interceptor);
                interceptors.add(0, inner);
            } catch (IllegalAccessException | ClassCastException e) {
                log.warn("[data-scope] 队首插入失败，退化为追加到末尾（分页 count 可能包含未过滤数据）", e);
                interceptor.addInnerInterceptor(inner);
            }
        }
    }

    private static boolean hasDataScopeAnnotation(Class<?> mapper) {
        for (Method method : mapper.getMethods()) {
            if (method.isAnnotationPresent(DataScope.class)) {
                return true;
            }
        }
        return false;
    }
}
