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

import java.util.Collection;

@Controller
public class PageController {

    @Autowired
    ApplicationContext applicationContext;

    @GetMapping("/")
    public String cluster(Model model, HttpSession session, HttpServletRequest request) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Cluster")
                .icon("bi-diagram-3")
                .build(model);
        return "views/pages/cluster.html";
    }


    @GetMapping("/connect")
    public String connect(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Connect")
                .build(model);
        return "views/pages/connect.html";
    }


    @GetMapping("/connections")
    public String connections(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Connections")
                .build(model);
        return "views/pages/connections.html";
    }


    @GetMapping("/connectors")
    public String connectors(Model model, HttpSession session, HttpServletRequest request) {
        new PageView.Builder()
                .title("Connectors")
                .build(model);
        return "views/pages/connectors.html";
    }

    @GetMapping("/topics")
    public String topics(Model model, HttpSession session) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Topics")
                .build(model);
        return "views/pages/topics.html";
    }


    @GetMapping("/consumers")
    public String consumers(Model model, HttpSession session) throws KafkaAdminApiException {
        new PageView.Builder()
                .title("Consumers")
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

}
