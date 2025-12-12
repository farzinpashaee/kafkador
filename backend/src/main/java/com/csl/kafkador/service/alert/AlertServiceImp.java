package com.csl.kafkador.service.alert;

import com.csl.kafkador.domain.dto.AlertDto;
import com.csl.kafkador.domain.model.Alert;
import com.csl.kafkador.exception.AlertNotFoundException;
import com.csl.kafkador.repository.AlertRepository;
import com.csl.kafkador.util.DtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service("AlertService")
@RequiredArgsConstructor
public class AlertServiceImp implements AlertService {

    @Autowired
    AlertRepository alertRepository;

    @Override
    public List<AlertDto> getAlerts(Pageable pageable) {
        return alertRepository.findAll(pageable).stream().map(DtoMapper::alertMapper).collect(Collectors.toList());
    }

    @Override
    public AlertDto getAlert(Integer id) throws AlertNotFoundException {
        Optional<Alert> alert = alertRepository.findById(id);
        if( alert.isPresent() ) throw new AlertNotFoundException("Alert with given ID not found!");
        return DtoMapper.alertMapper(alert.get());
    }
}
