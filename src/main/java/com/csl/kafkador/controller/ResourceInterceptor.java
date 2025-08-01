package com.csl.kafkador.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

@Slf4j
public class ResourceInterceptor  implements HandlerInterceptor {

    private String baseUrl;

    public ResourceInterceptor(){}

    public ResourceInterceptor( String baseUrl ){
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        UUID uuid = UUID.randomUUID();
        request.setAttribute("start" , System.currentTimeMillis());
        request.setAttribute("request-id", uuid );
        log.info( "{} - calling {}" , uuid , request.getRequestURI() );
        return true;
    }

    @Override
    public void postHandle( HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler,
                            ModelAndView modelAndView) throws Exception {
        if(modelAndView != null) {
            modelAndView.addObject("baseUrl",baseUrl);
        }
        log.info( "{} - response in {}ms",
                request.getAttribute("request-id"),
                System.currentTimeMillis() - (long) request.getAttribute("start") );
    }
}