package com.worldcup.service;

import com.worldcup.model.Match;
import com.worldcup.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BracketCalculationService {

    public record NextMatchPointer(int nextMatchNumber, boolean isTeam1) {}

    // ponytail: Uproszczony graf binarny (do podmiany na docelowy graf FIFA jak juz wystartuje R32)
    private static final Map<Integer, NextMatchPointer> PROGRESSION_MAP = Map.ofEntries(
        Map.entry(73, new NextMatchPointer(89, true)),
        Map.entry(74, new NextMatchPointer(89, false)),
        Map.entry(75, new NextMatchPointer(90, true)),
        Map.entry(76, new NextMatchPointer(90, false)),
        Map.entry(77, new NextMatchPointer(91, true)),
        Map.entry(78, new NextMatchPointer(91, false)),
        Map.entry(79, new NextMatchPointer(92, true)),
        Map.entry(80, new NextMatchPointer(92, false)),
        Map.entry(81, new NextMatchPointer(93, true)),
        Map.entry(82, new NextMatchPointer(93, false)),
        Map.entry(83, new NextMatchPointer(94, true)),
        Map.entry(84, new NextMatchPointer(94, false)),
        Map.entry(85, new NextMatchPointer(95, true)),
        Map.entry(86, new NextMatchPointer(95, false)),
        Map.entry(87, new NextMatchPointer(96, true)),
        Map.entry(88, new NextMatchPointer(96, false)),
        Map.entry(89, new NextMatchPointer(97, true)),
        Map.entry(90, new NextMatchPointer(97, false)),
        Map.entry(91, new NextMatchPointer(98, true)),
        Map.entry(92, new NextMatchPointer(98, false)),
        Map.entry(93, new NextMatchPointer(99, true)),
        Map.entry(94, new NextMatchPointer(99, false)),
        Map.entry(95, new NextMatchPointer(100, true)),
        Map.entry(96, new NextMatchPointer(100, false)),
        Map.entry(97, new NextMatchPointer(101, true)),
        Map.entry(98, new NextMatchPointer(101, false)),
        Map.entry(99, new NextMatchPointer(102, true)),
        Map.entry(100, new NextMatchPointer(102, false)),
        Map.entry(101, new NextMatchPointer(104, true)), // Zwyciezca SF1 do Finalu
        Map.entry(102, new NextMatchPointer(104, false)) // Zwyciezca SF2 do Finalu
    );

    // Mapowanie dla przegranych (tylko polfinaly -> mecz o 3. miejsce)
    private static final Map<Integer, NextMatchPointer> LOSER_PROGRESSION_MAP = Map.ofEntries(
        Map.entry(101, new NextMatchPointer(103, true)),  // Przegrany SF1 do 3P
        Map.entry(102, new NextMatchPointer(103, false))  // Przegrany SF2 do 3P
    );

    private static final Logger log = LoggerFactory.getLogger(BracketCalculationService.class);

    private final MatchRepository matchRepository;

    public BracketCalculationService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Transactional
    public void generateR32Bracket() {
        log.info("Generating Knockout Bracket R32 via static topological mapping...");

        // Mapowanie oficjalnych numerów meczów (73-88) do drużyn (znormalizowane dla aplikacji)
        // Topological order according to the official FIFA bracket for 2026
        Map<Integer, String[]> r32Mappings = Map.ofEntries(
            Map.entry(73, new String[]{"RPA", "Kanada"}),
            Map.entry(74, new String[]{"Niemcy", "Paragwaj"}),
            Map.entry(75, new String[]{"Holandia", "Maroko"}),
            Map.entry(76, new String[]{"Brazylia", "Japonia"}),
            Map.entry(77, new String[]{"Wybrzeze Kosci Sloniowej", "Norwegia"}),
            Map.entry(78, new String[]{"Francja", "Szwecja"}),
            Map.entry(79, new String[]{"Meksyk", "Ekwador"}),
            Map.entry(80, new String[]{"Anglia", "DR Konga"}),
            Map.entry(81, new String[]{"Belgia", "Senegal"}),
            Map.entry(82, new String[]{"USA", "Bosnia i Hercegowina"}),
            Map.entry(83, new String[]{"Hiszpania", "Austria"}),
            Map.entry(84, new String[]{"Portugalia", "Chorwacja"}),
            Map.entry(85, new String[]{"Szwajcaria", "Algieria"}),
            Map.entry(86, new String[]{"Australia", "Egipt"}),
            Map.entry(87, new String[]{"Argentyna", "Republika Zielonego Przyladka"}),
            Map.entry(88, new String[]{"Kolumbia", "Ghana"})
        );

        List<Match> r32Matches = matchRepository.findAllByPhase("KNOCKOUT").stream()
                .filter(m -> "R32".equals(m.getGroupName()))
                .collect(Collectors.toList());

        // Snapshot of current state for safety check
        long matchesToUpdate = r32Matches.stream()
                .filter(m -> m.getMatchNumber() != null && r32Mappings.containsKey(m.getMatchNumber()))
                .count();

        if (matchesToUpdate != 16) {
            throw new IllegalStateException("Oczekiwano zaktualizowania 16 slotow, ale w bazie znaleziono: " + matchesToUpdate + 
                                            ". Zabezpieczenie przed korupcja drabinki uruchomione.");
        }

        log.info("Zrzut stanu przed modyfikacja: w bazie znaleziono 16 poprawnych slotow R32.");

        for (Match m : r32Matches) {
            Integer matchNum = m.getMatchNumber();
            if (matchNum != null && r32Mappings.containsKey(matchNum)) {
                String t1 = r32Mappings.get(matchNum)[0];
                String t2 = r32Mappings.get(matchNum)[1];
                
                m.setTeam1Name(t1);
                m.setTeam1Code(Teams.CODES.get(t1));
                m.setTeam1En(Teams.EN_NAMES.get(t1));
                
                m.setTeam2Name(t2);
                m.setTeam2Code(Teams.CODES.get(t2));
                m.setTeam2En(Teams.EN_NAMES.get(t2));
                
                matchRepository.save(m);
            }
        }

        log.info("Knockout Bracket generation complete. Zaktualizowano wszystkie 16 meczow R32 ze stala topologia.");
    }

    @Transactional
    @SuppressWarnings("null")
    public void advanceTournamentBracket() {
        log.info("Checking completed knockout matches for bracket progression...");
        List<Match> allKnockouts = matchRepository.findAllByPhase("KNOCKOUT");
        
        Map<Integer, Match> byNumber = allKnockouts.stream()
                .filter(nm -> nm.getMatchNumber() != null)
                .collect(Collectors.toMap(Match::getMatchNumber, Function.identity()));
        
        for (Match m : allKnockouts) {
            // Mecz musi byc zakonczony (mamy wpisany wynik FT)
            if (m.getActualScore1() == null) {
                continue;
            }
            
            // --- Awanse zwyciezcow ---
            NextMatchPointer pointer = PROGRESSION_MAP.get(m.getMatchNumber());
            if (pointer != null) {
                String winnerName = getWinnerName(m);
                if (winnerName != null && !"TBD".equals(winnerName)) {
                    Match nextMatch = byNumber.get(pointer.nextMatchNumber());
                    if (nextMatch != null) {
                        updateMatchTeam(nextMatch, winnerName, pointer.isTeam1(), m.getMatchNumber());
                    }
                }
            }
            
            // --- Awanse przegranych (mecz o 3. miejsce) ---
            NextMatchPointer loserPointer = LOSER_PROGRESSION_MAP.get(m.getMatchNumber());
            if (loserPointer != null) {
                String loserName = getLoserName(m);
                if (loserName != null && !"TBD".equals(loserName)) {
                    Match nextMatch = byNumber.get(loserPointer.nextMatchNumber());
                    if (nextMatch != null) {
                        updateMatchTeam(nextMatch, loserName, loserPointer.isTeam1(), m.getMatchNumber());
                    }
                }
            }
        }
    }
    
    private void updateMatchTeam(Match nextMatch, String teamName, boolean isTeam1, int fromMatchNumber) {
        String code = Teams.CODES.get(teamName);
        String enName = Teams.EN_NAMES.get(teamName);
        
        if (code == null) {
            log.warn("Unknown team '{}' - skipping bracket advancement for match {}", teamName, fromMatchNumber);
            return;
        }

        boolean changed = false;
        if (isTeam1 && !teamName.equals(nextMatch.getTeam1Name())) {
            nextMatch.setTeam1Name(teamName);
            nextMatch.setTeam1Code(code);
            nextMatch.setTeam1En(enName);
            changed = true;
        } else if (!isTeam1 && !teamName.equals(nextMatch.getTeam2Name())) {
            nextMatch.setTeam2Name(teamName);
            nextMatch.setTeam2Code(code);
            nextMatch.setTeam2En(enName);
            changed = true;
        }
        
        if (changed) {
            log.info("Advanced {} from Match {} to Match {}", teamName, fromMatchNumber, nextMatch.getMatchNumber());
            matchRepository.save(nextMatch);
        }
    }
    
    private String getWinnerName(Match m) {
        if (m.getActualScore1() == null || m.getActualScore2() == null) return null;
        
        if (m.getActualScore1() > m.getActualScore2()) return m.getTeam1Name();
        if (m.getActualScore2() > m.getActualScore1()) return m.getTeam2Name();
        
        // ponytail: W przypadku remisu (rzuty karne) encja Match nie sledzi karnych.
        // Taki mecz wymaga recznego klikniecia w adminie zeby przepchnac zwyciezce.
        return null;
    }
    
    private String getLoserName(Match m) {
        if (m.getActualScore1() == null || m.getActualScore2() == null) return null;
        
        if (m.getActualScore1() < m.getActualScore2()) return m.getTeam1Name();
        if (m.getActualScore2() < m.getActualScore1()) return m.getTeam2Name();
        
        return null;
    }
}
