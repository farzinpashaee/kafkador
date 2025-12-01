package com.csl.kafkador.service.config;

import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.repository.KafkadorConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Primary
@Service("GenericConfigService")
@RequiredArgsConstructor
public class GenericKafkadorConfigServiceImp implements KafkadorConfigService<String, Map.Entry<String, String>> {

    private final KafkadorConfigRepository kafkadorConfigRepository;

    @Override
    public String get(String key, String clusterId) throws ConfigNotFoundException {
        Optional<KafkadorConfig> kafkadorConfigOptional = kafkadorConfigRepository.findByConfigKeyAndClusterId(key,clusterId);
        if(kafkadorConfigOptional.isPresent()){
            return kafkadorConfigOptional.get().getConfigValue();
        } else {
            throw new ConfigNotFoundException("Kafkador Config with given key not found!");
        }
    }

    @Override
    public String save(Map.Entry<String, String> entry, String clusterId) {

        Date now = new Date();
        KafkadorConfig kafkadorConfig;

        Optional<KafkadorConfig> kafkadorConfigOptional = kafkadorConfigRepository.findByConfigKeyAndClusterId(entry.getKey(),clusterId);

        if(kafkadorConfigOptional.isPresent()){
            kafkadorConfig = kafkadorConfigOptional.get();
        } else {
            kafkadorConfig = new KafkadorConfig();
            kafkadorConfig.setCreateDateTime(now);
        }

        kafkadorConfig.setConfigKey(entry.getKey());
        kafkadorConfig.setConfigValue(entry.getValue());
        kafkadorConfig.setProfile("default");
        kafkadorConfig.setUpdateDateTime(now);
        kafkadorConfig.setClusterId(clusterId);
        kafkadorConfigRepository.save(kafkadorConfig);
        return entry.getValue();

    }

    @Override
    public Map<String, KafkadorConfig> get(String clusterId) {
        List<KafkadorConfig> kafkadorConfigList = kafkadorConfigRepository.findByClusterId(clusterId);
        return kafkadorConfigList.stream()
                .collect(Collectors.toMap(KafkadorConfig::getConfigKey, Function.identity()));
    }

}
