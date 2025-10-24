package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.model.Metric;
import com.csl.kafkador.util.MetricEnum;
import com.csl.kafkador.util.TimeUnitEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class MetricChartDto {

    private String id;
    private MetricEnum metricEnum;
    private Long start;
    private Long end;
    private TimeUnitEnum sampleDuration;
    private List<Sample> timeSeries = new ArrayList<>();

    public void addMetricSample(Metric metric){
        timeSeries.add( new Sample()
                .setTime(metric.getCreateDateTime().getTime())
                .setValue(metric.getNumericMetricValue()));
    }

    @Data
    @Accessors(chain = true)
    public static class Sample<T>{
        private Long time;
        private BigDecimal value;
    }

}
