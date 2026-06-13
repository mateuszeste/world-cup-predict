package com.worldcup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Klient Sofascore API (RapidAPI) jako fallback gdy TheSportsDB nie zwraca wyniku.
 * Plan basic: 500 requestow/miesiac.
 */
@Component
public class ApiFootballClient {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballClient.class);
    private static final String API_BASE = "https://sofascore.p.rapidapi.com";
    private static final int FOOTBALL_SPORT_ID = 1;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.apifootball.key:}")
    private String apiKey;

    public ApiFootballClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Pobiera wynik meczu z Sofascore.
     * @return [ftHome, ftAway, htHome, htAway] lub null jesli nie znaleziono
     */
    public int[] fetchResult(String homeEn, String awayEn, String date) {
        if (!isEnabled()) return null;

        try {
            String uri = API_BASE + "/v1/events/schedule/date?sport_id=" + FOOTBALL_SPORT_ID
                    + "&date=" + date;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("x-rapidapi-host", "sofascore.p.rapidapi.com")
                    .header("x-rapidapi-key", apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Sofascore zwrocil status {}: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) return null;

            for (JsonNode event : data) {
                JsonNode homeTeam = event.get("homeTeam");
                JsonNode awayTeam = event.get("awayTeam");
                if (homeTeam == null || awayTeam == null) continue;

                String apiHome = homeTeam.has("name") ? homeTeam.get("name").asText() : null;
                String apiAway = awayTeam.has("name") ? awayTeam.get("name").asText() : null;
                if (apiHome == null || apiAway == null) continue;

                if (!apiHome.equalsIgnoreCase(homeEn) || !apiAway.equalsIgnoreCase(awayEn)) continue;

                JsonNode status = event.get("status");
                if (status == null) continue;
                String statusType = status.has("type") ? status.get("type").asText() : "";
                if (!"finished".equalsIgnoreCase(statusType)) continue;

                JsonNode homeScore = event.get("homeScore");
                JsonNode awayScore = event.get("awayScore");
                if (homeScore == null || awayScore == null) continue;

                int ftHome = homeScore.has("current") ? homeScore.get("current").asInt() : -1;
                int ftAway = awayScore.has("current") ? awayScore.get("current").asInt() : -1;
                if (ftHome < 0 || ftAway < 0) continue;

                int htHome = homeScore.has("period1") ? homeScore.get("period1").asInt() : -1;
                int htAway = awayScore.has("period1") ? awayScore.get("period1").asInt() : -1;

                if (htHome >= 0 && htAway >= 0) {
                    return new int[]{ftHome, ftAway, htHome, htAway};
                }
                return new int[]{ftHome, ftAway};
            }
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wyniku z Sofascore dla {} vs {}: {}",
                    homeEn, awayEn, e.getMessage());
        }
        return null;
    }
}
