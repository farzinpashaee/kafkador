package com.csl.kafkador.domain.options;

public enum AlertSeverity {

    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    private String name;
    private String style;

    AlertSeverity( String name ){
        this.name = name;
    }

}
