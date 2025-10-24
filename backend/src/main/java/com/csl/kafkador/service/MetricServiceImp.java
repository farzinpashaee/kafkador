package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.MetricChartDto;
import com.csl.kafkador.domain.model.Metric;
import com.csl.kafkador.repository.MetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
