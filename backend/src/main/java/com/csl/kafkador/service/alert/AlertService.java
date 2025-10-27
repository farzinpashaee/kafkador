package com.csl.kafkador.service.alert;

import com.csl.kafkador.domain.dto.AlertDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AlertService {

    List<AlertDto> getAlerts(Pageable pageable);

}
