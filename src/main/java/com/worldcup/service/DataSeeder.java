package com.worldcup.service;

import com.worldcup.model.Match;
import com.worldcup.repository.MatchRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Wypelnia baze prawdziwymi meczami MS 2026 – faza grupowa + faza pucharowa.
 * Terminarz wg oficjalnego losowania (FIFA/ESPN, grudzien 2025).
 * Mecze fazy pucharowej maja TBD jako nazwy druzyn – admin uzupelni po fazie grupowej.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final int EXPECTED_MATCH_COUNT = 72 + 1 + 32; // 72 grupowe + 1 testowy + 32 pucharowe = 105

    private final MatchRepository repository;

    public DataSeeder(MatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() >= EXPECTED_MATCH_COUNT) {
            return; // baza juz w pelni wypelniona
        }

        // Jesli baza jest czesciowo wypelniona (np. wersja bez fazy pucharowej), doczytaj reszte
        boolean needsGroup    = repository.count() == 0;
        boolean needsKnockout = repository.findAllByPhase("KNOCKOUT").isEmpty();

        List<Match> m = new ArrayList<>();

        if (needsGroup) {
            addGroupStage(m);
        }
        if (needsKnockout) {
            addKnockoutStage(m);
        }

        repository.saveAll(m);
    }

    // ============================================================
    //  FAZA GRUPOWA (72 mecze + 1 testowy)
    // ============================================================
    private void addGroupStage(List<Match> m) {
        // Godziny podane w czasie wschodnim USA (ET); metoda 'add' przelicza je na UTC.

        // ---- Kolejka 1 ----
        add(m, "2026-06-11T15:00", "A", "Meksyk", "RPA");
        add(m, "2026-06-11T22:00", "A", "Korea Pld.", "Czechy");

        add(m, "2026-06-12T15:00", "B", "Kanada", "Bosnia i Hercegowina");
        add(m, "2026-06-12T21:00", "D", "USA", "Paragwaj");

        add(m, "2026-06-13T15:00", "B", "Katar", "Szwajcaria");
        add(m, "2026-06-13T18:00", "C", "Brazylia", "Maroko");
        add(m, "2026-06-13T21:00", "C", "Haiti", "Szkocja");
        addLate(m, "2026-06-14T00:00", "D", "Australia", "Turcja");

        add(m, "2026-06-14T13:00", "E", "Niemcy", "Curacao");
        add(m, "2026-06-14T16:00", "F", "Holandia", "Japonia");
        add(m, "2026-06-14T19:00", "E", "Wybrzeze Kosci Sloniowej", "Ekwador");
        add(m, "2026-06-14T22:00", "F", "Szwecja", "Tunezja");

        add(m, "2026-06-15T13:00", "H", "Hiszpania", "Republika Zielonego Przyladka");
        add(m, "2026-06-15T18:00", "G", "Belgia", "Egipt");
        add(m, "2026-06-15T18:00", "H", "Arabia Saudyjska", "Urugwaj");
        addLate(m, "2026-06-16T00:00", "G", "Iran", "Nowa Zelandia");

        add(m, "2026-06-16T15:00", "I", "Francja", "Senegal");
        add(m, "2026-06-16T18:00", "I", "Irak", "Norwegia");
        add(m, "2026-06-16T21:00", "J", "Argentyna", "Algieria");
        addLate(m, "2026-06-17T00:00", "J", "Austria", "Jordania");

        add(m, "2026-06-17T13:00", "K", "Portugalia", "DR Konga");
        add(m, "2026-06-17T16:00", "L", "Anglia", "Chorwacja");
        add(m, "2026-06-17T19:00", "L", "Ghana", "Panama");
        add(m, "2026-06-17T22:00", "K", "Uzbekistan", "Kolumbia");

        // ---- Kolejka 2 ----
        add(m, "2026-06-18T12:00", "A", "Czechy", "RPA");
        add(m, "2026-06-18T15:00", "B", "Szwajcaria", "Bosnia i Hercegowina");
        add(m, "2026-06-18T18:00", "B", "Kanada", "Katar");
        add(m, "2026-06-18T23:00", "A", "Meksyk", "Korea Pld.");

        add(m, "2026-06-19T15:00", "D", "USA", "Australia");
        add(m, "2026-06-19T18:00", "C", "Szkocja", "Maroko");
        add(m, "2026-06-19T21:00", "C", "Brazylia", "Haiti");
        addLate(m, "2026-06-20T00:00", "D", "Turcja", "Paragwaj");

        add(m, "2026-06-20T13:00", "F", "Holandia", "Szwecja");
        add(m, "2026-06-20T16:00", "E", "Niemcy", "Wybrzeze Kosci Sloniowej");
        add(m, "2026-06-20T20:00", "E", "Ekwador", "Curacao");
        addLate(m, "2026-06-21T00:00", "F", "Tunezja", "Japonia");

        add(m, "2026-06-21T12:00", "H", "Hiszpania", "Arabia Saudyjska");
        add(m, "2026-06-21T15:00", "G", "Belgia", "Iran");
        add(m, "2026-06-21T18:00", "H", "Urugwaj", "Republika Zielonego Przyladka");
        add(m, "2026-06-21T21:00", "G", "Nowa Zelandia", "Egipt");

        add(m, "2026-06-22T13:00", "J", "Argentyna", "Austria");
        add(m, "2026-06-22T17:00", "I", "Francja", "Irak");
        add(m, "2026-06-22T20:00", "I", "Norwegia", "Senegal");
        add(m, "2026-06-22T23:00", "J", "Jordania", "Algieria");

        add(m, "2026-06-23T13:00", "K", "Portugalia", "Uzbekistan");
        add(m, "2026-06-23T16:00", "L", "Anglia", "Ghana");
        add(m, "2026-06-23T19:00", "L", "Panama", "Chorwacja");
        add(m, "2026-06-23T22:00", "K", "Kolumbia", "DR Konga");

        // ---- Kolejka 3 ----
        add(m, "2026-06-24T15:00", "B", "Szwajcaria", "Kanada");
        add(m, "2026-06-24T15:00", "B", "Bosnia i Hercegowina", "Katar");
        add(m, "2026-06-24T18:00", "C", "Szkocja", "Brazylia");
        add(m, "2026-06-24T18:00", "C", "Maroko", "Haiti");
        add(m, "2026-06-24T21:00", "A", "Czechy", "Meksyk");
        add(m, "2026-06-24T21:00", "A", "RPA", "Korea Pld.");

        add(m, "2026-06-25T16:00", "E", "Ekwador", "Niemcy");
        add(m, "2026-06-25T16:00", "E", "Curacao", "Wybrzeze Kosci Sloniowej");
        add(m, "2026-06-25T19:00", "F", "Japonia", "Szwecja");
        add(m, "2026-06-25T19:00", "F", "Tunezja", "Holandia");
        add(m, "2026-06-25T22:00", "D", "Turcja", "USA");
        add(m, "2026-06-25T22:00", "D", "Paragwaj", "Australia");

        add(m, "2026-06-26T15:00", "I", "Norwegia", "Francja");
        add(m, "2026-06-26T15:00", "I", "Senegal", "Irak");
        add(m, "2026-06-26T20:00", "H", "Republika Zielonego Przyladka", "Arabia Saudyjska");
        add(m, "2026-06-26T20:00", "H", "Urugwaj", "Hiszpania");
        add(m, "2026-06-26T23:00", "G", "Egipt", "Iran");
        add(m, "2026-06-26T23:00", "G", "Nowa Zelandia", "Belgia");

        add(m, "2026-06-27T17:00", "L", "Panama", "Anglia");
        add(m, "2026-06-27T17:00", "L", "Chorwacja", "Ghana");
        add(m, "2026-06-27T19:30", "K", "Kolumbia", "Portugalia");
        add(m, "2026-06-27T19:30", "K", "DR Konga", "Uzbekistan");
        add(m, "2026-06-27T22:00", "J", "Algieria", "Austria");
        add(m, "2026-06-27T22:00", "J", "Jordania", "Argentyna");

        // ---- Mecz testowy (do weryfikacji API) ----
        String testKickoff = "2026-06-10T19:45:00Z";
        m.add(new Match("TEST", "GROUP", "2026-06-10", testKickoff,
                "Portugalia", code("Portugalia"), enName("Portugalia"),
                "Nigeria", code("Nigeria"), enName("Nigeria")));
    }

    // ============================================================
    //  FAZA PUCHAROWA (32 mecze) – druzyny TBD
    // ============================================================
    private void addKnockoutStage(List<Match> m) {
        // Przyblizone godziny start UTC; admin uzupelni nazwy druzyn po fazie grupowej.
        // Daty wg oficjalnego harmonogramu FIFA (kolejnosc meczow symboliczna).

        // ---- 1/32 (Round of 32) – 16 meczow, 1–5 lipca 2026 ----
        addKO(m, "R32", "2026-07-01", "2026-07-01T22:00:00Z");
        addKO(m, "R32", "2026-07-01", "2026-07-02T01:00:00Z");
        addKO(m, "R32", "2026-07-02", "2026-07-02T22:00:00Z");
        addKO(m, "R32", "2026-07-02", "2026-07-03T01:00:00Z");
        addKO(m, "R32", "2026-07-03", "2026-07-03T22:00:00Z");
        addKO(m, "R32", "2026-07-03", "2026-07-04T01:00:00Z");
        addKO(m, "R32", "2026-07-04", "2026-07-04T20:00:00Z");
        addKO(m, "R32", "2026-07-04", "2026-07-04T23:00:00Z");
        addKO(m, "R32", "2026-07-05", "2026-07-05T00:00:00Z");
        addKO(m, "R32", "2026-07-05", "2026-07-05T03:00:00Z");
        addKO(m, "R32", "2026-07-05", "2026-07-05T22:00:00Z");
        addKO(m, "R32", "2026-07-05", "2026-07-06T01:00:00Z");
        addKO(m, "R32", "2026-07-06", "2026-07-06T22:00:00Z");
        addKO(m, "R32", "2026-07-06", "2026-07-07T01:00:00Z");
        addKO(m, "R32", "2026-07-07", "2026-07-07T22:00:00Z");
        addKO(m, "R32", "2026-07-07", "2026-07-08T01:00:00Z");

        // ---- 1/16 (Round of 16) – 8 meczow, 9–12 lipca 2026 ----
        addKO(m, "R16", "2026-07-09", "2026-07-09T22:00:00Z");
        addKO(m, "R16", "2026-07-10", "2026-07-10T01:00:00Z");
        addKO(m, "R16", "2026-07-10", "2026-07-10T22:00:00Z");
        addKO(m, "R16", "2026-07-11", "2026-07-11T01:00:00Z");
        addKO(m, "R16", "2026-07-11", "2026-07-11T22:00:00Z");
        addKO(m, "R16", "2026-07-12", "2026-07-12T01:00:00Z");
        addKO(m, "R16", "2026-07-12", "2026-07-12T22:00:00Z");
        addKO(m, "R16", "2026-07-13", "2026-07-13T01:00:00Z");

        // ---- Cwiercfinaly (QF) – 4 mecze, 14–15 lipca 2026 ----
        addKO(m, "QF", "2026-07-14", "2026-07-14T22:00:00Z");
        addKO(m, "QF", "2026-07-15", "2026-07-15T01:00:00Z");
        addKO(m, "QF", "2026-07-15", "2026-07-15T22:00:00Z");
        addKO(m, "QF", "2026-07-16", "2026-07-16T01:00:00Z");

        // ---- Polfinal (SF) – 2 mecze, 17 i 18 lipca 2026 ----
        addKO(m, "SF", "2026-07-17", "2026-07-17T22:00:00Z");
        addKO(m, "SF", "2026-07-18", "2026-07-18T22:00:00Z");

        // ---- Mecz o 3. miejsce ----
        addKO(m, "3P", "2026-07-18", "2026-07-19T00:30:00Z");

        // ---- Final ----
        addKO(m, "F",  "2026-07-19", "2026-07-19T22:00:00Z");
    }

    // ============================================================
    //  HELPERY
    // ============================================================

    /** Grupowy mecz o godzinie ET (przelicza na UTC). */
    private void add(List<Match> list, String etDateTime, String group, String home, String away) {
        register(list, etDateTime, group, home, away, false);
    }

    /** Grupowy mecz o 00:00 ET liczony do poprzedniego dnia meczowego. */
    private void addLate(List<Match> list, String etDateTime, String group, String home, String away) {
        register(list, etDateTime, group, home, away, true);
    }

    private void register(List<Match> list, String etDateTime, String group,
                          String home, String away, boolean prevDaySlate) {
        LocalDateTime ldt = LocalDateTime.parse(etDateTime);
        String kickoffUtc = ldt.atZone(ZoneId.of("America/New_York")).toInstant().toString();
        LocalDate slate = prevDaySlate
                ? ldt.atZone(ZoneId.of("America/New_York")).toInstant()
                        .atZone(ZoneId.of("UTC")).toLocalDate()
                : ldt.toLocalDate();
        list.add(new Match(group, "GROUP", slate.toString(), kickoffUtc,
                home, code(home), enName(home),
                away, code(away), enName(away)));
    }

    /** Mecz pucharowy z TBD druzyny. Kickoff podany w UTC. */
    private void addKO(List<Match> list, String stage, String date, String kickoffUtc) {
        list.add(new Match(stage, "KNOCKOUT", date, kickoffUtc,
                "TBD", "xx", "",
                "TBD", "xx", ""));
    }

    private String code(String team) {
        String c = Teams.CODES.get(team);
        if (c == null) throw new IllegalStateException("Brak kodu flagi dla druzyny: " + team);
        return c;
    }

    private String enName(String team) {
        String en = Teams.EN_NAMES.get(team);
        if (en == null) throw new IllegalStateException("Brak angielskiej nazwy dla druzyny: " + team);
        return en;
    }
}
