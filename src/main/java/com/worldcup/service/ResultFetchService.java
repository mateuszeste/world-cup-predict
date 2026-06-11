package com.worldcup.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.TournamentState;
import com.worldcup.model.User;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TournamentStateRepository;
import com.worldcup.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pobiera rzeczywiste wyniki zakonczonych meczow z darmowego API TheSportsDB
 * i na ich podstawie przyznaje punkty wg nowego systemu:
 *   5 pkt – dokladny wynik FT, 2 pkt – trafiony kierunek, +1 pkt – dokladny wynik HT.
 */
@Service
public class ResultFetchService {

    private static final Logger log = LoggerFactory.getLogger(ResultFetchService.class);

    private static final String API_BASE = "https://www.thesportsdb.com/api/v1/json/3";
    // Mecz uznajemy za potencjalnie zakonczony ok. 2h po starcie
    private static final Duration MATCH_DURATION = Duration.ofHours(2);

    // Final MS 2026: 19.07.2026
    private static final String FINAL_DATE = "2026-07-19";
    private static final Instant FINAL_CHECK_FROM = Instant.parse("2026-07-19T22:00:00Z");
    private static final String WORLD_CUP_LEAGUE_ID = "4429"; // FIFA World Cup w TheSportsDB

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final RestClient restClient;

    public ResultFetchService(MatchRepository matchRepository,
                               PredictionRepository predictionRepository,
                               UserRepository userRepository,
                               TournamentStateRepository tournamentStateRepository) {
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.restClient = RestClient.create(API_BASE);
    }

    /** Co 5 minut: pobiera wyniki zakonczonych meczow, przyznaje punkty i sprawdza mistrza. */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 15 * 1000)
    public void refresh() {
        fetchMissingResults();
        awardPendingPoints();
        fetchAndAwardChampion();
    }

    /** Dla zakonczonych meczow bez wyniku probuje pobrac go z darmowego API. */
    public void fetchMissingResults() {
        Instant now = Instant.now();
        for (Match match : matchRepository.findAll()) {
            // Pomijamy mecze TBD (brak angielskich nazw), mecz testowy i juz wypelnione
            if ("TEST".equals(match.getGroupName())) continue;
            if (match.getActualScore1() != null) continue;
            if (match.getTeam1En() == null || match.getTeam1En().isBlank()) continue;

            Instant kickoff = Instant.parse(match.getKickoffUtc());
            if (now.isBefore(kickoff.plus(MATCH_DURATION))) continue; // mecz jeszcze trwa

            int[] result = fetchResult(match);
            if (result != null) {
                // result: [ftHome, ftAway, htHome, htAway] – HT moze byc null
                match.setActualScore1(result[0]);
                match.setActualScore2(result[1]);
                if (result.length == 4) {
                    match.setActualHtScore1(result[2]);
                    match.setActualHtScore2(result[3]);
                }
                matchRepository.save(match);
                log.info("Pobrano wynik {} - {}: {}:{} (HT: {}:{})",
                        match.getTeam1En(), match.getTeam2En(), result[0], result[1],
                        result.length == 4 ? result[2] : "?", result.length == 4 ? result[3] : "?");
            }
        }
    }

    /**
     * Dla meczow z wynikiem FT, ale bez przyznanych punktow – liczy i dopisuje punkty.
     * Metoda publiczna (wywolywana rowniez z AdminController po recznym ustawieniu wyniku).
     */
    public void awardPendingPoints() {
        for (Match match : matchRepository.findAll()) {
            if (match.isPointsAwarded()
                    || match.getActualScore1() == null
                    || match.getActualScore2() == null) {
                continue;
            }
            for (Prediction p : predictionRepository.findByMatchId(match.getId())) {
                if (p.getScore1() == null || p.getScore2() == null) continue;

                int pts = ScoringService.points(
                        p.getHtScore1(), p.getHtScore2(),
                        p.getScore1(), p.getScore2(),
                        match.getActualHtScore1(), match.getActualHtScore2(),
                        match.getActualScore1(), match.getActualScore2());

                if (pts == 0) continue;
                userRepository.findByUsernameIgnoreCase(p.getUsername()).ifPresent(user -> {
                    user.setPoints(user.getPoints() + pts);
                    userRepository.save(user);
                });
            }
            match.setPointsAwarded(true);
            matchRepository.save(match);
        }
    }

    /** Szuka wyniku meczu w TheSportsDB po angielskich nazwach druzyn i dacie. */
    private int[] fetchResult(Match match) {
        try {
            String query = match.getTeam1En().replace(" ", "_")
                    + "_vs_"
                    + match.getTeam2En().replace(" ", "_");

            SearchEventsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/searchevents.php").queryParam("e", query).build())
                    .retrieve()
                    .body(SearchEventsResponse.class);

            if (response == null || response.event() == null) return null;

            for (Event event : response.event()) {
                if (!hasStarted(event.strStatus())) continue;
                if (event.intHomeScore() == null || event.intAwayScore() == null) continue;
                if (!match.getDate().equals(event.dateEvent())) continue;

                int ftHome = Integer.parseInt(event.intHomeScore());
                int ftAway = Integer.parseInt(event.intAwayScore());

                // TheSportsDB moze zwrocic wynik HT w intScore1Half / intScore2Half
                if (event.intScore1Half() != null && event.intScore2Half() != null
                        && !event.intScore1Half().isBlank() && !event.intScore2Half().isBlank()) {
                    return new int[]{ftHome, ftAway,
                            Integer.parseInt(event.intScore1Half()),
                            Integer.parseInt(event.intScore2Half())};
                }
                return new int[]{ftHome, ftAway};
            }
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wyniku dla {} - {}: {}",
                    match.getTeam1En(), match.getTeam2En(), e.getMessage());
        }
        return null;
    }

    private static final List<String> NOT_STARTED_STATUSES =
            List.of("", "ns", "not started", "postponed", "cancelled", "tbd");

    private boolean hasStarted(String status) {
        return status != null && !NOT_STARTED_STATUSES.contains(status.toLowerCase());
    }

    /**
     * Po zakonczeniu finalu MS 2026 ustala zwyciezce z TheSportsDB i jednorazowo
     * dolicza {@value ScoringService#BONUS_POINTS} pkt uzytkownikom, ktorzy trafili.
     */
    public void fetchAndAwardChampion() {
        TournamentState state = tournamentStateRepository.getOrCreate();
        if (state.isChampionPointsAwarded()) return;

        if (state.getChampionCode() == null) {
            if (Instant.now().isBefore(FINAL_CHECK_FROM)) return;
            String championCode = fetchChampionCode();
            if (championCode == null) return;
            state.setChampionCode(championCode);
            tournamentStateRepository.save(state);
            log.info("Mistrz MS 2026 ustalony: {}", championCode);
        }

        for (User user : userRepository.findAll()) {
            if (state.getChampionCode().equals(user.getChampionPick())) {
                user.setPoints(user.getPoints() + ScoringService.BONUS_POINTS);
                userRepository.save(user);
            }
        }
        state.setChampionPointsAwarded(true);
        tournamentStateRepository.save(state);
    }

    private String fetchChampionCode() {
        try {
            EventsDayResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/eventsday.php")
                            .queryParam("d", FINAL_DATE)
                            .queryParam("l", WORLD_CUP_LEAGUE_ID)
                            .build())
                    .retrieve()
                    .body(EventsDayResponse.class);

            if (response == null || response.events() == null) return null;

            for (DayEvent event : response.events()) {
                if (!isFinal(event.strEvent())) continue;
                if (event.intHomeScore() == null || event.intAwayScore() == null) continue;

                int home = Integer.parseInt(event.intHomeScore());
                int away = Integer.parseInt(event.intAwayScore());
                String winnerEn;
                if (home > away) {
                    winnerEn = event.strHomeTeam();
                } else if (away > home) {
                    winnerEn = event.strAwayTeam();
                } else {
                    continue; // remis – czekaj na aktualizacje API z wynikiem karnych
                }
                return Teams.ENGLISH_TO_CODE.get(winnerEn);
            }
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wyniku finalu MS 2026: {}", e.getMessage());
        }
        return null;
    }

    private boolean isFinal(String eventName) {
        if (eventName == null) return false;
        String name = eventName.toLowerCase();
        return name.contains("final") && !name.contains("semi")
                && !name.contains("3rd") && !name.contains("third place");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchEventsResponse(List<Event> event) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Event(String dateEvent, String strStatus,
                         String intHomeScore, String intAwayScore,
                         String intScore1Half, String intScore2Half) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EventsDayResponse(List<DayEvent> events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DayEvent(String strEvent, String strHomeTeam, String strAwayTeam,
                             String intHomeScore, String intAwayScore) {}
}
