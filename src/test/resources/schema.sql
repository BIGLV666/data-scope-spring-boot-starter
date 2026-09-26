-- 数据权限集成测试表：三个用户 / 三个部门的数据分布
-- 10001(self 部门 101)：2 条；10002(部门 102)：1 条；10003(部门 103，经理)：1 条
CREATE TABLE IF NOT EXISTS t_data_card (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    title     VARCHAR(64) NOT NULL,
    dept_id   BIGINT      NOT NULL,
    create_by VARCHAR(32) NOT NULL
);
