package com.csl.kafkador.interceptor;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;


public class SessionInterceptor implements HandlerInterceptor {

    ObjectMapper mapper = new ObjectMapper();

    public SessionInterceptor(){
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession();
        if(session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString()) == null){
            String path = request.getRequestURI();
            if (path.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                ResponseEntity<GenericResponse<Void>> error = new GenericResponse.Builder<Void>()
                        .code(String.valueOf(HttpStatus.UNAUTHORIZED.value()))
                        .message("Session is invalid or expired!")
                        .failed(HttpStatus.INTERNAL_SERVER_ERROR);
                response.getWriter().write(mapper.writeValueAsString(error.getBody()));
                return false;
            } else {
                throw new ConnectionSessionExpiredException("No Active Connection Found!", "/connect");
            }
        }

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