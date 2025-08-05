package com.csl.kafkador.controller;

import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.service.ClusterService;
import com.csl.kafkador.service.ConnectionService;
import com.csl.kafkador.service.ConsumerService;
import com.csl.kafkador.service.TopicService;
import com.csl.kafkador.util.ViewHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.TopicListing;
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

        ViewHelper.setPageTitle("Cluster", model);
        // connectionService.checkConnection(session);
        ClusterService clusterService = (ClusterService) applicationContext.getBean("ClusterService");
        ClusterDetails clusterDetails = clusterService.getClusterDetails();
        model.addAttribute("clusterDetails", clusterDetails);

        return "views/pages/cluster.html";
    }


    @GetMapping("/connect")
    public String connect(Model model, HttpSession session, HttpServletRequest request) {

        ViewHelper.setPageTitle("Connect", model);


        return "views/pages/connect.html";
    }


    @GetMapping("/topics")
    public String topics(Model model, HttpSession session, HttpServletRequest request) throws KafkaAdminApiException {

        ViewHelper.setPageTitle("Topics", model);
        // connectionService.checkConnection(session);
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        Collection<TopicListing> topics = topicService.getTopics();
        model.addAttribute("topics", topics);

        return "views/pages/topics.html";
    }


    @GetMapping("/consumers")
    public String consumers(Model model, HttpSession session, HttpServletRequest request) throws KafkaAdminApiException {

        ViewHelper.setPageTitle("Consumers", model);
        ConsumerService consumersService = (ConsumerService) applicationContext.getBean("ConsumersService");
        Collection<ConsumerGroupListing> consumerGroups = consumersService.getConsumersGroup();
        model.addAttribute("consumerGroups", consumerGroups);

        return "views/pages/consumers.html";
    }

}
