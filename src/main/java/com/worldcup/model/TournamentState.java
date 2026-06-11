package com.worldcup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Singleton (id=1) przechowujacy globalny stan turnieju:
 * – mistrz turnieju (kod ISO flagi),
 * – krol strzelcow (imie i nazwisko),
 * – flagi informujace, czy punkty bonusowe zostaly juz przyznane.
 */
@Entity
public class TournamentState {

    @Id
    private Long id = 1L;

    /** Kod ISO (flagcdn) faktycznego mistrza turnieju. Null dopoki final sie nie zakonczy. */
    private String championCode;
    private boolean championPointsAwarded;

    /** Imie i nazwisko faktycznego krola strzelcow. Null dopoki turniej sie nie zakonczy. */
    private String topScorerName;
    private boolean topScorerPointsAwarded;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChampionCode() { return championCode; }
    public void setChampionCode(String championCode) { this.championCode = championCode; }

    public boolean isChampionPointsAwarded() { return championPointsAwarded; }
    public void setChampionPointsAwarded(boolean championPointsAwarded) {
        this.championPointsAwarded = championPointsAwarded;
    }

    public String getTopScorerName() { return topScorerName; }
    public void setTopScorerName(String topScorerName) { this.topScorerName = topScorerName; }

    public boolean isTopScorerPointsAwarded() { return topScorerPointsAwarded; }
    public void setTopScorerPointsAwarded(boolean topScorerPointsAwarded) {
        this.topScorerPointsAwarded = topScorerPointsAwarded;
    }
}
