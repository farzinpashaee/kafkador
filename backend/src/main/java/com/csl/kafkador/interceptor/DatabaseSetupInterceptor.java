package com.csl.kafkador.interceptor;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.service.DatabaseCredentialsService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks everything (API and legacy pages alike) until first-run database credentials are set — the
 * most fundamental gate, ahead of the Kafka connection session. Paths for the setup flow itself, and
 * static assets needed to render it, are excluded in WebConfig.
 */
@RequiredArgsConstructor
public class DatabaseSetupInterceptor implements HandlerInterceptor {

    /** Set on the 428 response so the frontend can tell this apart from other 428s (e.g. AI assistant not configured). */
    public static final String SETUP_REQUIRED_HEADER = "X-Kafkador-Db-Setup-Required";

    private final DatabaseCredentialsService databaseCredentialsService;

    private final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!databaseCredentialsService.isSetupRequired()) {
            return true;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            response.setHeader(SETUP_REQUIRED_HEADER, "true");
            response.setStatus(HttpStatus.PRECONDITION_REQUIRED.value());
            response.setContentType("application/json;charset=UTF-8");
            ResponseEntity<GenericResponse<Void>> error = new GenericResponse.Builder<Void>()
                    .code(String.valueOf(HttpStatus.PRECONDITION_REQUIRED.value()))
                    .message("Database setup is required before Kafkador can be used.")
                    .failed(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getWriter().write(mapper.writeValueAsString(error.getBody()));
            return false;
        }
        throw new ConnectionSessionExpiredException("Database setup is required.", "/setup");
    }

}
