package io.github.biglv666.datascope.itest;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.biglv666.datascope.model.ScopeContext;
import io.github.biglv666.datascope.model.ScopePolicy;
import io.github.biglv666.datascope.spi.ScopeContextProvider;
import io.github.biglv666.datascope.spi.ScopeResolver;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Set;
import java.util.function.Supplier;

/**
 * data-scope 集成测试应用：H2 + MyBatis-Plus + 分页插件共存。
 * <p>登录态与范围决策走可变 Holder，测试用例随时切换场景。
 * auth-kit 在测试类路径上但被排除自动装配（桥接Bean 由测试自供，避免依赖 auth-kit 登录态）。</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = io.github.biglv666.authkit.config.AuthKitAutoConfiguration.class)
@MapperScan("io.github.biglv666.datascope.itest")
public class DataScopeTestApp {

    /** 测试场景控制器：各用例 beforeEach 设置当前用户与范围决策 */
    public static final ScopeHolder HOLDER = new ScopeHolder();

    public static class ScopeHolder {
        public String userId = "10001";
        public Set<String> roles = Set.of("staff");
        public Supplier<ScopePolicy> policy = () -> ScopePolicy.self("10001");
    }

    @Bean
    public ScopeResolver testScopeResolver() {
        return context -> HOLDER.policy.get();
    }

    @Bean
    public ScopeContextProvider testScopeContextProvider() {
        return () -> new ScopeContext(HOLDER.userId, HOLDER.roles);
    }

    /** 业务自配分页插件：验证数据权限拦截器被头插且共存正确 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
