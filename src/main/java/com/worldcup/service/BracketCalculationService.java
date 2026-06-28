package com.worldcup.service;

import com.worldcup.model.Match;
import com.worldcup.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BracketCalculationService {

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
}
