package com.worldcup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Typ (obstawiony wynik) konkretnego uzytkownika na konkretny mecz.
 * Para (username, matchId) jest unikalna - jeden typ na uzytkownika i mecz.
 * Przechowuje zarówno typ na przerwe (HT) jak i pelny czas (FT).
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"username", "matchId"}))
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private Long matchId;

    /** Wynik po pierwszej polowie (przerwa) – opcjonalny. */
    private Integer htScore1;
    private Integer htScore2;

    /** Wynik koncowy (pelny czas). */
    private Integer score1;
    private Integer score2;

    public Prediction() {
    }

    public Prediction(String username, Long matchId,
                      Integer htScore1, Integer htScore2,
                      Integer score1, Integer score2) {
        this.username = username;
        this.matchId = matchId;
        this.htScore1 = htScore1;
        this.htScore2 = htScore2;
        this.score1 = score1;
        this.score2 = score2;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public Integer getHtScore1() {
        return htScore1;
    }

    public void setHtScore1(Integer htScore1) {
        this.htScore1 = htScore1;
    }

    public Integer getHtScore2() {
        return htScore2;
    }

    public void setHtScore2(Integer htScore2) {
        this.htScore2 = htScore2;
    }

    public Integer getScore1() {
        return score1;
    }

    public void setScore1(Integer score1) {
        this.score1 = score1;
    }

    public Integer getScore2() {
        return score2;
    }

    public void setScore2(Integer score2) {
        this.score2 = score2;
    }
}
