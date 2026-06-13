package com.worldcup.dto;

import java.util.List;

/** Widok wszystkich typów wszystkich użytkowników (dla zakładki Typy). */
public class AllPredictionsView {

    private final List<MatchPredictionEntry> matchPredictions;
    private final List<ChampionPickEntry> championPicks;
    private final List<TopScorerPickEntry> topScorerPicks;

    public AllPredictionsView(List<MatchPredictionEntry> matchPredictions,
                              List<ChampionPickEntry> championPicks,
                              List<TopScorerPickEntry> topScorerPicks) {
        this.matchPredictions = matchPredictions;
        this.championPicks = championPicks;
        this.topScorerPicks = topScorerPicks;
    }

    public List<MatchPredictionEntry> getMatchPredictions() { return matchPredictions; }
    public List<ChampionPickEntry> getChampionPicks() { return championPicks; }
    public List<TopScorerPickEntry> getTopScorerPicks() { return topScorerPicks; }

    public static class MatchPredictionEntry {
        private final long matchId;
        private final String team1Name;
        private final String team2Name;
        private final String team1Code;
        private final String team2Code;
        private final String date;
        private final String kickoffUtc;
        private final String groupName;
        private final String phase;
        private final Integer actualScore1;
        private final Integer actualScore2;
        private final List<UserPick> picks;

        public MatchPredictionEntry(long matchId, String team1Name, String team2Name,
                                    String team1Code, String team2Code, String date,
                                    String kickoffUtc, String groupName, String phase,
                                    Integer actualScore1, Integer actualScore2,
                                    List<UserPick> picks) {
            this.matchId = matchId;
            this.team1Name = team1Name;
            this.team2Name = team2Name;
            this.team1Code = team1Code;
            this.team2Code = team2Code;
            this.date = date;
            this.kickoffUtc = kickoffUtc;
            this.groupName = groupName;
            this.phase = phase;
            this.actualScore1 = actualScore1;
            this.actualScore2 = actualScore2;
            this.picks = picks;
        }

        public long getMatchId() { return matchId; }
        public String getTeam1Name() { return team1Name; }
        public String getTeam2Name() { return team2Name; }
        public String getTeam1Code() { return team1Code; }
        public String getTeam2Code() { return team2Code; }
        public String getDate() { return date; }
        public String getKickoffUtc() { return kickoffUtc; }
        public String getGroupName() { return groupName; }
        public String getPhase() { return phase; }
        public Integer getActualScore1() { return actualScore1; }
        public Integer getActualScore2() { return actualScore2; }
        public List<UserPick> getPicks() { return picks; }
    }

    public static class UserPick {
        private final String username;
        private final Integer htScore1;
        private final Integer htScore2;
        private final Integer score1;
        private final Integer score2;

        public UserPick(String username, Integer htScore1, Integer htScore2,
                        Integer score1, Integer score2) {
            this.username = username;
            this.htScore1 = htScore1;
            this.htScore2 = htScore2;
            this.score1 = score1;
            this.score2 = score2;
        }

        public String getUsername() { return username; }
        public Integer getHtScore1() { return htScore1; }
        public Integer getHtScore2() { return htScore2; }
        public Integer getScore1() { return score1; }
        public Integer getScore2() { return score2; }
    }

    public static class ChampionPickEntry {
        private final String username;
        private final String teamCode;
        private final String teamName;

        public ChampionPickEntry(String username, String teamCode, String teamName) {
            this.username = username;
            this.teamCode = teamCode;
            this.teamName = teamName;
        }

        public String getUsername() { return username; }
        public String getTeamCode() { return teamCode; }
        public String getTeamName() { return teamName; }
    }

    public static class TopScorerPickEntry {
        private final String username;
        private final String playerName;

        public TopScorerPickEntry(String username, String playerName) {
            this.username = username;
            this.playerName = playerName;
        }

        public String getUsername() { return username; }
        public String getPlayerName() { return playerName; }
    }
}
