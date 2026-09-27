package com.csl.kafkador.config;

import com.csl.kafkador.interceptor.DatabaseSetupInterceptor;
import com.csl.kafkador.interceptor.ResourceInterceptor;
import com.csl.kafkador.interceptor.SessionInterceptor;
import com.csl.kafkador.interceptor.SessionTimeoutInterceptor;
import com.csl.kafkador.service.DatabaseCredentialsService;
import com.csl.kafkador.service.SessionSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ApplicationConfig applicationConfig;
    private final SessionSettingsService sessionSettingsService;
    private final DatabaseCredentialsService databaseCredentialsService;

    private static final String[] SETUP_PATHS = {"/setup", "/api/v1/setup/**"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration databaseSetupInterceptor = registry.addInterceptor( new DatabaseSetupInterceptor(databaseCredentialsService) )
                .excludePathPatterns("/assets/**","/css/**","/js/**", "/error", "/kafkador-h2","/kafkador-h2/**")
                .excludePathPatterns(SETUP_PATHS);
        registry.addInterceptor( new SessionTimeoutInterceptor(sessionSettingsService) )
                .addPathPatterns("/api/**");
        registry.addInterceptor( new ResourceInterceptor(applicationConfig.getUrl()) )
                .excludePathPatterns("/assets/**","/css/**","/js/**");
        InterceptorRegistration sessionInterceptor = registry.addInterceptor( new SessionInterceptor() )
                .excludePathPatterns("/assets/**","/css/**","/js/**", "/error",
                        "/connect","/api/v1/apm/**",
                        "/api/v1/connections","/api/v1/connections/**",
                        "/kafkador-h2","/kafkador-h2/**")
                .excludePathPatterns(SETUP_PATHS);
        if (BundledFrontend.isPresent()) {
            // The bundled Angular files (index.html, hashed js/css, images) must load without a session
            // or completed setup; only the API is protected, and the UI reacts to 401/428 itself.
            sessionInterceptor.addPathPatterns("/api/**");
            databaseSetupInterceptor.addPathPatterns("/api/**");
        }
        registry.addInterceptor(localeChangeInterceptor());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (BundledFrontend.isPresent()) {
            registry.addResourceHandler("/**")
                    .addResourceLocations("classpath:/static/")
                    .setCacheControl(CacheControl.noCache())
                    .resourceChain(true)
                    .addResolver(new SpaFallbackResolver());
        }
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver localeResolver = new SessionLocaleResolver();
        localeResolver.setDefaultLocale(Locale.US);
        return localeResolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        return lci;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:4200")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders(DatabaseSetupInterceptor.SETUP_REQUIRED_HEADER)
                        .allowCredentials(true);
            }
        };
    }

}
