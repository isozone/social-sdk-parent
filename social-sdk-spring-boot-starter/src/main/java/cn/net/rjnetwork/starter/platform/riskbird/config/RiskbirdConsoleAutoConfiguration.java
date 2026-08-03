package cn.net.rjnetwork.starter.platform.riskbird.config;

import cn.net.rjnetwork.riskbird.service.RiskbirdSdk;
import cn.net.rjnetwork.starter.platform.common.web.StarterGlobalExceptionHandler;
import cn.net.rjnetwork.starter.platform.riskbird.controller.RiskbirdConsoleController;
import cn.net.rjnetwork.starter.platform.riskbird.service.RiskbirdConsoleService;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * riskbird 控制台能力自动配置。
 *
 * <p>启用条件：
 * <ul>
 *   <li>{@code social-sdk.console.riskbird.enabled=true}（配置开关）</li>
 *   <li>类路径存在 {@link RiskbirdSdk}（social-sdk-riskbird）与 {@link ChromeBrowser}（social-sdk-chrome）</li>
 *   <li>容器中存在 {@link ChromeBrowser} Bean（Spring 扫描到 social-sdk-chrome 的组件时自动装配）</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass({RiskbirdSdk.class, ChromeBrowser.class})
@ConditionalOnProperty(prefix = "social-sdk.console.riskbird", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RiskbirdConsoleProperties.class)
public class RiskbirdConsoleAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RiskbirdConsoleAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RiskbirdConsoleService riskbirdConsoleService(
            RiskbirdConsoleProperties properties,
            ObjectProvider<ChromeBrowser> chromeBrowserProvider) {
        ChromeBrowser chromeBrowser = chromeBrowserProvider.getIfAvailable();
        if (chromeBrowser == null) {
            throw new IllegalStateException(
                    "启用 riskbird 控制台需要 ChromeBrowser Bean：请引入 social-sdk-chrome 并确保扫描 cn.net.rjnetwork.xianyu.chrome 包");
        }
        logger.info("Riskbird console service initialized: queryChannel={}, perAccountContainer={}",
                properties.getQueryChannel(), properties.isPerAccountContainer());
        return new RiskbirdConsoleService(properties, chromeBrowser);
    }

    @Bean
    @ConditionalOnMissingBean
    public RiskbirdConsoleController riskbirdConsoleController(RiskbirdConsoleService service) {
        return new RiskbirdConsoleController(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public StarterGlobalExceptionHandler starterGlobalExceptionHandler() {
        return new StarterGlobalExceptionHandler();
    }
}
