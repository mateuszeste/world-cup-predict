package com.worldcup.dto;

import com.worldcup.service.ScoringService;

import java.util.List;

/** Stan typu na mistrza turnieju dla zalogowanego uzytkownika. */
public class ChampionView {

    private final List<TeamOption> teams;
    private final String pick;
    private final boolean locked;
    private final String actualChampion;
    private final Integer pointsEarned;

    public ChampionView(List<TeamOption> teams, String pick, boolean locked, String actualChampion) {
        this.teams = teams;
        this.pick = pick;
        this.locked = locked;
        this.actualChampion = actualChampion;
        if (actualChampion == null) {
            this.pointsEarned = null;
        } else {
            this.pointsEarned = actualChampion.equals(pick) ? ScoringService.BONUS_POINTS : 0;
        }
    }

    public List<TeamOption> getTeams() {
        return teams;
    }

    public String getPick() {
        return pick;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getActualChampion() {
        return actualChampion;
    }

    public Integer getPointsEarned() {
        return pointsEarned;
    }
}
