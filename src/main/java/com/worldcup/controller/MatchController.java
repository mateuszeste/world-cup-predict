package com.worldcup.controller;

import com.worldcup.dto.ChampionRequest;
import com.worldcup.dto.ChampionView;
import com.worldcup.dto.LeaderboardEntry;
import com.worldcup.dto.MatchView;
import com.worldcup.dto.ResultRequest;
import com.worldcup.dto.TeamOption;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.TournamentState;
import com.worldcup.model.User;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TournamentStateRepository;
import com.worldcup.repository.UserRepository;
import com.worldcup.service.JwtService;
import com.worldcup.service.ResultFetchService;
import com.worldcup.service.Teams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MatchController {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final JwtService jwtService;
    private final ResultFetchService resultFetchService;

    /** Nazwa uzytkownika z uprawnieniami admina (konfigurowana przez env APP_ADMIN_USERNAME). */
    @Value("${app.admin.username:admin}")
    private String adminUsername;

    public MatchController(MatchRepository matchRepository,
                           PredictionRepository predictionRepository,
                           UserRepository userRepository,
                           TournamentStateRepository tournamentStateRepository,
                           JwtService jwtService,
                           ResultFetchService resultFetchService) {
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.jwtService = jwtService;
        this.resultFetchService = resultFetchService;
    }

    // ============================================================
    // MECZE – odczyt i typowanie
    // ============================================================

    /** Mecze wraz z typami ZALOGOWANEGO uzytkownika (nigdy cudzymi). */
    @GetMapping("/matches")
    public List<MatchView> getMatches(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);

        Map<Long, Prediction> mine = new HashMap<>();
        for (Prediction p : predictionRepository.findByUsername(username)) {
            mine.put(p.getMatchId(), p);
        }

        return matchRepository.findAllByOrderByKickoffUtcAscIdAsc().stream()
                .filter(m -> !"TEST".equals(m.getGroupName()))
                .map(m -> new MatchView(m, mine.get(m.getId())))
                .toList();
    }

    /** Zapis lub wyczyszczenie WLASNEGO typu na mecz (HT + FT). */
    @PutMapping("/matches/{id}")
    public ResponseEntity<MatchView> updatePrediction(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long id,
            @RequestBody ResultRequest request) {

        String username = requireUser(auth);

        Match match = matchRepository.findById(id).orElse(null);
        if (match == null) {
            return ResponseEntity.notFound().build();
        }

        // Blokada: po rozpoczeciu meczu nie mozna juz zmieniac typu
        if (Instant.now().isAfter(Instant.parse(match.getKickoffUtc()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean hasFt = request.getScore1() != null && request.getScore2() != null;
        boolean hasHt = request.getHtScore1() != null && request.getHtScore2() != null;

        if (!hasFt) {
            // Wyczyszczenie calego typu
            predictionRepository.findByUsernameAndMatchId(username, id)
                    .ifPresent(predictionRepository::delete);
            return ResponseEntity.ok(new MatchView(match, null));
        }

        Prediction prediction = predictionRepository
                .findByUsernameAndMatchId(username, id)
                .orElseGet(() -> new Prediction(username, id, null, null, null, null));

        prediction.setScore1(Math.max(0, request.getScore1()));
        prediction.setScore2(Math.max(0, request.getScore2()));

        if (hasHt) {
            prediction.setHtScore1(Math.max(0, request.getHtScore1()));
            prediction.setHtScore2(Math.max(0, request.getHtScore2()));
        } else {
            prediction.setHtScore1(null);
            prediction.setHtScore2(null);
        }

        predictionRepository.save(prediction);
        return ResponseEntity.ok(new MatchView(match, prediction));
    }

    // ============================================================
    // RANKING
    // ============================================================

    /** Ranking wszystkich zarejestrowanych uzytkownikow. */
    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> getLeaderboard(@RequestHeader(value = "Authorization", required = false) String auth) {
        requireUser(auth);
        return userRepository.findAllByOrderByPointsDescUsernameAsc().stream()
                .map(LeaderboardEntry::new)
                .toList();
    }

    // ============================================================
    // TYP NA MISTRZA TURNIEJU
    // ============================================================

    /** Stan typu na mistrza turnieju (lista druzyn, wlasny typ, blokada, rzeczywisty mistrz). */
    @GetMapping("/champion")
    public ChampionView getChampion(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        List<TeamOption> teams = Teams.CODES.entrySet().stream()
                .map(e -> new TeamOption(e.getValue(), e.getKey()))
                .sorted(Comparator.comparing(t -> t.getName()))
                .toList();

        TournamentState state = tournamentStateRepository.getOrCreate();
        return new ChampionView(teams, user.getChampionPick(), isChampionPickLocked(), state.getChampionCode());
    }

    /** Zapis lub wyczyszczenie WLASNEGO typu na mistrza turnieju. */
    @PutMapping("/champion")
    public ResponseEntity<ChampionView> updateChampion(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody ChampionRequest request) {

        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        if (isChampionPickLocked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String code = request.getCode();
        if (code != null && !Teams.CODES.containsValue(code)) {
            return ResponseEntity.badRequest().build();
        }

        user.setChampionPick(code);
        userRepository.save(user);

        return ResponseEntity.ok(getChampion(auth));
    }

    private boolean isChampionPickLocked() {
        return matchRepository.findFirstByGroupNameNotOrderByKickoffUtcAsc("TEST")
                .map(m -> !Instant.now().isBefore(Instant.parse(m.getKickoffUtc())))
                .orElse(false);
    }

    // ============================================================
    // TYP NA KROLA STRZELCOW
    // ============================================================

    /** Stan typu na krola strzelcow (wlasny typ, blokada, rzeczywisty krol strzelcow). */
    @GetMapping("/topscorer")
    public Map<String, Object> getTopScorer(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        TournamentState state = tournamentStateRepository.getOrCreate();
        return Map.of(
                "pick", user.getTopScorerPick() == null ? "" : user.getTopScorerPick(),
                "locked", isChampionPickLocked(),   // blokuje sie razem z typowaniem mistrza (start turnieju)
                "actualTopScorer", state.getTopScorerName() == null ? "" : state.getTopScorerName(),
                "bonusPoints", com.worldcup.service.ScoringService.BONUS_POINTS
        );
    }

    /** Zapis lub wyczyszczenie WLASNEGO typu na krola strzelcow. */
    @PutMapping("/topscorer")
    public ResponseEntity<Map<String, Object>> updateTopScorer(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {

        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        if (isChampionPickLocked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String pick = body.get("pick");
        if (pick != null) {
            pick = pick.trim();
            if (pick.length() > 60) {
                return ResponseEntity.badRequest().build();
            }
        }
        user.setTopScorerPick(pick == null || pick.isEmpty() ? null : pick);
        userRepository.save(user);

        return ResponseEntity.ok(getTopScorer(auth));
    }

    // ============================================================
    // ODSWIEZANIE WYNIKOW
    // ============================================================

    /** Recznie wymusza sprawdzenie wynikow zakonczonych meczow i przyznanie punktow. */
    @PostMapping("/results/refresh")
    public ResponseEntity<Void> refreshResults(@RequestHeader(value = "Authorization", required = false) String auth) {
        requireUser(auth);
        resultFetchService.refresh();
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // ADMIN – reczne ustawianie wynikow i druzyn
    // ============================================================

    /** Czy zalogowany uzytkownik jest adminem. */
    @GetMapping("/admin/check")
    public Map<String, Boolean> adminCheck(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);
        return Map.of("admin", adminUsername.equalsIgnoreCase(username));
    }

    /**
     * Admin: recznie ustaw wynik meczu (HT i/lub FT).
     * Uzywa sie gdy auto-fetch z API nie dziala (np. dla meczow pucharowych).
     * JSON: { "htScore1": 1, "htScore2": 0, "score1": 2, "score2": 1 }
     */
    @PostMapping("/admin/matches/{id}/result")
    public ResponseEntity<MatchView> adminSetResult(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long id,
            @RequestBody ResultRequest request) {

        requireAdmin(auth);

        Match match = matchRepository.findById(id).orElse(null);
        if (match == null) return ResponseEntity.notFound().build();

        if (request.getScore1() != null && request.getScore2() != null) {
            match.setActualScore1(Math.max(0, request.getScore1()));
            match.setActualScore2(Math.max(0, request.getScore2()));
        } else {
            // Wyczyszczenie wyniku – punkty cofniete sa przez recalc przy nastepnym award
            match.setActualScore1(null);
            match.setActualScore2(null);
            match.setPointsAwarded(false);
        }

        if (request.getHtScore1() != null && request.getHtScore2() != null) {
            match.setActualHtScore1(Math.max(0, request.getHtScore1()));
            match.setActualHtScore2(Math.max(0, request.getHtScore2()));
        } else {
            match.setActualHtScore1(null);
            match.setActualHtScore2(null);
        }

        matchRepository.save(match);

        // Natychmiastowe przyznanie punktow
        resultFetchService.awardPendingPoints();

        return ResponseEntity.ok(new MatchView(match, null));
    }

    /**
     * Admin: zaktualizuj nazwy druzyn w meczu pucharowym (gdy sa juz znane po fazie grupowej).
     * JSON: { "team1Name": "Polska", "team1Code": "pl", "team1En": "Poland",
     *         "team2Name": "Niemcy", "team2Code": "de", "team2En": "Germany" }
     */
    @PostMapping("/admin/matches/{id}/teams")
    public ResponseEntity<MatchView> adminSetTeams(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long id,
            @RequestBody Map<String, String> body) {

        requireAdmin(auth);

        Match match = matchRepository.findById(id).orElse(null);
        if (match == null) return ResponseEntity.notFound().build();

        if (body.containsKey("team1Name")) match.setTeam1Name(body.get("team1Name"));
        if (body.containsKey("team1Code")) match.setTeam1Code(body.get("team1Code"));
        if (body.containsKey("team1En"))   match.setTeam1En(body.get("team1En"));
        if (body.containsKey("team2Name")) match.setTeam2Name(body.get("team2Name"));
        if (body.containsKey("team2Code")) match.setTeam2Code(body.get("team2Code"));
        if (body.containsKey("team2En"))   match.setTeam2En(body.get("team2En"));
        if (body.containsKey("date"))      match.setDate(body.get("date"));
        if (body.containsKey("kickoffUtc")) match.setKickoffUtc(body.get("kickoffUtc"));

        matchRepository.save(match);
        return ResponseEntity.ok(new MatchView(match, null));
    }

    /**
     * Admin: ustaw rzeczywistego krola strzelcow i przyznaj bonusy.
     * JSON: { "name": "Cristiano Ronaldo" }
     */
    @PostMapping("/admin/topscorer")
    public ResponseEntity<Void> adminSetTopScorer(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {

        requireAdmin(auth);

        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) return ResponseEntity.badRequest().build();

        TournamentState state = tournamentStateRepository.getOrCreate();
        if (!state.isTopScorerPointsAwarded()) {
            state.setTopScorerName(name);
            // Przyznaj punkty wszystkim, ktorzy trafili
            for (User user : userRepository.findAll()) {
                if (name.equalsIgnoreCase(user.getTopScorerPick())) {
                    user.setPoints(user.getPoints() + com.worldcup.service.ScoringService.BONUS_POINTS);
                    userRepository.save(user);
                }
            }
            state.setTopScorerPointsAwarded(true);
            tournamentStateRepository.save(state);
        } else {
            // Tylko aktualizuj nazwe bez ponownego przyznawania punktow
            state.setTopScorerName(name);
            tournamentStateRepository.save(state);
        }

        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // HELPERY
    // ============================================================

    private String requireUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Brak tokenu");
        }
        try {
            return jwtService.validateAndGetUsername(authHeader.substring(7));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny");
        }
    }

    private void requireAdmin(String authHeader) {
        String username = requireUser(authHeader);
        if (!adminUsername.equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnien admina");
        }
    }
}
