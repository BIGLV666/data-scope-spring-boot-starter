# data-scope-spring-boot-starter

轻量级**行级数据权限**组件：`@DataScope` 注解 + SQL 自动改写。同一个接口，销售只看自己
的订单、主管看本部门、admin 看全部——业务代码与 SQL 零改动。

与 [auth-kit](https://github.com/BIGLV666/auth-kit-spring-boot-starter)（认证鉴权）互补：
auth-kit 管"这个接口你能不能调"，data-scope 管"调通后你能看到哪几行"。

## 特性

- **注解即接入**：Mapper 方法标 `@DataScope`，查询执行前自动追加 WHERE 过滤条件
- **范围决策归业务**：组件不理解任何角色语义，业务方实现一个 `ScopeResolver` Bean 全权决策
- **fail-closed**：注解存在但组件未启用 / ScopeResolver 缺失 / 范围与列声明不匹配时，
  一律抛异常拒绝执行，绝不静默放行全量数据
- **fail-fast**：`data-scope.enabled=false` 时扫描 Mapper，发现 `@DataScope` 直接启动失败
  （防止"以为有数据权限"的静默失效漏洞）；enabled=true 但缺必配 Bean 同样启动失败
- **分页友好**：拦截器自动注册到 MyBatis-Plus 插件链队首，分页 count 拿到的是过滤后的总数
- **ORM 可插拔**：core 零 ORM 依赖，MyBatis-Plus 实现内置（3.5.x），JPA 实现按需扩展

## 快速开始

```xml
<dependency>
    <groupId>io.github.biglv666</groupId>
    <artifactId>data-scope-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
data-scope:
  enabled: true     # 显式开启；开启是强承诺，必须配套 ScopeResolver Bean
```

```java
// ① 唯一必配的 Bean：当前用户该看到什么范围（角色语义归业务）
@Bean
public ScopeResolver scopeResolver() {
    return ctx -> {
        if (ctx.hasRole("admin"))   return ScopePolicy.all();
        if (ctx.hasRole("manager")) return ScopePolicy.deptInLongs(deptService.selfAndChildren(ctx.getUserId()));
        return ScopePolicy.self(ctx.getUserId());
    };
}
```

```java
// ② Mapper 方法标注解：声明数据归属列
public interface OrderMapper extends BaseMapper<Order> {

    @DataScope(deptColumn = "dept_id", selfColumn = "create_by")
    @Select("SELECT * FROM t_order ORDER BY id")
    List<Order> selectScoped();
}
```

效果（拦截器改写，业务无感知）：

| 当前用户范围 | 实际执行的 SQL |
|---|---|
| `ScopePolicy.self("10001")` | `... WHERE (原有条件) AND create_by = '10001'` |
| `ScopePolicy.deptInLongs([101, 102])` | `... WHERE (原有条件) AND dept_id IN ('101', '102')` |
| `ScopePolicy.all()`（豁免） | SQL 不改写 |

## 三种范围（ScopePolicy）

| 工厂 | 语义 | 注解要求 |
|---|---|---|
| `ScopePolicy.all()` | 全量放行（SQL 不改写） | 无 |
| `ScopePolicy.self(userId)` | 只看自己 | `selfColumn` 必填 |
| `ScopePolicy.deptIn(ids)` / `deptInLongs(ids)` | 指定部门集合 | `deptColumn` 必填 |

范围与列声明不匹配（如决策 SELF 但方法只声明了 `deptColumn`）时抛异常，拒绝执行。

## 与 auth-kit 配合

类路径存在 auth-kit 时自动桥接（无需任何配置）：

- 当前用户：`AuthKit` 登录态（未登录抛 `NotLoginException` → 401，与拦截链语义一致）
- 角色：`PermissionProvider.getRoles(userId)`（未配置 PermissionProvider 则空集）

没有 auth-kit 也能用：注册自己的 `ScopeContextProvider` Bean，从任意认证来源构造
`ScopeContext(userId, roles)`。

## 工作原理

- MyBatis-Plus `DataPermissionInterceptor` + 自定义 Handler：每个查询按 statementId
  查 `@DataScope` 注解（首次反射后缓存），有注解才改写 SQL（JSqlParser 构造表达式，
  过滤值参数化，无注入面）
- 拦截器头插插件链：分页插件的 count 在后续阶段执行，天然拿到过滤后的总数
- 未登录 / 决策为 null / 缺 Bean 等一切异常态均为**拒绝**（fail-closed），不回退为全量

### 异常出口

拦截器内抛出的 `DataScopeException` 会被 MyBatis 统一包装为
`MyBatisSystemException`（MyBatis 全局行为），`getRootCause()` 即原异常。
无 web-common 时表现为 500；接入 web-common 后建议在全局异常处理器统一转业务错误码。

## 扩展点

| SPI | 职责 | 默认 |
|---|---|---|
| `ScopeResolver` | 范围决策（业务必配） | 无（缺失启动失败） |
| `ScopeContextProvider` | 登录上下文来源 | auth-kit 桥接（类路径有 auth-kit 时） |

ORM 实现接口预留：core 只输出抽象过滤条件与注解语义，MyBatis-Plus 之外的新实现
（如 JPA/Hibernate Filter）仅新增实现模块，core 不动。

## 已知边界

| 边界 | 说明 |
|---|---|
| 仅 Mapper 方法级注解 | 不支持类级/Service 级，保持精确、可测试 |
| 列名来自注解 | 开发者手写常量，支持 `表别名.列名` 适配 JOIN 场景 |
| DEPT_IN 为展开后的 id 集合 | 部门树遍历（含子部门）由 ScopeResolver 实现，组件不感知 |
| 无 MyBatis-Plus | starter 可引入但不生效；未来 ORM 实现按需扩展 |

## 测试

13 个测试（H2 + MyBatis-Plus 真库）：SELF/DEPT_IN/ALL 过滤、无注解零干预、
fail-closed（缺列/null 决策）、fail-fast（未启用带注解/缺 resolver/缺 provider）、
分页 count 过滤、拦截器队首断言。
