package com.csl.kafkador.service.agent;

import com.csl.kafkador.domain.model.Agent;
import com.csl.kafkador.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service("AgentService")
@RequiredArgsConstructor
public class AgentServiceImp implements AgentService {

    private final AgentRepository agentRepository;

    @Override
    public List<Agent> getAgents() {
        return agentRepository.findAll();
    }

}
