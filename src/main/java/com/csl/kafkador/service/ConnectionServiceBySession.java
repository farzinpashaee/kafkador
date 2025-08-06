package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.dto.RequestContext;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.model.Connection;
import com.csl.kafkador.util.HashHelper;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service("ConnectionServiceBySession")
public class ConnectionServiceBySession {

    @Autowired
    KafkadorContext kafkadorContext;

    public Connection createConnection( RequestContext<Connection> request ){
        List<Connection> connections = request.getHttpSession().getAttribute(KafkadorContext.Attribute.CONNECTIONS.toString()) == null ?
                new ArrayList<>() : (List<Connection>) request.getHttpSession().getAttribute(KafkadorContext.Attribute.CONNECTIONS.toString());
        Connection connection = request.getBody();
        connection.setId(HashHelper.MD5(connection.getHost()+connection.getName()+new Date()));
        connections.add(connection);
        request.getHttpSession().setAttribute(KafkadorContext.Attribute.CONNECTIONS.toString(), connections);
        return connection;
    }

    public List<Connection> getConnections(RequestContext request){
        List<Connection> connections = request.getHttpSession().getAttribute(KafkadorContext.Attribute.CONNECTIONS.toString()) == null ?
                new ArrayList<>() : (List<Connection>) request.getHttpSession().getAttribute(KafkadorContext.Attribute.CONNECTIONS.toString());

        Connection dummy = new Connection(); // TODO: remove dummy
        dummy.setId("XXXXXXXXXXX");
        dummy.setHost("192.168.2.139");
        dummy.setPort("9092");
        dummy.setName("default-connection");
        connections.add( dummy );

        return connections;
    }

    public Connection connect( RequestContext<String> request ) throws ConnectionNotFoundException {
        List<Connection> connections = request.getHttpSession().getAttribute(KafkadorContext.Attribute.CONNECTIONS.toString()) == null ?
                new ArrayList<>() : (List<Connection>) request.getHttpSession().getAttribute(KafkadorContext.Attribute.CONNECTIONS.toString());

        Optional<Connection> connection = connections.stream().filter(i -> i.getId().equals(request.getBody()) ).findFirst();
        if(connection.isPresent()){
            request.getHttpSession().setAttribute(KafkadorContext.Attribute.ACTIVE_CONNECTION.toString(),connection.get());
            return connection.get();
        } else {
            throw new ConnectionNotFoundException("Connection with given ID not Found!");
        }
    }

    public Properties getConnectionProperties( RequestContext requestContext ){
        Connection connection = (Connection) requestContext.getHttpSession().getAttribute(KafkadorContext.Attribute.ACTIVE_CONNECTION.toString());
        String bootstrapServers = connection.getHost() + ":" + connection.getPort() ; // Replace with your Kafka broker address
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return props;
    }


}
