package io.github.biglv666.datascope.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据过滤范围：{@link io.github.biglv666.datascope.spi.ScopeResolver} 的决策结果，
 * 组件据此改写 SQL。不可变对象，静态工厂构造。
 */
public final class ScopePolicy {

    /** 范围类型 */
    public enum Type { ALL, SELF, DEPT_IN }

    private final Type type;
    private final String userId;
    private final Set<String> deptIds;

    private ScopePolicy(Type type, String userId, Collection<String> deptIds) {
        this.type = type;
        this.userId = userId;
        this.deptIds = deptIds == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(deptIds));
    }

    /** 全量放行（admin 豁免等），SQL 不改写 */
    public static ScopePolicy all() {
        return new ScopePolicy(Type.ALL, null, null);
    }

    /** 只看自己：selfColumn = userId */
    public static ScopePolicy self(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("ScopePolicy.self(userId) 的 userId 不能为空");
        }
        return new ScopePolicy(Type.SELF, userId, null);
    }

    /** 数字型便捷重载 */
    public static ScopePolicy self(Long userId) {
        return self(String.valueOf(userId));
    }

    /** 只看指定部门集合：deptColumn IN (deptIds) */
    public static ScopePolicy deptIn(Collection<String> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            throw new IllegalArgumentException("ScopePolicy.deptIn(deptIds) 不能为空集合"
                    + "（用户无任何可见部门时应由业务方决定返回 self(...) 还是 all()）");
        }
        return new ScopePolicy(Type.DEPT_IN, null, deptIds);
    }

    /** 数字型便捷重载 */
    public static ScopePolicy deptInLongs(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            throw new IllegalArgumentException("ScopePolicy.deptInLongs(deptIds) 不能为空集合");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Long id : deptIds) {
            ids.add(String.valueOf(id));
        }
        return new ScopePolicy(Type.DEPT_IN, null, ids);
    }

    public Type getType() {
        return type;
    }

    /** SELF 范围的用户 id；其他类型返回 null */
    public String getUserId() {
        return userId;
    }

    /** DEPT_IN 范围的可见部门 id 集合；其他类型返回空集合 */
    public Set<String> getDeptIds() {
        return deptIds;
    }
}
