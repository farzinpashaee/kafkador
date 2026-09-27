package com.csl.kafkador.interceptor;

import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.service.DatabaseCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseSetupInterceptorTest {

    @Test
    void setupComplete_letsEverythingThrough() throws Exception {
        DatabaseCredentialsService service = mock(DatabaseCredentialsService.class);
        when(service.isSetupRequired()).thenReturn(false);
        DatabaseSetupInterceptor interceptor = new DatabaseSetupInterceptor(service);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cluster");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void setupRequired_apiCall_returns428WithMarkerHeader() throws Exception {
        DatabaseCredentialsService service = mock(DatabaseCredentialsService.class);
        when(service.isSetupRequired()).thenReturn(true);
        DatabaseSetupInterceptor interceptor = new DatabaseSetupInterceptor(service);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cluster");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(428);
        assertThat(response.getHeader(DatabaseSetupInterceptor.SETUP_REQUIRED_HEADER)).isEqualTo("true");
        assertThat(response.getContentAsString()).contains("Database setup is required");
    }

    @Test
    void setupRequired_pageLoad_redirectsToSetupPage() {
        DatabaseCredentialsService service = mock(DatabaseCredentialsService.class);
        when(service.isSetupRequired()).thenReturn(true);
        DatabaseSetupInterceptor interceptor = new DatabaseSetupInterceptor(service);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ConnectionSessionExpiredException.class)
                .satisfies(ex -> assertThat(((ConnectionSessionExpiredException) ex).getRedirectUrl()).isEqualTo("/setup"));
    }

    @Test
    void optionsRequest_alwaysPassesThrough() throws Exception {
        DatabaseCredentialsService service = mock(DatabaseCredentialsService.class);
        when(service.isSetupRequired()).thenReturn(true);
        DatabaseSetupInterceptor interceptor = new DatabaseSetupInterceptor(service);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/cluster");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

}
