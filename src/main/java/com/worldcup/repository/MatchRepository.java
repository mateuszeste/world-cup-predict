package com.worldcup.repository;

import com.worldcup.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    // Mecze posortowane chronologicznie wg momentu startu (ISO UTC sortuje sie poprawnie alfabetycznie)
    List<Match> findAllByOrderByKickoffUtcAscIdAsc();

    // Pierwszy mecz turnieju (z pominieciem meczu testowego "TEST") - wyznacza moment blokady typu na mistrza
    Optional<Match> findFirstByGroupNameNotOrderByKickoffUtcAsc(String groupName);

    // Mecze wg fazy (GROUP / KNOCKOUT) - uzywane w DataSeeder do sprawdzania czy faza pucharowa juz dodana
    List<Match> findAllByPhase(String phase);
}
