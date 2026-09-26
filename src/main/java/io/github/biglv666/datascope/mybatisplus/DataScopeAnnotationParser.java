package io.github.biglv666.datascope.mybatisplus;

import io.github.biglv666.datascope.annotation.DataScope;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mappedStatementId → @DataScope 注解解析与缓存。
 * <p>statementId 形如 {@code com.x.CardMapper.selectPage}，按"接口全名 + 方法名"
 * 反射定位注解；每个 statement 仅首次反射，之后走 ConcurrentHashMap 缓存。
 * 未标注解的 statement 缓存空值，避免重复反射。</p>
 */
public class DataScopeAnnotationParser {

    private final Map<String, Optional<DataScope>> cache = new ConcurrentHashMap<>();

    /**
     * 查询 statement 对应的 @DataScope 注解。
     *
     * @return 注解；statement 无对应接口方法（纯 XML namespace）、方法未标注解时返回 null
     */
    public DataScope find(String mappedStatementId) {
        return cache.computeIfAbsent(mappedStatementId, DataScopeAnnotationParser::parse).orElse(null);
    }

    private static Optional<DataScope> parse(String mappedStatementId) {
        int idx = mappedStatementId.lastIndexOf('.');
        if (idx <= 0) {
            return Optional.empty();
        }
        String className = mappedStatementId.substring(0, idx);
        String methodName = mappedStatementId.substring(idx + 1);
        try {
            Class<?> mapper = Class.forName(className);
            for (Method method : mapper.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                    return Optional.of(method.getAnnotation(DataScope.class));
                }
            }
            return Optional.empty();
        } catch (ClassNotFoundException e) {
            // 纯 XML namespace（无对应 Mapper 接口）：注解语义只存在于接口方法上，跳过
            return Optional.empty();
        }
    }
}
