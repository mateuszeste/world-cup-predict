package com.worldcup.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.worldcup.model.Match;
import com.worldcup.repository.MatchRepository;
import com.worldcup.service.ApiFootballClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/diag")
public class DiagnosticController {

    private final ApiFootballClient apiFootballClient;
    private final MatchRepository matchRepository;

    public DiagnosticController(ApiFootballClient apiFootballClient, MatchRepository matchRepository) {
        this.apiFootballClient = apiFootballClient;
        this.matchRepository = matchRepository;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("apiFootball_enabled", apiFootballClient.isEnabled());
        response.put("apiFootball_lastError", apiFootballClient.getLastError());
        response.put("apiFootball_lastRequestUrl", apiFootballClient.getLastRequestUrl());
        response.put("apiFootball_lastResponseStatus", apiFootballClient.getLastResponseStatus());
        
        JsonNode lastResponse = apiFootballClient.getLastResponse();
        if (lastResponse != null) {
            response.put("apiFootball_lastResponse", lastResponse.toString());
        }
        
        List<Match> blockedMatches = matchRepository.findAll().stream()
            .filter(m -> m.getHtFetchAttempts() != null && m.getHtFetchAttempts() >= 3)
            .collect(Collectors.toList());
            
        List<Map<String, Object>> blockedInfo = blockedMatches.stream().map(m -> {
            Map<String, Object> info = new HashMap<>();
            info.put("id", m.getId());
            info.put("match", m.getTeam1En() + " vs " + m.getTeam2En());
            info.put("attempts", m.getHtFetchAttempts());
            return info;
        }).collect(Collectors.toList());
        
        response.put("blocked_matches_count", blockedMatches.size());
        response.put("blocked_matches", blockedInfo);
        
        return response;
    }

    @GetMapping("/reset-attempts")
    public Map<String, Object> resetAttempts() {
        List<Match> matches = matchRepository.findAll();
        int resetCount = 0;
        for (Match m : matches) {
            if (m.getHtFetchAttempts() != null && m.getHtFetchAttempts() > 0) {
                m.setHtFetchAttempts(0);
                matchRepository.save(m);
                resetCount++;
            }
        }
        return Map.of("reset_matches_count", resetCount);
    }
}
