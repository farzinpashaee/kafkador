package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpMethod;

@Data
@Accessors(chain = true)
public class IntegrationExchangeDto<T> {

    private String url;
    private HttpMethod httpMethod;
    private T responseType;

}
