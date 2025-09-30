package com.csl.kafkador.component;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.ObserverConfigDto;
import com.csl.kafkador.exception.KafkadorConfigNotFoundException;
import com.csl.kafkador.service.ConfigService;
import com.csl.kafkador.service.ObserverService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCaptureComponent {
    private final ApplicationConfig applicationConfig;
    private final TaskScheduler taskScheduler;
    private final ExecutorService executorService;
    private final ApplicationContext applicationContext;
    @Qualifier("ObserverKafkadorConfigService")
    private final ConfigService<ObserverConfigDto,ObserverConfigDto.ObserverCluster> configService;

    @PostConstruct
    public void init(){

        try {
            ObserverConfigDto observerConfig = configService.get("kafkador.observer");
            if( observerConfig.getEnabled() ) {
                if( observerConfig.getObserverClusters() != null && observerConfig.getObserverClusters().size() > 0 ) {
                    for (ObserverConfigDto.ObserverCluster observerCluster : observerConfig.getObserverClusters()) {
                        if(observerCluster.getObservers() != null && observerCluster.getObservers().size() > 0){
                            for(ObserverConfigDto.Observer observer: observerCluster.getObservers() ){
                                if(observer.getEnabled()){
                                    CronTrigger cronTrigger = new CronTrigger(observer.getFrequency());
                                    taskScheduler.schedule(
                                            () -> {
                                                if(observer.getLog())
                                                    log.info(observer.getId() + " Observer starting at " + System.currentTimeMillis());
                                                ObserverService observerService = (ObserverService) applicationContext.getBean(observer.getId()+"Observer");
                                                observerService.capture(observerCluster.getClusterId(), observer);
                                            }, cronTrigger);
                                }
                            }
                        } else {
                            log.info("No Observer Cluster Group configuration found!");
                        }
                    }
                } else {
                    log.info("No Observer Cluster configuration found!");
                }
            } else {
                log.info("No Observer configuration found!");
            }

        } catch (KafkadorConfigNotFoundException e) {
            throw new RuntimeException(e);
        }

    }


}
