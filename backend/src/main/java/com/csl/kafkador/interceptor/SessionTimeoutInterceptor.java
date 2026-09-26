package com.csl.kafkador.interceptor;

import com.csl.kafkador.service.SessionSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/** Applies the configured idle timeout to an existing session on each API call (never creates one). */
@RequiredArgsConstructor
public class SessionTimeoutInterceptor implements HandlerInterceptor {

    private final SessionSettingsService sessionSettingsService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionSettingsService.apply(session);
        }
        return true;
    }

}
