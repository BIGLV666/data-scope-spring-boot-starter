package io.github.biglv666.datascope.spi;

import io.github.biglv666.datascope.model.ScopeContext;
import io.github.biglv666.datascope.model.ScopePolicy;

/**
 * 数据范围决策 SPI（业务方唯一必配的 Bean）：决定当前用户查询数据时的过滤范围。
 * <p>组件 fail-closed：启用了 data-scope 而本 SPI 缺失时启动失败（fail-fast），
 * 运行期兜底同样拒绝放行——数据权限缺位时宁可报错，绝不静默返回全量数据。</p>
 * <p>角色语义归业务：admin 豁免、经理看本部门等判断全部在此实现，
 * 组件不理解任何具体角色。</p>
 */
@FunctionalInterface
public interface ScopeResolver {

    /**
     * 决策当前用户的过滤范围。
     *
     * @param context 登录上下文（userId + 角色，由组件从认证来源构造）
     * @return 过滤范围；返回 {@link ScopePolicy#all()} 表示豁免（SQL 不改写）
     */
    ScopePolicy resolve(ScopeContext context);
}
