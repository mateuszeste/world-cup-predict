package com.worldcup.dto;

import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.service.ScoringService;

/**
 * Mecz wraz z typem ZALOGOWANEGO uzytkownika.
 * Dzieki temu kazdy uzytkownik dostaje z API wylacznie wlasne wyniki.
 */
public class MatchView {

    private final Long id;
    private final String groupName;
    private final String phase;
    private final String date;
    private final String kickoffUtc;
    private final String team1Name;
    private final String team1Code;
    private final String team2Name;
    private final String team2Code;

    // Typ gracza – HT (przerwa)
    private final Integer htScore1;
    private final Integer htScore2;
    private final boolean htPlayed;

    // Typ gracza – FT (pelny czas)
    private final Integer score1;
    private final Integer score2;
    private final boolean played;

    // Rzeczywiste wyniki
    private final Integer actualHtScore1;
    private final Integer actualHtScore2;
    private final Integer actualScore1;
    private final Integer actualScore2;

    private final Integer pointsEarned; // null gdy mecz jeszcze nierozegrany

    public MatchView(Match m, Prediction p) {
        this.id = m.getId();
        this.groupName = m.getGroupName();
        this.phase = m.getPhase();
        this.date = m.getDate();
        this.kickoffUtc = m.getKickoffUtc();
        this.team1Name = m.getTeam1Name();
        this.team1Code = m.getTeam1Code();
        this.team2Name = m.getTeam2Name();
        this.team2Code = m.getTeam2Code();
        this.actualHtScore1 = m.getActualHtScore1();
        this.actualHtScore2 = m.getActualHtScore2();
        this.actualScore1 = m.getActualScore1();
        this.actualScore2 = m.getActualScore2();

        if (p != null && p.getScore1() != null && p.getScore2() != null) {
            this.score1 = p.getScore1();
            this.score2 = p.getScore2();
            this.played = true;
        } else {
            this.score1 = null;
            this.score2 = null;
            this.played = false;
        }

        if (p != null && p.getHtScore1() != null && p.getHtScore2() != null) {
            this.htScore1 = p.getHtScore1();
            this.htScore2 = p.getHtScore2();
            this.htPlayed = true;
        } else {
            this.htScore1 = null;
            this.htScore2 = null;
            this.htPlayed = false;
        }

        if (actualScore1 != null && actualScore2 != null) {
            if (played) {
                this.pointsEarned = ScoringService.points(
                        htPlayed ? htScore1 : null,
                        htPlayed ? htScore2 : null,
                        score1, score2,
                        actualHtScore1, actualHtScore2,
                        actualScore1, actualScore2);
            } else {
                this.pointsEarned = 0;
            }
        } else {
            this.pointsEarned = null;
        }
    }

    public Long getId() { return id; }
    public String getGroupName() { return groupName; }
    public String getPhase() { return phase; }
    public String getDate() { return date; }
    public String getKickoffUtc() { return kickoffUtc; }
    public String getTeam1Name() { return team1Name; }
    public String getTeam1Code() { return team1Code; }
    public String getTeam2Name() { return team2Name; }
    public String getTeam2Code() { return team2Code; }
    public Integer getHtScore1() { return htScore1; }
    public Integer getHtScore2() { return htScore2; }
    public boolean isHtPlayed() { return htPlayed; }
    public Integer getScore1() { return score1; }
    public Integer getScore2() { return score2; }
    public boolean isPlayed() { return played; }
    public Integer getActualHtScore1() { return actualHtScore1; }
    public Integer getActualHtScore2() { return actualHtScore2; }
    public Integer getActualScore1() { return actualScore1; }
    public Integer getActualScore2() { return actualScore2; }
    public Integer getPointsEarned() { return pointsEarned; }
}
