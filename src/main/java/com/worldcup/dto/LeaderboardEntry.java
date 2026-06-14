package com.worldcup.dto;

import com.worldcup.model.User;

public class LeaderboardEntry {

    private String username;
    private int points;
    private int exactHits;
    private int directionHits;
    private int htHits;
    private int bonusPoints;

    public LeaderboardEntry(String username, int points, int exactHits, int directionHits, int htHits, int bonusPoints) {
        this.username = username;
        this.points = points;
        this.exactHits = exactHits;
        this.directionHits = directionHits;
        this.htHits = htHits;
        this.bonusPoints = bonusPoints;
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }

    public int getExactHits() {
        return exactHits;
    }

    public int getDirectionHits() {
        return directionHits;
    }

    public int getHtHits() {
        return htHits;
    }

    public int getBonusPoints() {
        return bonusPoints;
    }
}
