package com.csl.kafkador.config;

import com.csl.kafkador.controller.ResourceInterceptor;
import com.csl.kafkador.controller.SessionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${kafkador.url}")
    private String baseUrl;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor( new ResourceInterceptor(baseUrl) )
                .excludePathPatterns("/assets/**","/css/**","/js/**");
        registry.addInterceptor( new SessionInterceptor() )
                .excludePathPatterns("/assets/**","/css/**","/js/**","/connect","/api/connection","/api/connect");
    }

}
