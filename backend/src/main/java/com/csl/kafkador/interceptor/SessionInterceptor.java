package com.csl.kafkador.interceptor;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.domain.GenericResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.HandlerInterceptor;

/** Answers API calls made without an active cluster connection with 401, which sends the UI to its connect page. */
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            ResponseEntity<GenericResponse<Void>> error = new GenericResponse.Builder<Void>()
                    .code(String.valueOf(HttpStatus.UNAUTHORIZED.value()))
                    .message("Session is invalid or expired!")
                    .failed(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getWriter().write(mapper.writeValueAsString(error.getBody()));
            return false;
        }

        return true;
    }

}
