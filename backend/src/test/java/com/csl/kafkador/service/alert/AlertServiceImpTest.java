package com.csl.kafkador.service.alert;

import com.csl.kafkador.domain.dto.AlertDto;
import com.csl.kafkador.domain.model.Alert;
import com.csl.kafkador.domain.options.AlertSeverity;
import com.csl.kafkador.exception.AlertNotFoundException;
import com.csl.kafkador.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceImpTest {

    @Mock
    AlertRepository alertRepository;

    @InjectMocks
    AlertServiceImp alertService;

    private Alert sampleAlert;

    @BeforeEach
    void setUp() {
        sampleAlert = new Alert();
        sampleAlert.setId(1);
        sampleAlert.setTitle("Test Alert");
        sampleAlert.setClusterId("cluster-1");
        sampleAlert.setDescription("Something went wrong");
        sampleAlert.setAction("Check broker logs");
        sampleAlert.setSeverity(AlertSeverity.HIGH);
        sampleAlert.setCreationDateTime(new Date());
    }

    @Test
    void getAlert_whenAlertExists_returnsDto() throws AlertNotFoundException {
        when(alertRepository.findById(1)).thenReturn(Optional.of(sampleAlert));

        AlertDto result = alertService.getAlert(1);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Alert");
        assertThat(result.getClusterId()).isEqualTo("cluster-1");
        assertThat(result.getSeverity()).isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void getAlert_whenAlertDoesNotExist_throwsAlertNotFoundException() {
        when(alertRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.getAlert(99))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getAlerts_returnsPagedResults() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "creationDateTime"));
        Page<Alert> page = new PageImpl<>(List.of(sampleAlert));
        when(alertRepository.findAll(pageable)).thenReturn(page);

        List<AlertDto> results = alertService.getAlerts(pageable);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Test Alert");
    }

    @Test
    void getAlerts_whenNoAlerts_returnsEmptyList() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(alertRepository.findAll(pageable)).thenReturn(Page.empty());

        List<AlertDto> results = alertService.getAlerts(pageable);

        assertThat(results).isEmpty();
    }
}
