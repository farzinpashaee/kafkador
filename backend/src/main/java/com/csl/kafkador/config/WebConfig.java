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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SessionSettingsService sessionSettingsService;
    private final DatabaseCredentialsService databaseCredentialsService;

    private static final String SETUP_API = "/api/v1/setup/**";

    // Only the API is guarded: the Angular files must load without a session or completed setup, and the UI
    // reacts to the 401 and 428 answers itself.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor( new DatabaseSetupInterceptor(databaseCredentialsService) )
                .addPathPatterns("/api/**")
                .excludePathPatterns(SETUP_API);
        registry.addInterceptor( new SessionTimeoutInterceptor(sessionSettingsService) )
                .addPathPatterns("/api/**");
        registry.addInterceptor( new ResourceInterceptor() )
                .addPathPatterns("/api/**");
        registry.addInterceptor( new SessionInterceptor() )
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/apm/**", "/api/v1/connections", "/api/v1/connections/**", SETUP_API);
        registry.addInterceptor(localeChangeInterceptor());
    }

    /** Serves the Angular app when it's bundled into the jar; a jar built without it serves only the API. */
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
