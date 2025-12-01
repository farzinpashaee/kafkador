package com.csl.kafkador.service.config;

import com.csl.kafkador.domain.dto.ObserverConfigDto;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.repository.KafkadorConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service("ObserverConfigService")
@RequiredArgsConstructor
public class ObserverKafkadorConfigServiceImp implements KafkadorConfigService<ObserverConfigDto,ObserverConfigDto> {

    ObjectMapper objectMapper = new ObjectMapper();
    private final KafkadorConfigRepository kafkadorConfigRepository;
    private final static String KEY = "kafkador.observer";

    @Override
    public ObserverConfigDto get(String key, String clusterId) throws ConfigNotFoundException {
        Optional<KafkadorConfig> kafkadorConfigOptional = kafkadorConfigRepository.findByConfigKeyAndClusterId(key,clusterId);
        if(kafkadorConfigOptional.isPresent()){
            try {
                return objectMapper.readValue(kafkadorConfigOptional.get().getConfigValue(), ObserverConfigDto.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new ConfigNotFoundException("Kafkador Config with given key not found!");
        }
    }

    @Override
    public ObserverConfigDto save(ObserverConfigDto observerConfig, String clusterId) {
        try {
            Date now = new Date();
            KafkadorConfig kafkadorConfig;

            Optional<KafkadorConfig> kafkadorConfigOptional = kafkadorConfigRepository.findByConfigKeyAndClusterId(KEY,clusterId);
            if(kafkadorConfigOptional.isPresent()){
                kafkadorConfig = kafkadorConfigOptional.get();
                observerConfig = objectMapper
                        .readValue(kafkadorConfigOptional.get().getConfigValue(), ObserverConfigDto.class);
            } else {
                kafkadorConfig = new KafkadorConfig();
                kafkadorConfig.setCreateDateTime(now);
            }

            kafkadorConfig.setConfigKey(KEY);
            kafkadorConfig.setConfigValue(objectMapper.writeValueAsString(observerConfig));
            kafkadorConfig.setProfile("default");
            kafkadorConfig.setUpdateDateTime(now);
            kafkadorConfig.setClusterId(clusterId);
            kafkadorConfigRepository.save(kafkadorConfig);
            return observerConfig;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, KafkadorConfig> get(String clusterId) {
        return null;
    }
}
