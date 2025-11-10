package com.csl.kafkador.interceptor;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.domain.dto.ConnectionDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;


public class SessionInterceptor implements HandlerInterceptor {

    public SessionInterceptor(){}

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {


        HttpSession session = request.getSession();
        if(session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString()) == null)
            throw new ConnectionSessionExpiredException("No Active Connection Found!", "/connect");

        return true;
    }

    @Override
    public void postHandle( HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler,
                            ModelAndView modelAndView) throws Exception {
        if(modelAndView != null) {
            HttpSession session = request.getSession();
            if( session != null ){
                ConnectionDto connection = (ConnectionDto) session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString());
                modelAndView.addObject("connection",connection);
            }
        }
    }
}