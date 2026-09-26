package io.github.biglv666.datascope.itest;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.biglv666.datascope.annotation.DataScope;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 测试 Mapper：覆盖注解各种列声明组合（含故意的"缺列"组合，验证 fail-closed）。
 */
public interface CardMapper extends BaseMapper<Card> {

    /** 全列声明：SELF/DEPT_IN 两种范围都能执行 */
    @DataScope(deptColumn = "dept_id", selfColumn = "create_by")
    @Select("SELECT * FROM t_data_card ORDER BY id")
    List<Card> selectScoped();

    /** 仅声明部门列：DEPT_IN 可执行，SELF 应 fail-closed */
    @DataScope(deptColumn = "dept_id")
    @Select("SELECT * FROM t_data_card ORDER BY id")
    List<Card> selectDeptOnly();

    /** 无注解：组件不得干预 */
    @Select("SELECT * FROM t_data_card ORDER BY id")
    List<Card> selectUnscoped();

    /** 带注解的自定义分页：验证与分页插件共存时 count 也带过滤条件 */
    @DataScope(deptColumn = "dept_id", selfColumn = "create_by")
    @Select("SELECT * FROM t_data_card ORDER BY id")
    IPage<Card> selectScopedPage(IPage<Card> page);
}
