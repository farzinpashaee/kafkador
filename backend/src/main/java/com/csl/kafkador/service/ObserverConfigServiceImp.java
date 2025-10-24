package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.ObserverConfigDto;
import com.csl.kafkador.exception.KafkadorConfigNotFoundException;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.repository.KafkadorConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service("ObserverConfigService")
@RequiredArgsConstructor
public class ObserverConfigServiceImp implements ConfigService<ObserverConfigDto,ObserverConfigDto.ObserverCluster> {

    ObjectMapper objectMapper = new ObjectMapper();
    private final KafkadorConfigRepository kafkadorConfigRepository;

    @Override
    public ObserverConfigDto get(String key) throws KafkadorConfigNotFoundException {
        Optional<KafkadorConfig> kafkadorConfigOptional = kafkadorConfigRepository.findByConfigKey(key);
        if(kafkadorConfigOptional.isPresent()){
            try {
                return objectMapper.readValue(kafkadorConfigOptional.get().getConfigValue(), ObserverConfigDto.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new KafkadorConfigNotFoundException("Kafkador Config with given key not found!");
        }
    }

    @Override
    public ObserverConfigDto save(ObserverConfigDto.ObserverCluster observerCluster) {
        try {
            String key = "kafkador.observer";
            Date now = new Date();
            KafkadorConfig kafkadorConfig;
            ObserverConfigDto observerConfig;

            Optional<KafkadorConfig> kafkadorConfigOptional = kafkadorConfigRepository.findByConfigKey(key);
            if(kafkadorConfigOptional.isPresent()){
                kafkadorConfig = kafkadorConfigOptional.get();
                observerConfig = objectMapper
                        .readValue(kafkadorConfigOptional.get().getConfigValue(), ObserverConfigDto.class);
                Optional<ObserverConfigDto.ObserverCluster> observerClusterOptional = observerConfig.getObserverClusters()
                        .stream()
                        .filter( i -> i.getClusterId().equals(observerCluster.getClusterId()) )
                        .findFirst();
                if(observerClusterOptional.isPresent()){
                    // update exiting config record and cluster
                    observerConfig.getObserverClusters().add(observerCluster);
                } else {
                    // update exiting config record and adding new cluster
                    kafkadorConfig.setCreateDateTime(now);
                    observerConfig = new ObserverConfigDto();
                    observerConfig.getObserverClusters().add(observerCluster);
                }
            } else {
                // adding new config record and new cluster
                observerConfig = new ObserverConfigDto();
                observerConfig.getObserverClusters().add(observerCluster);
                kafkadorConfig = new KafkadorConfig();
            }


            kafkadorConfig.setConfigKey(key);
            kafkadorConfig.setConfigValue(objectMapper.writeValueAsString(observerConfig));
            kafkadorConfig.setProfile("default");
            kafkadorConfig.setUpdateDateTime(now);
            kafkadorConfigRepository.save(kafkadorConfig);
            return observerConfig;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
