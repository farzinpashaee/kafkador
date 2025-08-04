package com.csl.kafkador.controller;

import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    ApplicationContext applicationContext;

    @PostMapping("/topics")
    public void createTopic(@RequestBody Topic request) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.createTopic(request);
    }

    @DeleteMapping("/topics/{name}")
    public void deleteTopic(@PathVariable String name) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.deleteTopic(name);
    }

    @GetMapping("/topics/{name}")
    public Topic getTopic(@PathVariable String name) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        return topicService.getTopic(name);
    }

}
