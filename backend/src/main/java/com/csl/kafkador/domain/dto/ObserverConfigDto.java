package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Accessors(chain = true)
public class ObserverConfigDto implements Serializable {

    private Boolean enabled = true;
    private List<ObserverCluster> observerClusters = new ArrayList<>();

    @Data
    public static class ObserverCluster {
        String clusterId;
        Boolean enabled = false;
        Integer retentionPeriod = 30;
        List<Observer> observers;
    }

    @Data
    public static class Observer {
        String id;
        Boolean enabled = false;
        Boolean log = false;
        String frequency = "* * * * *";
    }

    public static List<Observer> defaultObserverConfig(){
        List<Observer> observers = new ArrayList<>();
        String[] observerGroups = {"Cluster", "Broker", "Topic", "Consumer"};
        for( String observerGroup : Arrays.asList(observerGroups) ){
            Observer observer = new Observer();
            observer.setId(observerGroup);
            observer.setLog(true);
            observer.setFrequency("*/10 * * * * *");
            observer.setEnabled(true);
            observers.add(observer);
        }
        return observers;
    }
}
