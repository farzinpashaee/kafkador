package com.csl.kafkador.service.alert;

import com.csl.kafkador.domain.dto.AlertDto;
import com.csl.kafkador.exception.AlertNotFoundException;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AlertService {

    List<AlertDto> getAlerts(Pageable pageable);
    AlertDto getAlert(Integer id) throws AlertNotFoundException;

}
