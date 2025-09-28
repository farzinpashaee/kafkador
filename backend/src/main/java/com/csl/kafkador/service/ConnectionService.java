package com.csl.kafkador.service;

import com.csl.kafkador.dto.Request;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.model.Connection;

import java.util.List;
import java.util.Properties;

public interface ConnectionService {

    Connection connect( Integer id ) throws ConnectionNotFoundException;
    Connection disconnect() throws ConnectionNotFoundException;
    List<Connection> getConnections();
    Connection getActiveConnection() throws ConnectionSessionExpiredException;
    Properties getActiveConnectionProperties() throws ConnectionSessionExpiredException;
    Connection createConnection( Connection connection ) throws KafkadorException;

}
