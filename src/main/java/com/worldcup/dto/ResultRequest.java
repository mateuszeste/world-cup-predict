package com.worldcup.dto;

/**
 * Cialo zadania PUT przy zapisie typu gracza na mecz.
 * htScore1/htScore2 – typ na przerwe (opcjonalny).
 * score1/score2     – typ na pelny czas; null oznacza wyczyszczenie calego typu.
 */
public class ResultRequest {

    private Integer htScore1;
    private Integer htScore2;
    private Integer score1;
    private Integer score2;

    public Integer getHtScore1() { return htScore1; }
    public void setHtScore1(Integer htScore1) { this.htScore1 = htScore1; }

    public Integer getHtScore2() { return htScore2; }
    public void setHtScore2(Integer htScore2) { this.htScore2 = htScore2; }

    public Integer getScore1() { return score1; }
    public void setScore1(Integer score1) { this.score1 = score1; }

    public Integer getScore2() { return score2; }
    public void setScore2(Integer score2) { this.score2 = score2; }
}
