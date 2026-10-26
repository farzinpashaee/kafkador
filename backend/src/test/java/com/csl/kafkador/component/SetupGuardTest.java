package com.csl.kafkador.component;

import com.csl.kafkador.exception.SetupTokenException;
import com.csl.kafkador.service.DatabaseCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupGuardTest {

    private final DatabaseCredentialsService credentials = mock(DatabaseCredentialsService.class);

    @BeforeEach
    void setUp() {
        when(credentials.isSetupRequired()).thenReturn(true);
    }

    @Test
    void asksForAOneTimeTokenThatOnlyTheLogShows() throws Exception {
        SetupGuard guard = guard("", "", "", true);
        guard.prepare();

        assertThat(guard.isTokenRequired()).isTrue();
        assertThat(guard.currentToken()).hasSize(32).matches("[0-9a-f]+");
        assertThatThrownBy(() -> guard.verify(null)).isInstanceOf(SetupTokenException.class).hasMessageContaining("log");
        assertThatThrownBy(() -> guard.verify("not-it")).isInstanceOf(SetupTokenException.class).hasMessageContaining("wrong");
        assertThatCode(() -> guard.verify(" " + guard.currentToken() + " ")).doesNotThrowAnyException();
    }

    @Test
    void acceptsTheTokenOnlyUntilSetupIsDone() throws Exception {
        SetupGuard guard = guard("", "", "chosen-token", true);
        guard.prepare();
        assertThatCode(() -> guard.verify("chosen-token")).doesNotThrowAnyException();

        guard.used();

        assertThat(guard.isTokenRequired()).isFalse();
        assertThat(guard.currentToken()).isNull();
    }

    @Test
    void completesSetupFromPresetCredentialsWithoutAsking() throws Exception {
        SetupGuard guard = guard("kafkador_admin", "a-long-password", "", true);
        guard.prepare();

        verify(credentials).configure("kafkador_admin", "a-long-password");
        assertThat(guard.isTokenRequired()).isFalse();
    }

    @Test
    void refusesToStartWithPresetCredentialsTheSetupPageWouldReject() throws Exception {
        assertThatThrownBy(() -> guard("1admin", "a-long-password", "", true).prepare())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("kafkador.setup.username");
        assertThatThrownBy(() -> guard("admin", "short", "", true).prepare())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("kafkador.setup.password");
        verify(credentials, never()).configure(anyString(), anyString());
    }

    @Test
    void canBeTurnedOffForLocalDevelopment() throws Exception {
        SetupGuard guard = guard("", "", "", false);
        guard.prepare();

        assertThat(guard.isTokenRequired()).isFalse();
        assertThatCode(() -> guard.verify(null)).doesNotThrowAnyException();
    }

    @Test
    void doesNothingOnceSetupIsDone() throws Exception {
        when(credentials.isSetupRequired()).thenReturn(false);
        SetupGuard guard = guard("kafkador_admin", "a-long-password", "", true);
        guard.prepare();

        verify(credentials, never()).configure(anyString(), anyString());
        assertThat(guard.isTokenRequired()).isFalse();
    }

    private SetupGuard guard(String username, String password, String token, boolean requireToken) {
        return new SetupGuard(credentials, username, password, token, requireToken);
    }

}
