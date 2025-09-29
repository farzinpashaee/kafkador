package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.ObserverConfigDto;
import org.springframework.stereotype.Service;

@Service
public interface ObserverService {

    public void capture(String clusterId, ObserverConfigDto.Observer observer);

}
