package com.csl.kafkador.domain.options;

public enum AlertSeverity {

    HIGH("High"),MEDIUM("Medium"),LOW("low");

    private String name;

    AlertSeverity( String name ){
        this.name = name;
    }

}
