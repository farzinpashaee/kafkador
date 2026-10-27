package com.csl.kafkador.interceptor;

import com.csl.kafkador.service.DatabaseCredentialsService;
import com.csl.kafkador.service.access.AccessService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseSetupInterceptorTest {

    private final DatabaseCredentialsService database = mock(DatabaseCredentialsService.class);
    private final AccessService access = mock(AccessService.class);
    private final DatabaseSetupInterceptor interceptor = new DatabaseSetupInterceptor(database, access);

    @Test
    void setupComplete_letsEverythingThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cluster");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void setupRequired_apiCall_returns428WithMarkerHeader() throws Exception {
        when(database.isSetupRequired()).thenReturn(true);
        assertAsksForSetup();
    }

    @Test
    void accessNotChosenYet_apiCall_returns428WithMarkerHeader() throws Exception {
        when(access.isSetupRequired()).thenReturn(true);
        assertAsksForSetup();
    }

    @Test
    void optionsRequest_alwaysPassesThrough() throws Exception {
        when(database.isSetupRequired()).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/cluster");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    private void assertAsksForSetup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cluster");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(428);
        assertThat(response.getHeader(DatabaseSetupInterceptor.SETUP_REQUIRED_HEADER)).isEqualTo("true");
        assertThat(response.getContentAsString()).contains("Setup is required");
    }

}
