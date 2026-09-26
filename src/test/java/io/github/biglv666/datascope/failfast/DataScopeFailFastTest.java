package io.github.biglv666.datascope.failfast;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.github.biglv666.datascope.itest.CardMapper;
import io.github.biglv666.datascope.model.ScopeContext;
import io.github.biglv666.datascope.model.ScopePolicy;
import io.github.biglv666.datascope.spi.ScopeContextProvider;
import io.github.biglv666.datascope.spi.ScopeResolver;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fail-fast 校验测试（对齐 auth-kit 的管理端 fail-fast 惯例）：
 * <ul>
 *   <li>写了 @DataScope 但 enabled=false → 启动失败（防"以为有数据权限"的静默失效漏洞）；</li>
 *   <li>enabled=true 但缺 ScopeResolver / ScopeContextProvider → 启动失败；</li>
 *   <li>配置齐全 → 正常启动；未使用注解的项目引入 starter 默认零影响。</li>
 * </ul>
 */
class DataScopeFailFastTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AnnotatedMapperConfig.class)
            .withConfiguration(AutoConfigurations.of(io.github.biglv666.datascope.config.DataScopeAutoConfiguration.class));

    @Test
    void annotationWithoutEnabledFailsFast() {
        runner.withPropertyValues("data-scope.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("@DataScope")
                            .hasStackTraceContaining("data-scope.enabled=true");
                });
    }

    @Test
    void enabledWithoutResolverFailsFast() {
        runner.withPropertyValues("data-scope.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("ScopeResolver Bean");
                });
    }

    @Test
    void enabledWithoutContextProviderFailsFast() {
        runner.withPropertyValues("data-scope.enabled=true")
                .withBean("resolver", ScopeResolver.class,
                        () -> context -> ScopePolicy.self("10001"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("ScopeContextProvider");
                });
    }

    @Test
    void fullyConfiguredStartsNormally() {
        runner.withPropertyValues("data-scope.enabled=true")
                .withBean("resolver", ScopeResolver.class,
                        () -> context -> ScopePolicy.self("10001"))
                .withBean("contextProvider", ScopeContextProvider.class,
                        () -> () -> new ScopeContext("10001", Set.of()))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void noAnnotationWithoutEnabledStartsNormally() {
        // 未用 @DataScope 的项目引入 starter 默认零影响
        ApplicationContextRunner plainRunner = new ApplicationContextRunner()
                .withUserConfiguration(PlainMapperConfig.class)
                .withConfiguration(AutoConfigurations.of(io.github.biglv666.datascope.config.DataScopeAutoConfiguration.class));

        plainRunner.withPropertyValues("data-scope.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /** 注册带 @DataScope 的 Mapper（触发守卫与装配两条路径） */
    @Configuration
    static class AnnotatedMapperConfig {

        @Bean
        public DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:failfast;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            return factoryBean.getObject();
        }

        @Bean
        public MapperFactoryBean<CardMapper> scopedMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<CardMapper> factoryBean = new MapperFactoryBean<>(CardMapper.class);
            factoryBean.setSqlSessionFactory(sqlSessionFactory);
            return factoryBean;
        }
    }

    /** 注册无任何 @DataScope 注解的 Mapper（验证默认零影响路径）——不复用带注解配置，防止泄漏 */
    @Configuration
    static class PlainMapperConfig {

        interface PlainMapper {
            @Select("SELECT 1")
            int ping();
        }

        @Bean
        public DataSource plainDataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:failfast-plain;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        public SqlSessionFactory plainSqlSessionFactory(DataSource plainDataSource) throws Exception {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(plainDataSource);
            return factoryBean.getObject();
        }

        @Bean
        public MapperFactoryBean<PlainMapper> plainMapper(SqlSessionFactory plainSqlSessionFactory) {
            MapperFactoryBean<PlainMapper> factoryBean = new MapperFactoryBean<>(PlainMapper.class);
            factoryBean.setSqlSessionFactory(plainSqlSessionFactory);
            return factoryBean;
        }
    }
}
