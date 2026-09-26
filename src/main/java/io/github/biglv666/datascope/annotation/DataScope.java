package io.github.biglv666.datascope.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 行级数据权限注解：标注在 MyBatis-Plus Mapper 方法上，查询执行前自动追加过滤条件。
 * <p>过滤范围由业务方实现的 {@code ScopeResolver} Bean 决定（SELF / DEPT_IN / ALL），
 * 本注解只负责声明"数据归属在哪些列"，二者配合完成改写：</p>
 * <ul>
 *   <li>{@link ScopePolicy} 为 SELF → 追加 {@code selfColumn = 当前用户}</li>
 *   <li>{@link ScopePolicy} 为 DEPT_IN → 追加 {@code deptColumn IN (可见部门集合)}</li>
 *   <li>{@link ScopePolicy} 为 ALL（如 admin 豁免）→ 不改写 SQL</li>
 * </ul>
 * <p>安全约束：组件 fail-closed——方法带了本注解但组件未启用 / ScopeResolver 缺失 /
 * 范围与列声明不匹配时，一律抛 {@code DataScopeException}，绝不静默放行全量数据。</p>
 *
 * <p>列名写在本注解中（开发者手写常量），不经过外部输入，无注入面；
 * 过滤值由组件经 JSqlParser 参数化构造，同样无注入面。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 部门归属列名（可带表别名，如 {@code "u.dept_id"}）。
     * ScopeResolver 返回 DEPT_IN 时必填，缺失则运行期抛 DataScopeException。
     */
    String deptColumn() default "";

    /**
     * 创建人/归属人列名（可带表别名，如 {@code "o.create_by"}）。
     * ScopeResolver 返回 SELF 时必填，缺失则运行期抛 DataScopeException。
     */
    String selfColumn() default "";
}
