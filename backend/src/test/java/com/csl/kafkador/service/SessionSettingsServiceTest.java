package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionSettingsServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private int reads;
    private SessionSettingsService service;

    @BeforeEach
    void setUp() {
        KafkadorConfigService<String, Map.Entry<String, String>> config = new KafkadorConfigService<>() {
            @Override
            public String get(String key, String clusterId) throws ConfigNotFoundException {
                reads++;
                String value = store.get(clusterId + "|" + key);
                if (value == null) throw new ConfigNotFoundException("not found");
                return value;
            }

            @Override
            public String save(Map.Entry<String, String> entry, String clusterId) {
                store.put(clusterId + "|" + entry.getKey(), entry.getValue());
                return entry.getValue();
            }

            @Override
            public Map<String, KafkadorConfig> get(String clusterId) {
                return Map.of();
            }
        };
        service = new SessionSettingsService(config, new ApplicationConfig());
    }

    @Test
    void defaultsToTheConfiguredApplicationValue() {
        assertThat(service.getTimeoutMinutes()).isEqualTo(30);
    }

    @Test
    void savedValueIsReturnedAndPersistedGlobally() {
        assertThat(service.saveTimeoutMinutes(90)).isEqualTo(90);

        assertThat(service.getTimeoutMinutes()).isEqualTo(90);
        assertThat(store).containsEntry("global|kafkador.session.timeout-minutes", "90");
    }

    @Test
    void valueIsLoadedFromStorageOnceAndThenCached() {
        store.put("global|kafkador.session.timeout-minutes", "45");

        assertThat(service.getTimeoutMinutes()).isEqualTo(45);
        service.getTimeoutMinutes();
        service.getTimeoutMinutes();

        assertThat(reads).isEqualTo(1);
    }

    @Test
    void outOfRangeOrGarbageStoredValues_fallBackToTheDefault() {
        store.put("global|kafkador.session.timeout-minutes", "0");
        assertThat(service.getTimeoutMinutes()).isEqualTo(30);

        SessionSettingsService other = new SessionSettingsService(configWith("abc"), new ApplicationConfig());
        assertThat(other.getTimeoutMinutes()).isEqualTo(30);
    }

    @Test
    void rejectsValuesOutsideTheAllowedRange() {
        assertThatThrownBy(() -> service.saveTimeoutMinutes(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveTimeoutMinutes(525_601)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.saveTimeoutMinutes(1)).isEqualTo(1);
        assertThat(service.saveTimeoutMinutes(525_600)).isEqualTo(525_600);
    }

    @Test
    void apply_setsTheIdleIntervalInSeconds_andSkipsWhenAlreadyCorrect() {
        service.saveTimeoutMinutes(10);
        HttpSession session = mock(HttpSession.class);
        when(session.getMaxInactiveInterval()).thenReturn(1800);

        service.apply(session);
        verify(session).setMaxInactiveInterval(600);

        HttpSession current = mock(HttpSession.class);
        when(current.getMaxInactiveInterval()).thenReturn(600);
        service.apply(current);
        verify(current, never()).setMaxInactiveInterval(600);
    }

    private static KafkadorConfigService<String, Map.Entry<String, String>> configWith(String value) {
        return new KafkadorConfigService<>() {
            @Override
            public String get(String key, String clusterId) {
                return value;
            }

            @Override
            public String save(Map.Entry<String, String> entry, String clusterId) {
                return entry.getValue();
            }

            @Override
            public Map<String, KafkadorConfig> get(String clusterId) {
                return Map.of();
            }
        };
    }

}
