package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import org.springframework.stereotype.Service;

@Service
public interface ObserverService {

    public void capture(ApplicationConfig.Observer observerConfig  );

}
