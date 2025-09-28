package com.csl.kafkador.component;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.service.ObserverService;
import com.csl.kafkador.util.ValidationHelper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCaptureComponent {
    private final ApplicationConfig applicationConfig;
    private final TaskScheduler taskScheduler;
    private final ExecutorService executorService;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void init(){

        if(ValidationHelper.safeCall( () -> applicationConfig.getObserverService().getEnabled())){
            log.info("Initiating Observer component");
            List<ApplicationConfig.Observer> observersConfig = applicationConfig.getObserverService().getObservers();
            if( observersConfig != null && observersConfig.size() > 0 ){
                for( ApplicationConfig.Observer observerConfig : observersConfig ){
                    if(observerConfig.getEnabled()) {
                        CronTrigger cronTrigger = new CronTrigger(observerConfig.getFrequency());
                        taskScheduler.schedule(
                                () -> {
                                    if(observerConfig.getLog())
                                        log.info(observerConfig.getId() + " Observer starting at " + System.currentTimeMillis());
                                    ObserverService observerService = (ObserverService) applicationContext.getBean(observerConfig.getId()+"Observer");
                                    observerService.capture(observerConfig);
                                }, cronTrigger);
                    }
                }
            } else {
                log.info("No metric configuration found!");
            }

        }

    }


}
