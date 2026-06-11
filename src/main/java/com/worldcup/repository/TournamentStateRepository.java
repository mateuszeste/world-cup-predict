package com.worldcup.repository;

import com.worldcup.model.TournamentState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentStateRepository extends JpaRepository<TournamentState, Long> {

    /** Zwraca jedyny wiersz stanu turnieju, tworzac go przy pierwszym uzyciu. */
    default TournamentState getOrCreate() {
        return findById(1L).orElseGet(() -> save(new TournamentState()));
    }
}
