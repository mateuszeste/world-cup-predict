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
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Klient API-Football (api-football.com) przez RapidAPI,
 * uzywany do uzupelniania brakujacego HT, gdy TheSportsDB zwroci tylko wynik FT.
 * Darmowy tier: 100 requestow/dzien. Cache po daty zeby nie przekraczac limitu.
 */
@Component
public class ApiFootballClient {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballClient.class);
    private static final String API_BASE = "https://v3.football.api-sports.io";
    private static final int WORLD_CUP_ID = 1;

    private static final Map<String, String> ALIAS_TABLE = Map.of(
            "USA", "United States",
            "Korea", "Korea Republic",
            "South Korea", "Korea Republic",
            "Netherlands", "Holland",
            "Czech Republic", "Czechia"
    );

    private static class CacheEntry {
        final JsonNode data;
        final Instant expiryTime;
        CacheEntry(JsonNode data, Instant expiryTime) {
            this.data = data;
            this.expiryTime = expiryTime;
        }
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private Instant rateLimitUntil = null;

    @Value("${app.apifootball.key:}")
    private String apiKey;

    private String lastError;
    private String lastRequestUrl;
    private Integer lastResponseStatus;
    private JsonNode lastResponse;

    public String getLastError() { return lastError; }
    public String getLastRequestUrl() { return lastRequestUrl; }
    public Integer getLastResponseStatus() { return lastResponseStatus; }
    public JsonNode getLastResponse() { return lastResponse; }

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
     * Pobiera wynik meczu z API-Football. Cache'uje po dacie zeby ograniczyc requesty.
     * @return [ftHome, ftAway, htHome, htAway] lub null jesli nie znaleziono
     */
    public int[] fetchResult(String homeEn, String awayEn, String date) {
        if (!isEnabled()) {
            log.debug("API-Football wylaczone (brak klucza API_FOOTBALL_KEY)");
            return null;
        }

        try {
            JsonNode data = getFixturesForDate(date);
            if (data == null) return null;

            for (JsonNode fixture : data) {
                JsonNode teams = fixture.get("teams");
                if (teams == null) continue;

                JsonNode homeTeam = teams.get("home");
                JsonNode awayTeam = teams.get("away");
                if (homeTeam == null || awayTeam == null) continue;

                String apiHome = homeTeam.has("name") ? homeTeam.get("name").asText() : null;
                String apiAway = awayTeam.has("name") ? awayTeam.get("name").asText() : null;
                if (apiHome == null || apiAway == null) continue;

                if (!matchTeamName(apiHome, homeEn) || !matchTeamName(apiAway, awayEn)) continue;

                JsonNode fixtureNode = fixture.get("fixture");
                if (fixtureNode == null) continue;
                JsonNode status = fixtureNode.get("status");
                if (status == null) continue;
                String shortStatus = status.has("short") ? status.get("short").asText() : "";
                if (!"FT".equals(shortStatus) && !"AET".equals(shortStatus) && !"PEN".equals(shortStatus)) continue;

                JsonNode score = fixture.get("score");
                if (score == null) continue;

                JsonNode ftScore = score.get("fulltime");
                JsonNode htScore = score.get("halftime");
                if (ftScore == null) continue;

                int ftHome = ftScore.has("home") && !ftScore.get("home").isNull() ? ftScore.get("home").asInt() : -1;
                int ftAway = ftScore.has("away") && !ftScore.get("away").isNull() ? ftScore.get("away").asInt() : -1;
                if (ftHome < 0 || ftAway < 0) continue;

                if (htScore != null) {
                    int htHome = htScore.has("home") && !htScore.get("home").isNull() ? htScore.get("home").asInt() : -1;
                    int htAway = htScore.has("away") && !htScore.get("away").isNull() ? htScore.get("away").asInt() : -1;
                    if (htHome >= 0 && htAway >= 0) {
                        return new int[]{ftHome, ftAway, htHome, htAway};
                    }
                }
                log.debug("API-Football zwrocil FT ({}:{}), ale brakuje wyniku HT dla {} vs {}", ftHome, ftAway, homeEn, awayEn);
                return new int[]{ftHome, ftAway};
            }
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wyniku z API-Football dla {} vs {}: {}",
                    homeEn, awayEn, e.getMessage());
        }
        return null;
    }

    private boolean matchTeamName(String apiName, String dbName) {
        if (apiName.equalsIgnoreCase(dbName)) return true;
        if (dbName.equalsIgnoreCase(ALIAS_TABLE.get(apiName))) return true;
        if (apiName.equalsIgnoreCase(ALIAS_TABLE.get(dbName))) return true;
        return false;
    }


    private JsonNode getFixturesForDate(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            log.warn("Nieprawidlowy format daty dla API-Football: {}", date);
            return null;
        }

        if (rateLimitUntil != null && Instant.now().isBefore(rateLimitUntil)) {
            log.debug("Zapytania do API-Football wstrzymane po błędzie 429.");
            return null;
        }

        CacheEntry entry = cache.get(date);
        if (entry != null && Instant.now().isBefore(entry.expiryTime)) {
            return entry.data;
        }

        try {
            String year = date.substring(0, 4);
            String uri = API_BASE + "/fixtures?date=" + date + "&league=" + WORLD_CUP_ID + "&season=" + year;
            this.lastRequestUrl = uri;
            this.lastError = null;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("x-apisports-key", apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            this.lastResponseStatus = response.statusCode();

            if (response.statusCode() == 429) {
                log.warn("Przekroczono limit zapytan API-Football (HTTP 429). Blokada na 1h.");
                rateLimitUntil = Instant.now().plus(Duration.ofHours(1));
                this.lastError = "HTTP 429 Rate Limit";
                return null;
            }

            if (response.statusCode() != 200) {
                log.warn("API-Football zwrocil status {}: {}", response.statusCode(), response.body());
                this.lastError = "HTTP " + response.statusCode() + " " + response.body();
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            this.lastResponse = root;
            JsonNode data = root.get("response");
            
            if (data != null && !data.isNull()) {
                cache.put(date, new CacheEntry(data, Instant.now().plus(Duration.ofMinutes(15))));
            } else {
                this.lastError = "Zwrócono 200 OK, ale brak węzła 'response'";
            }
            return data;
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac terminarza z API-Football dla {}: {}", date, e.getMessage());
            this.lastError = "Exception: " + e.getMessage();
            return null;
        }
    }
}
