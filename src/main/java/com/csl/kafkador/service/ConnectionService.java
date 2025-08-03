package com.csl.kafkador.service;

import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.model.Connection;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class ConnectionService {

    public Connection checkConnection(HttpSession session){
        throw new ConnectionSessionExpiredException("x","/connect");
    }

}
