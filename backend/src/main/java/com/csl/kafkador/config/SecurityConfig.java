package com.csl.kafkador.config;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.service.access.AccessService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Who may call the API, and protection against cross-site requests.
 *
 * <p>The API needs a signed-in user when first-run setup or Settings chose DATABASE or LDAP sign-in (see
 * {@link AccessService}); with NONE, or before setup has chosen, it's open as before. The setup, version and sign-in
 * endpoints are always open, as are the broker agent's metrics endpoint, the healthcheck and the UI's own files.</p>
 *
 * <p>Requests that change something need the token from the XSRF-TOKEN cookie in an X-XSRF-TOKEN header, which only
 * pages from Kafkador's own site can read, whatever the sign-in mode.</p>
 */
@Configuration
public class SecurityConfig {

    /** Set on the 401 that asks the UI to show its sign-in page, as opposed to the 401 that asks for a cluster connection. */
    public static final String LOGIN_REQUIRED_HEADER = "X-Kafkador-Login-Required";

    private static final ObjectMapper MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessService accessService) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> signedIn =
                (authentication, context) -> new AuthorizationDecision(accessService.isAllowed(authentication.get()));

        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers("/api/v1/setup/**", "/api/v1/app-info", "/api/v1/auth/**").permitAll()
                        // The broker agent (agent/) posts metrics here without a browser session.
                        .requestMatchers("/api/v1/apm/**").permitAll()
                        .requestMatchers("/api/**").access(signedIn)
                        // The UI's files, which then ask the API; /actuator/health; the H2 console in the dev profile.
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // The token goes in a header as read from the cookie, not masked as in a server-rendered form.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // The agent isn't a browser; the dev-only H2 console has its own sign-in.
                        .ignoringRequestMatchers("/api/v1/apm/**", "/kafkador-h2/**"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) -> loginRequired(response))
                        .accessDeniedHandler((request, response, e) -> {
                            if (e instanceof CsrfException) {
                                write(response, HttpStatus.FORBIDDEN, "This request is missing its security token. Reload the page and try again.");
                            } else {
                                // Signed in before who may use Kafkador last changed.
                                loginRequired(response);
                            }
                        }))
                // The H2 console (dev profile) shows itself in frames.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable());
        return http.build();
    }

    private static void loginRequired(HttpServletResponse response) throws IOException {
        response.setHeader(LOGIN_REQUIRED_HEADER, "true");
        write(response, HttpStatus.UNAUTHORIZED, "Sign in to use Kafkador.");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        GenericResponse<Void> body = new GenericResponse.Builder<Void>()
                .code(String.valueOf(status.value()))
                .message(message)
                .failed(status)
                .getBody();
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }

    /** Makes sure every response carries the XSRF-TOKEN cookie, so the UI has it before its first change. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(request, response);
        }
    }

}
