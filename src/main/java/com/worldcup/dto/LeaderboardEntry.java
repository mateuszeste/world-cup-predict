package com.worldcup.dto;

import com.worldcup.model.User;

public class LeaderboardEntry {

    private String username;
    private int points;

    public LeaderboardEntry(User user) {
        this.username = user.getUsername();
        this.points = user.getPoints();
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }
}
