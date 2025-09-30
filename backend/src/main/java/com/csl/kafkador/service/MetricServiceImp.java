package com.csl.kafkador.service;

import com.csl.kafkador.dto.MetricChartDto;
import com.csl.kafkador.model.Metric;
import com.csl.kafkador.repository.MetricRepository;
import com.csl.kafkador.util.MetricEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricServiceImp implements MetricService {

    private final MetricRepository metricRepository;

    @Override
    public MetricChartDto getChart(MetricChartDto metricChart){
        List<Metric> metricHistory = metricRepository.findAll();
        metricHistory.stream().forEach( i -> {
            metricChart.addMetricSample(i);
        });
        return metricChart;
    }

}
