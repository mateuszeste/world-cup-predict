package com.worldcup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Pojedynczy mecz MS 2026 (faza grupowa lub pucharowa).
 * Wyniki typowane przez uzytkownikow trzymane sa osobno w encji {@link Prediction}.
 * Rzeczywisty wynik (po zakonczeniu meczu) trzymany jest w polach actual*.
 */
@Entity
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String groupName;   // litera grupy (A..L) lub etap: R32, R16, QF, SF, 3P, F
    private String phase;       // "GROUP" | "KNOCKOUT"
    private String date;        // data "dnia meczowego" ISO, np. "2026-06-11"
    private String kickoffUtc;  // dokladny moment startu UTC, np. "2026-06-11T19:00:00Z"

    private String team1Name;
    private String team1Code;   // kod ISO do flagi, np. "pl" (lub "xx" gdy TBD)
    private String team1En;     // nazwa angielska (do wyszukiwania wyniku w API)
    private String team2Name;
    private String team2Code;
    private String team2En;

    private Integer actualHtScore1; // wynik przerwy (uzupelniany po meczu)
    private Integer actualHtScore2;
    private Integer actualScore1;   // wynik pelnego czasu
    private Integer actualScore2;
    private boolean pointsAwarded;  // czy punkty za ten mecz zostaly juz przyznane

    public Match() {
    }

    public Match(String groupName, String phase, String date, String kickoffUtc,
                 String team1Name, String team1Code, String team1En,
                 String team2Name, String team2Code, String team2En) {
        this.groupName = groupName;
        this.phase = phase;
        this.date = date;
        this.kickoffUtc = kickoffUtc;
        this.team1Name = team1Name;
        this.team1Code = team1Code;
        this.team1En = team1En;
        this.team2Name = team2Name;
        this.team2Code = team2Code;
        this.team2En = team2En;
    }

    public Long getId() { return id; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getKickoffUtc() { return kickoffUtc; }
    public void setKickoffUtc(String kickoffUtc) { this.kickoffUtc = kickoffUtc; }

    public String getTeam1Name() { return team1Name; }
    public void setTeam1Name(String team1Name) { this.team1Name = team1Name; }

    public String getTeam1Code() { return team1Code; }
    public void setTeam1Code(String team1Code) { this.team1Code = team1Code; }

    public String getTeam1En() { return team1En; }
    public void setTeam1En(String team1En) { this.team1En = team1En; }

    public String getTeam2Name() { return team2Name; }
    public void setTeam2Name(String team2Name) { this.team2Name = team2Name; }

    public String getTeam2Code() { return team2Code; }
    public void setTeam2Code(String team2Code) { this.team2Code = team2Code; }

    public String getTeam2En() { return team2En; }
    public void setTeam2En(String team2En) { this.team2En = team2En; }

    public Integer getActualHtScore1() { return actualHtScore1; }
    public void setActualHtScore1(Integer actualHtScore1) { this.actualHtScore1 = actualHtScore1; }

    public Integer getActualHtScore2() { return actualHtScore2; }
    public void setActualHtScore2(Integer actualHtScore2) { this.actualHtScore2 = actualHtScore2; }

    public Integer getActualScore1() { return actualScore1; }
    public void setActualScore1(Integer actualScore1) { this.actualScore1 = actualScore1; }

    public Integer getActualScore2() { return actualScore2; }
    public void setActualScore2(Integer actualScore2) { this.actualScore2 = actualScore2; }

    public boolean isPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(boolean pointsAwarded) { this.pointsAwarded = pointsAwarded; }
}
