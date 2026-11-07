package com.scaffoldai.autoconfigure;

import com.scaffoldai.security.TenantFilter;
import com.scaffoldai.web.ChatController;
import com.scaffoldai.web.GlobalExceptionHandler;
import com.scaffoldai.web.KnowledgeBaseController;
import com.scaffoldai.web.SystemController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层自动配置：REST 接口、租户过滤器、CORS、SSE 异步超时。
 * 只引入 starter 即可获得开箱可用的 HTTP API；不需要时设置 ai.scaffold.web.enabled=false。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ai.scaffold.web", name = "enabled", matchIfMissing = true)
@Import({ChatController.class, KnowledgeBaseController.class, SystemController.class,
        GlobalExceptionHandler.class})
public class AiScaffoldWebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiScaffoldWebAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TenantFilter aiTenantFilter(AiScaffoldProperties properties) {
        log.info("租户过滤器初始化，鉴权模式=header（X-Tenant-Id / X-User-Id / X-Roles 透传）");
        return new TenantFilter(properties.getSecurity().getTenantHeader(),
                properties.getSecurity().getUserHeader(),
                properties.getSecurity().getRolesHeader(),
                properties.getSecurity().getDefaultTenant(),
                properties.getSecurity().getDefaultUser(),
                properties.getSecurity().getDefaultRoles());
    }

    @Bean
    public FilterRegistrationBean<TenantFilter> aiTenantFilterRegistration(TenantFilter aiTenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(aiTenantFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        registration.setName("aiTenantFilter");
        return registration;
    }

    @Bean
    public WebMvcConfigurer aiScaffoldWebMvcConfigurer(AiScaffoldProperties properties) {
        String[] origins = properties.getWeb().getCorsAllowedOrigins().split(",");
        int sseTimeoutMillis = properties.getChat().getSseTimeoutSeconds() * 1000;
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping(properties.getWeb().getBasePath() + "/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }

            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setDefaultTimeout(sseTimeoutMillis);
            }
        };
    }
}
