package com.csl.kafkador.service;

import com.csl.kafkador.dto.MetricChartDto;

public interface MetricService {

    MetricChartDto getChart(MetricChartDto request);
}
