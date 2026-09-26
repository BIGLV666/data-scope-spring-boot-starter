package io.github.biglv666.datascope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * data-scope 配置（前缀 data-scope）。
 * <p>enabled 默认 false：显式开启后才装配拦截器。开启是强承诺——必须注册
 * {@code ScopeResolver} Bean，否则启动失败（fail-fast，防止数据权限半残上线）。</p>
 */
@ConfigurationProperties(prefix = "data-scope")
public class DataScopeProperties {

    /** 是否启用行级数据权限 */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
