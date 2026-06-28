package com.worldcup.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pobiera rzeczywiste wyniki zakonczonych meczow z darmowego API TheSportsDB
 * i na ich podstawie przyznaje punkty wg nowego systemu:
 *   5 pkt – dokladny wynik FT, 2 pkt – trafiony kierunek, +1 pkt – dokladny wynik HT.
 */
@Service
public class ResultFetchService {

    private static final Logger log = LoggerFactory.getLogger(ResultFetchService.class);

    private static final String API_BASE = "https://www.thesportsdb.com/api/v1/json/3";
    // Opcjonalna optymalizacja: Mecz trwa ~90 min + 15 min przerwy.
    // Aby nie marnować zapytań do API, zaczynamy sprawdzać wyniki
    // dopiero 30 min po rozpoczęciu (tj. w połowie pierwszej połowy).
    // Poprawność i tak gwarantuje weryfikacja statusu w isFinished().
    private static final Duration MATCH_DURATION = Duration.ofMinutes(30);

    // Final MS 2026: 19.07.2026
    private static final String FINAL_DATE = "2026-07-19";
    private static final Instant FINAL_CHECK_FROM = Instant.parse("2026-07-19T22:00:00Z");
    private static final String WORLD_CUP_LEAGUE_ID = "4429"; // FIFA World Cup w TheSportsDB

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final RestClient restClient;
    private final ApiFootballClient apiFootballClient;
    private final BracketCalculationService bracketCalculationService;

    public ResultFetchService(MatchRepository matchRepository,
                               PredictionRepository predictionRepository,
                               UserRepository userRepository,
                               TournamentStateRepository tournamentStateRepository,
                               ApiFootballClient apiFootballClient,
                               BracketCalculationService bracketCalculationService) {
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.restClient = RestClient.create(API_BASE);
        this.apiFootballClient = apiFootballClient;
        this.bracketCalculationService = bracketCalculationService;
    }

    /** Co 5 minut: pobiera wyniki zakonczonych meczow, przyznaje punkty i sprawdza mistrza. */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 15 * 1000)
    public void refresh() {
        fetchMissingResults();
        awardPendingPoints();
        bracketCalculationService.advanceTournamentBracket();
        fetchAndAwardChampion();
    }

    public void fetchMissingResults() {
        Instant now = Instant.now();
        for (Match match : matchRepository.findAll()) {
            // Pomijamy mecze TBD (brak angielskich nazw), mecz testowy i juz wypelnione
            if ("TEST".equals(match.getGroupName())) continue;
            if (match.getHtFetchAttempts() != null && match.getHtFetchAttempts() >= 3) continue;
            if (match.getActualScore1() != null && match.getActualHtScore1() != null) continue;
            if (match.getTeam1En() == null || match.getTeam1En().isBlank()) continue;

            Instant kickoff = Instant.parse(match.getKickoffUtc());
            if (now.isBefore(kickoff.plus(MATCH_DURATION))) continue; // mecz jeszcze trwa (optymalizacja)

            int[] result = null;
            if (match.getActualScore1() == null) {
                result = fetchFromTheSportsDB(match);
                if (result != null) {
                    boolean changed = false;
                    if (!Objects.equals(match.getActualScore1(), result[0]) || 
                        !Objects.equals(match.getActualScore2(), result[1])) {
                        match.setActualScore1(result[0]);
                        match.setActualScore2(result[1]);
                        changed = true;
                    }
                    if (result.length == 4) {
                        if (!Objects.equals(match.getActualHtScore1(), result[2]) || 
                            !Objects.equals(match.getActualHtScore2(), result[3])) {
                            match.setActualHtScore1(result[2]);
                            match.setActualHtScore2(result[3]);
                            changed = true;
                        }
                    }
                    if (changed) {
                        matchRepository.save(match);
                        log.info("Pobrano/zaktualizowano wynik z TheSportsDB {} - {}: {}:{} (HT: {}:{})",
                                match.getTeam1En(), match.getTeam2En(), result[0], result[1],
                                result.length == 4 ? result[2] : "?", result.length == 4 ? result[3] : "?");
                    }
                }
            } else {
                result = new int[]{match.getActualScore1(), match.getActualScore2()};
            }

            // Mamy wynik FT, ale brakuje HT - wywolujemy API-Football
            if (result != null && match.getActualHtScore1() == null) {
                int[] sofaResult = apiFootballClient.fetchResult(match.getTeam1En(), match.getTeam2En(), match.getDate());
                if (sofaResult != null && sofaResult.length == 4) {
                    log.info("Pobrano wynik z API-Football: {} - {} (HT: {}:{})", match.getTeam1En(), match.getTeam2En(), sofaResult[2], sofaResult[3]);
                    
                    // Jesli mecz mial juz rozdane punkty (za same FT), cofamy je
                    retractPointsForMatch(match);
                    
                    match.setActualHtScore1(sofaResult[2]);
                    match.setActualHtScore2(sofaResult[3]);
                    match.setPointsAwarded(false); // wymusza poprawne zliczenie punktow ponownie (tym razem z HT) w awardPendingPoints()
                    matchRepository.save(match);
                } else if (sofaResult == null && apiFootballClient.isEnabled()) {
                    match.setHtFetchAttempts((match.getHtFetchAttempts() == null ? 0 : match.getHtFetchAttempts()) + 1);
                    matchRepository.save(match);
                }
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

            // Upewnijmy sie, ze wykorzystalismy proby z API-Football zanim rozdamy punkty bez HT
            if (match.getActualHtScore1() == null) {
                int attempts = match.getHtFetchAttempts() != null ? match.getHtFetchAttempts() : 0;
                if (attempts < 3) continue;
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



    private int[] fetchFromTheSportsDB(Match match) {
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
                if (!isFinished(event.strStatus())) continue;
                if (event.intHomeScore() == null || event.intAwayScore() == null) continue;
                if (!match.getDate().equals(event.dateEvent()) && !match.getDate().equals(event.dateEventLocal())) continue;

                int ftHome = Integer.parseInt(event.intHomeScore());
                int ftAway = Integer.parseInt(event.intAwayScore());

                if (event.intScore1Half() != null && event.intScore2Half() != null
                        && !event.intScore1Half().isBlank() && !event.intScore2Half().isBlank()) {
                    return new int[]{ftHome, ftAway,
                            Integer.parseInt(event.intScore1Half()),
                            Integer.parseInt(event.intScore2Half())};
                }
                return new int[]{ftHome, ftAway};
            }
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wyniku z TheSportsDB dla {} - {}: {}",
                    match.getTeam1En(), match.getTeam2En(), e.getMessage());
        }
        return null;
    }

    private static final Set<String> FINISHED_STATUSES = Set.of(
            "ft", "aet", "ap", "finished", "pen", "matchfinished", "gamefinished"
    );

    boolean isFinished(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.replaceAll("[\\W_]", "").toLowerCase(Locale.ROOT);
        return FINISHED_STATUSES.contains(normalized);
    }

    /**
     * Odejmuje punkty za dany mecz od wszystkich uzytkownikow.
     * Uzywane przed zmiana wyniku, aby uniknac podwojnego naliczania.
     */
    public void retractPointsForMatch(Match match) {
        if (!match.isPointsAwarded()) return;
        if (match.getActualScore1() == null || match.getActualScore2() == null) return;
        for (Prediction p : predictionRepository.findByMatchId(match.getId())) {
            if (p.getScore1() == null || p.getScore2() == null) continue;
            int pts = ScoringService.points(
                    p.getHtScore1(), p.getHtScore2(),
                    p.getScore1(), p.getScore2(),
                    match.getActualHtScore1(), match.getActualHtScore2(),
                    match.getActualScore1(), match.getActualScore2());
            if (pts > 0) {
                userRepository.findByUsernameIgnoreCase(p.getUsername()).ifPresent(user -> {
                    user.setPoints(Math.max(0, user.getPoints() - pts));
                    userRepository.save(user);
                });
            }
        }
    }

    /**
     * Przelicza WSZYSTKIE punkty od zera. Naprawia wszelkie rozbieznosci
     * miedzy MatchView.pointsEarned a user.points w bazie.
     */
    public void recalculateAllPoints() {
        for (User user : userRepository.findAll()) {
            user.setPoints(0);
            userRepository.save(user);
        }

        for (Match match : matchRepository.findAll()) {
            if ("TEST".equals(match.getGroupName())) continue;
            if (match.getActualScore1() == null || match.getActualScore2() == null) continue;

            for (Prediction p : predictionRepository.findByMatchId(match.getId())) {
                if (p.getScore1() == null || p.getScore2() == null) continue;
                int pts = ScoringService.points(
                        p.getHtScore1(), p.getHtScore2(),
                        p.getScore1(), p.getScore2(),
                        match.getActualHtScore1(), match.getActualHtScore2(),
                        match.getActualScore1(), match.getActualScore2());
                if (pts > 0) {
                    userRepository.findByUsernameIgnoreCase(p.getUsername()).ifPresent(user -> {
                        user.setPoints(user.getPoints() + pts);
                        userRepository.save(user);
                    });
                }
            }
            match.setPointsAwarded(true);
            matchRepository.save(match);
        }

        TournamentState state = tournamentStateRepository.getOrCreate();
        if (state.getChampionCode() != null) {
            for (User user : userRepository.findAll()) {
                if (state.getChampionCode().equals(user.getChampionPick())) {
                    user.setPoints(user.getPoints() + ScoringService.BONUS_POINTS);
                    userRepository.save(user);
                }
            }
            state.setChampionPointsAwarded(true);
            tournamentStateRepository.save(state);
        }
        if (state.getTopScorerName() != null && !state.getTopScorerName().isEmpty()) {
            for (User user : userRepository.findAll()) {
                if (state.getTopScorerName().equalsIgnoreCase(user.getTopScorerPick())) {
                    user.setPoints(user.getPoints() + ScoringService.BONUS_POINTS);
                    userRepository.save(user);
                }
            }
            state.setTopScorerPointsAwarded(true);
            tournamentStateRepository.save(state);
        }
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
    public record SearchEventsResponse(@JsonProperty("event") List<Event> event) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            @JsonProperty("dateEvent") String dateEvent,
            @JsonProperty("dateEventLocal") String dateEventLocal,
            @JsonProperty("strStatus") String strStatus,
            @JsonProperty("intHomeScore") String intHomeScore,
            @JsonProperty("intAwayScore") String intAwayScore,
            @JsonProperty("intScore1Half") String intScore1Half,
            @JsonProperty("intScore2Half") String intScore2Half) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventsDayResponse(@JsonProperty("events") List<DayEvent> events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DayEvent(
            @JsonProperty("strEvent") String strEvent,
            @JsonProperty("strHomeTeam") String strHomeTeam,
            @JsonProperty("strAwayTeam") String strAwayTeam,
            @JsonProperty("intHomeScore") String intHomeScore,
            @JsonProperty("intAwayScore") String intAwayScore) {}
}
