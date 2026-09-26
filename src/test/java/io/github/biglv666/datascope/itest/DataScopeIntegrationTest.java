package io.github.biglv666.datascope.itest;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.biglv666.datascope.exception.DataScopeException;
import io.github.biglv666.datascope.model.ScopePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据权限端到端集成测试（H2 + MyBatis-Plus + 分页插件共存）。
 * <p>数据分布：10001 自建 2 条（部门 101）；10002 自建 1 条（部门 102）；
 * 10003 自建 1 条（部门 103）。</p>
 */
@SpringBootTest(classes = DataScopeTestApp.class)
class DataScopeIntegrationTest {

    @Autowired
    private CardMapper cardMapper;

    @Autowired
    private MybatisPlusInterceptor interceptor;

    @BeforeEach
    void resetHolder() {
        DataScopeTestApp.HOLDER.userId = "10001";
        DataScopeTestApp.HOLDER.roles = Set.of("staff");
        DataScopeTestApp.HOLDER.policy = () -> ScopePolicy.self("10001");
    }

    @Test
    void selfScopeFiltersByCreator() {
        // 种子数据：10001 两条、10002 一条、10003 一条
        seed();

        List<Card> cards = cardMapper.selectScoped();
        assertEquals(2, cards.size(), "SELF 范围只应看到自己创建的数据");
        assertTrue(cards.stream().allMatch(c -> "10001".equals(c.getCreateBy())));
    }

    @Test
    void deptInScopeFiltersByDept() {
        seed();
        DataScopeTestApp.HOLDER.policy = () -> ScopePolicy.deptInLongs(List.of(101L, 102L));

        List<Card> cards = cardMapper.selectScoped();
        assertEquals(3, cards.size(), "DEPT_IN(101,102) 应看到两部门全部数据");
        assertTrue(cards.stream().allMatch(c -> c.getDeptId() == 101L || c.getDeptId() == 102L));
    }

    @Test
    void allScopeReturnsEverything() {
        seed();
        DataScopeTestApp.HOLDER.policy = ScopePolicy::all;

        assertEquals(4, cardMapper.selectScoped().size(), "ALL 豁免不应改写 SQL");
    }

    @Test
    void methodWithoutAnnotationIsUntouched() {
        seed();
        DataScopeTestApp.HOLDER.policy = () -> ScopePolicy.self("10001");

        assertEquals(4, cardMapper.selectUnscoped().size(), "无注解方法不得被改写");
    }

    @Test
    void selfScopeWithoutSelfColumnFailsClosed() {
        seed();
        // 决策为 SELF，但方法只声明了 deptColumn → 拒绝执行而非放行
        DataScopeTestApp.HOLDER.policy = () -> ScopePolicy.self("10001");

        // MyBatis 会统一包装拦截器内异常：root cause 才是 DataScopeException
        MyBatisSystemException ex = assertThrows(MyBatisSystemException.class, () -> cardMapper.selectDeptOnly());
        DataScopeException cause = assertInstanceOf(DataScopeException.class, ex.getRootCause());
        assertTrue(cause.getMessage().contains("selfColumn"), "异常应指明缺失的列声明");
    }

    @Test
    void nullPolicyFailsClosed() {
        seed();
        DataScopeTestApp.HOLDER.policy = () -> null;

        MyBatisSystemException ex = assertThrows(MyBatisSystemException.class, () -> cardMapper.selectScoped());
        assertInstanceOf(DataScopeException.class, ex.getRootCause());
    }

    @Test
    void paginationCountsFilteredRows() {
        seed();
        DataScopeTestApp.HOLDER.policy = () -> ScopePolicy.self("10001");

        // 数据权限拦截器在分页之前生效：count 应为过滤后的 2 而非全量 4
        IPage<Card> page = cardMapper.selectScopedPage(new Page<>(1, 10));
        assertEquals(2, page.getTotal(), "分页 count 必须带数据权限条件");
        assertEquals(2, page.getRecords().size());
    }

    @Test
    void dataScopeInterceptorIsHeadOfChain() {
        // 与业务分页插件共存：数据权限必须头插（否则分页 count 未过滤）
        List<?> inners = interceptor.getInterceptors();
        assertInstanceOf(DataPermissionInterceptor.class, inners.get(0), "数据权限拦截器应位于队首");
        assertInstanceOf(PaginationInnerInterceptor.class, inners.get(inners.size() - 1));
    }

    private void seed() {
        if (cardMapper.selectCount(null) > 0) {
            return;
        }
        insert("101-own-1", 101, "10001");
        insert("101-own-2", 101, "10001");
        insert("102-own", 102, "10002");
        insert("103-own", 103, "10003");
    }

    private void insert(String title, long deptId, String createBy) {
        Card card = new Card();
        card.setTitle(title);
        card.setDeptId(deptId);
        card.setCreateBy(createBy);
        cardMapper.insert(card);
    }
}
