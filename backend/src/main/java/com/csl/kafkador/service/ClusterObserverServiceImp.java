package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.BrokerDto;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

               List<Metric> metrics = new ArrayList<>();
               Metric numberOfBrokers = new Metric();
               numberOfBrokers.setClusterId(clusterDetails.getId());
               numberOfBrokers.setMetricName(MetricEnum.NUMBER_OF_BROKERS.toString());
               numberOfBrokers.setEntityId(String.valueOf(clusterDetails.getId()));
               numberOfBrokers.setNumericMetricValue( BigDecimal.valueOf( clusterDetails.getBrokers().size()) );
               numberOfBrokers.setCreateDateTime(new Date());
               metrics.add(numberOfBrokers);

               for( BrokerDto broker : clusterDetails.getBrokers() ) {
                   Metric brokerSize = new Metric();
                   brokerSize.setClusterId(clusterDetails.getId());
                   brokerSize.setMetricName(MetricEnum.SIZE_OF_BROKER.toString());
                   brokerSize.setEntityId(broker.getId());
                   brokerSize.setNumericMetricValue(broker.getSize());
                   brokerSize.setCreateDateTime(new Date());
                   metrics.add(brokerSize);
               }
               metricRepository.saveAll(metrics);

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
