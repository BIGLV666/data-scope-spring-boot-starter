package io.github.biglv666.datascope.exception;

/**
 * 数据权限组件异常：fail-closed 语义的统一出口。
 * <p>典型场景：方法带 @DataScope 但 ScopeResolver 缺失、范围与列声明不匹配
 * （如决策为 DEPT_IN 但注解未声明 deptColumn）、登录上下文不可得。
 * 该异常表示"数据权限无法执行"，组件选择拒绝而不是放行。</p>
 */
public class DataScopeException extends RuntimeException {

    public DataScopeException(String message) {
        super(message);
    }

    public DataScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
