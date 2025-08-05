package com.csl.kafkador.service;

import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.model.Connection;
import com.csl.kafkador.repository.ConnectionRepository;
import com.csl.kafkador.util.HashHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service("ConnectionService")
public class ConnectionService {

    @Autowired
    ConnectionRepository connectionRepository;

    public Connection checkConnection(HttpSession session){
        throw new ConnectionSessionExpiredException("x","/connect");
    }

    public Connection createConnection( Connection connection, HttpSession httpSession ){
        List<Connection> connections = httpSession.getAttribute("connections") == null ? new ArrayList<>() : (List<Connection>) httpSession.getAttribute("connections");
        connection.setId(HashHelper.MD5(connection.getHost()+connection.getName()+new Date()));
        connections.add(connection);
        httpSession.setAttribute("connections",connections);
        return connection;
    }

    public List<Connection> getConnections(HttpSession httpSession){
        List<Connection> connections = httpSession.getAttribute("connections") == null ? new ArrayList<>() : (List<Connection>) httpSession.getAttribute("connections");
        return connections;
    }

    public List<Connection> getConnections(){
        return connectionRepository.findAll();
    }

}
