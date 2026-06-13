package com.worldcup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import java.util.List;

@RestController
public class TestEndpointController {

    private final RestClient restClient = RestClient.create("https://www.thesportsdb.com/api/v1/json/3");

    @GetMapping("/test-api")
    public String testApi() {
        try {
            String query = "Mexico_vs_South_Africa";
            SearchEventsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/searchevents.php").queryParam("e", query).build())
                    .retrieve()
                    .body(SearchEventsResponse.class);
            if (response == null) return "Response is null";
            if (response.event() == null) return "Response event is null";
            return "Found " + response.event().size() + " events! First event date: " + response.event().get(0).dateEvent() + ", status: " + response.event().get(0).strStatus();
        } catch (Exception e) {
            return "Error: " + e.getMessage() + "\n" + java.util.Arrays.toString(e.getStackTrace());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchEventsResponse(List<Event> event) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String dateEvent, String strStatus,
                         String intHomeScore, String intAwayScore,
                         String intScore1Half, String intScore2Half) {}
}
