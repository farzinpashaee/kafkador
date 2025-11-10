package com.csl.kafkador.service.integration;

import com.csl.kafkador.domain.dto.IntegrationExchangeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service("SchemaRegistryService")
@RequiredArgsConstructor
public class IntegrationServiceImp implements IntegrationService {

    private final RestTemplate restTemplate;

    public ResponseEntity exchange(IntegrationExchangeDto<? extends Class> integrationExchange){
        ResponseEntity<List<String>> response = restTemplate.exchange(
                integrationExchange.getUrl(),
                integrationExchange.getHttpMethod(),
                null,
                integrationExchange.getResponseType()
        );
        return response;
    }

}
