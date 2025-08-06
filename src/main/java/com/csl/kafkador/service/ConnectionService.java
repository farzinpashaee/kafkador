package com.csl.kafkador.service;

import com.csl.kafkador.dto.RequestContext;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.model.Connection;

import java.util.List;
import java.util.Properties;

public interface ConnectionService {

    Connection connect( RequestContext<String> request ) throws ConnectionNotFoundException;
    List<Connection> getConnections(RequestContext request);
    Connection getActiveConnection(RequestContext request) throws ConnectionSessionExpiredException;
    Properties getActiveConnectionProperties(RequestContext request) throws ConnectionSessionExpiredException;

    Connection createConnection( RequestContext<Connection> request ) throws KafkadorException;

}
