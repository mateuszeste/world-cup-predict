package com.worldcup.service;

/**
 * Wspolna logika punktacji typow:
 *   5 pkt – dokladny wynik FT (pelny czas),
 *   2 pkt – trafiony kierunek FT (zwyciestwo / remis),
 *  +1 pkt – premia za dokladny wynik HT (przerwa),
 *   0 pkt – zly kierunek FT.
 *
 * Typy na mistrza turnieju i krola strzelcow: po 15 pkt.
 */
public final class ScoringService {

    private ScoringService() {
    }

    /** Punkty za trafiony typ mistrza turnieju lub krola strzelcow. */
    public static final int BONUS_POINTS = 15;

    /**
     * Punkty FT + opcjonalna premia HT.
     * Przekaz null jako predHt1/predHt2 lub actualHt1/actualHt2, jesli typ/wynik HT niedostepny.
     */
    public static int points(Integer predHt1, Integer predHt2,
                             int predFt1, int predFt2,
                             Integer actualHt1, Integer actualHt2,
                             int actualFt1, int actualFt2) {
        int ftPts;
        if (predFt1 == actualFt1 && predFt2 == actualFt2) {
            ftPts = 5;
        } else {
            int predDir   = Integer.signum(predFt1  - predFt2);
            int actualDir = Integer.signum(actualFt1 - actualFt2);
            ftPts = predDir == actualDir ? 2 : 0;
        }

        int htBonus = 0;
        if (predHt1 != null && predHt2 != null && actualHt1 != null && actualHt2 != null) {
            if (predHt1.equals(actualHt1) && predHt2.equals(actualHt2)) {
                htBonus = 1;
            }
        }

        return ftPts + htBonus;
    }

    /**
     * Uproszczona wersja bez HT (gdy gracz nie podal typu HT lub wynik HT niedostepny).
     */
    public static int points(int predFt1, int predFt2, int actualFt1, int actualFt2) {
        return points(null, null, predFt1, predFt2, null, null, actualFt1, actualFt2);
    }
}
