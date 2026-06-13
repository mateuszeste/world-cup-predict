import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

public class TestJson {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SearchEventsResponse response = mapper.readValue(new File("/tmp/test_api.json"), SearchEventsResponse.class);
        System.out.println("Response event list size: " + (response.event() != null ? response.event().size() : "null"));
        if (response.event() != null && !response.event().isEmpty()) {
            Event event = response.event().get(0);
            System.out.println("Status: " + event.strStatus());
            System.out.println("Home score: " + event.intHomeScore());
            System.out.println("Away score: " + event.intAwayScore());
            System.out.println("HT Home: " + event.intScore1Half());
            System.out.println("HT Away: " + event.intScore2Half());
        }
    }
}

record SearchEventsResponse(java.util.List<Event> event) {}

record Event(String dateEvent, String strStatus,
             String intHomeScore, String intAwayScore,
             String intScore1Half, String intScore2Half) {}
