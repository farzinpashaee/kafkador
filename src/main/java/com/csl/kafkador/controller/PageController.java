package com.csl.kafkador.controller;

import com.csl.kafkador.dto.*;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.service.TopicService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collection;

@Controller
public class PageController {

    @Autowired
    ApplicationContext applicationContext;

    @GetMapping("/")
    public String cluster(Model model) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Cluster")
                .activeMenu("cluster")
                .icon("bi-diagram-3")
                .build(model);
        return "views/pages/cluster.html";
    }


    @GetMapping("/broker/{id}")
    public String broker(Model model, @PathVariable String id) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Broker")
                .activeMenu("cluster")
                .build(model);
        return "views/pages/broker.html";
    }


    @GetMapping("/connect")
    public String connect(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Connect")
                .activeMenu("connect")
                .build(model);
        return "views/pages/connect.html";
    }


    @GetMapping("/connections")
    public String connections(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Connections")
                .activeMenu("connections")
                .build(model);
        return "views/pages/connections.html";
    }


    @GetMapping("/connectors")
    public String connectors(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Connectors")
                .activeMenu("connectors")
                .build(model);
        return "views/pages/connectors.html";
    }


    @GetMapping("/access-control")
    public String accessControl(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Access Control")
                .activeMenu("access-control")
                .build(model);
        return "views/pages/access-control.html";
    }

    @GetMapping("/ksql-db")
    public String ksqlDB(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("KsqlDB")
                .activeMenu("ksql-db")
                .build(model);
        return "views/pages/ksql-db.html";
    }


    @GetMapping("/schema-registry")
    public String schemaRegistry(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Schema Registry")
                .activeMenu("schema-registry")
                .build(model);
        return "views/pages/schema-registry.html";
    }


    @GetMapping("/streams")
    public String streams(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Streams")
                .activeMenu("streams")
                .build(model);
        return "views/pages/streams.html";
    }

    @GetMapping("/topics")
    public String topics(Model model, HttpSession session) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Topics")
                .activeMenu("topics")
                .build(model);
        return "views/pages/topics.html";
    }


    @GetMapping("/consumers")
    public String consumers(Model model, HttpSession session) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Consumers")
                .activeMenu("consumers")
                .build(model);
        return "views/pages/consumers.html";
    }


    @GetMapping("/sandbox")
    public String sandbox(Model model, HttpSession session) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Sandbox")
                .build(model);
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        Collection<Topic> topics = topicService.getTopics();
        model.addAttribute("topics", topics);

        return "views/pages/sandbox.html";
    }



    @GetMapping("/settings")
    public String settings(Model model) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Settings")
                .activeMenu("settings")
                .build(model);

        return "views/pages/settings.html";
    }

}
