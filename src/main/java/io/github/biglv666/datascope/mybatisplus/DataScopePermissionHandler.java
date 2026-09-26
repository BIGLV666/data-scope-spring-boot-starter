package io.github.biglv666.datascope.mybatisplus;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import io.github.biglv666.datascope.annotation.DataScope;
import io.github.biglv666.datascope.exception.DataScopeException;
import io.github.biglv666.datascope.model.ScopeContext;
import io.github.biglv666.datascope.model.ScopePolicy;
import io.github.biglv666.datascope.spi.ScopeContextProvider;
import io.github.biglv666.datascope.spi.ScopeResolver;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限条件构造器（对接 MyBatis-Plus {@link DataPermissionInterceptor}）。
 * <p>每个查询执行前回调：statement 无 @DataScope 注解则原样放行；
 * 有注解则取 {@link ScopePolicy} 决策，用 JSqlParser 表达式把过滤条件 AND 进 WHERE。
 * 过滤值经 {@link StringValue}/{@code LongValue} 参数化构造，无注入面。</p>
 * <p><b>fail-closed</b>：ScopeResolver 缺失 / 决策返回 null / 范围与注解列声明不匹配
 * （如决策 DEPT_IN 但未声明 deptColumn）时抛 {@link DataScopeException}，
 * 绝不静默放行全量数据。</p>
 */
public class DataScopePermissionHandler implements DataPermissionHandler {

    private final ScopeResolver resolver;
    private final ScopeContextProvider contextProvider;
    private final DataScopeAnnotationParser parser;

    public DataScopePermissionHandler(ScopeResolver resolver, ScopeContextProvider contextProvider) {
        this.resolver = resolver;
        this.contextProvider = contextProvider;
        this.parser = new DataScopeAnnotationParser();
    }

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        DataScope annotation = parser.find(mappedStatementId);
        if (annotation == null) {
            return where;
        }
        ScopePolicy policy = resolvePolicy(mappedStatementId);
        return switch (policy.getType()) {
            case ALL -> where;
            case SELF -> and(where, selfCondition(annotation, policy, mappedStatementId));
            case DEPT_IN -> and(where, deptInCondition(annotation, policy, mappedStatementId));
        };
    }

    private ScopePolicy resolvePolicy(String mappedStatementId) {
        if (resolver == null) {
            throw new DataScopeException("statement " + mappedStatementId
                    + " 带 @DataScope 但未注册 ScopeResolver Bean，数据权限无法执行（fail-closed 拒绝放行）");
        }
        ScopeContext context = contextProvider.provide();
        ScopePolicy policy = resolver.resolve(context);
        if (policy == null) {
            throw new DataScopeException("ScopeResolver 对用户 " + context.getUserId()
                    + " 返回 null，数据权限无法执行（fail-closed 拒绝放行）；豁免请返回 ScopePolicy.all()");
        }
        return policy;
    }

    private Expression selfCondition(DataScope annotation, ScopePolicy policy, String mappedStatementId) {
        String column = requireColumn(annotation.selfColumn(), "selfColumn", "SELF", mappedStatementId);
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(column));
        eq.setRightExpression(new StringValue(policy.getUserId()));
        return eq;
    }

    private Expression deptInCondition(DataScope annotation, ScopePolicy policy, String mappedStatementId) {
        String column = requireColumn(annotation.deptColumn(), "deptColumn", "DEPT_IN", mappedStatementId);
        List<Expression> values = new ArrayList<>();
        for (String deptId : policy.getDeptIds()) {
            values.add(new StringValue(deptId));
        }
        InExpression in = new InExpression();
        in.setLeftExpression(new Column(column));
        in.setRightItemsList(new ExpressionList(values));
        return in;
    }

    private static String requireColumn(String column, String annotationAttribute,
                                        String policyType, String mappedStatementId) {
        if (column == null || column.isBlank()) {
            throw new DataScopeException("statement " + mappedStatementId
                    + " 的 @DataScope 未声明 " + annotationAttribute
                    + "，无法执行 " + policyType + " 范围过滤（fail-closed 拒绝放行）");
        }
        return column;
    }

    private static Expression and(Expression where, Expression condition) {
        return where == null ? condition : new AndExpression(where, condition);
    }
}
