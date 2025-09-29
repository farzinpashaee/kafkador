package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.dto.ClusterDto;
import com.csl.kafkador.dto.ObserverConfigDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.model.Metric;
import com.csl.kafkador.repository.MetricRepository;
import com.csl.kafkador.util.MetricEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service("ClusterObserver")
@RequiredArgsConstructor
public class ClusterObserverServiceImp implements ObserverService {

    private final ClusterService clusterService;
    private final MetricRepository metricRepository;

    @Override
    public void capture(String clusterId, ObserverConfigDto.Observer observer) {
       try {

           ClusterDto clusterDetails = null;
           try {
               clusterDetails = clusterService.getClusterDetails(clusterId);
               Metric metric = new Metric();
               metric.setClusterId(clusterDetails.getId());
               metric.setMetricName(MetricEnum.NUMBER_OF_BROKERS.toString());
               metric.setEntityId(String.valueOf(clusterDetails.getId()));
               metric.setNumericMetricValue( Double.valueOf( clusterDetails.getBrokers().size()) );
               metric.setCreateDateTime(new Date());
               metricRepository.save(metric);
               if(observer.getLog())
                   log.info(observer.getId() + " Observer captured at " + System.currentTimeMillis());
           } catch (ClusterNotFoundException e) {
               throw new RuntimeException(e);
           }


        } catch (KafkaAdminApiException e) {
            throw new RuntimeException(e);
        }
    }
}
