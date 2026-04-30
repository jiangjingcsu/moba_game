package com.moba.battleserver.manager;

import com.moba.battleserver.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class SettlementSystem {
    private static SettlementSystem instance;

    public SettlementSystem() {
    }

    public static synchronized SettlementSystem getInstance() {
        if (instance == null) {
            instance = new SettlementSystem();
        }
        return instance;
    }

    public BattleSettlementResult calculateSettlement(BattleRoom room) {
        BattleSession session = room.getSession();
        BattleSettlementResult result = new BattleSettlementResult();
        result.setBattleId(room.getBattleId());
        result.setDuration(session.getEndTime() - session.getStartTime());
        result.setMapId(session.getMapId());

        List<Integer> winningTeams = new ArrayList<>();
        List<Integer> losingTeams = new ArrayList<>();

        for (Map.Entry<Integer, BattleSession.Team> entry : session.getTeams().entrySet()) {
            if (entry.getValue().isDefeated()) {
                losingTeams.add(entry.getKey());
            } else {
                winningTeams.add(entry.getKey());
            }
        }

        result.setWinningTeams(winningTeams);
        result.setLosingTeams(losingTeams);

        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            long playerId = entry.getKey();
            BattlePlayer bp = entry.getValue();
            PlayerSettlement playerResult = new PlayerSettlement();
            playerResult.setPlayerId(playerId);
            playerResult.setHeroId(bp.getHeroId());
            playerResult.setTeamId(bp.getTeamId());
            playerResult.setLevel(bp.getLevel());
            playerResult.setKills(bp.getKillCount());
            playerResult.setDeaths(bp.getDeathCount());
            playerResult.setAssists(bp.getAssistCount());
            playerResult.setGoldEarned(bp.getGold());

            boolean isWinning = winningTeams.contains(bp.getTeamId());
            playerResult.setWinning(isWinning);

            int rankScoreChange = calculateRankScoreChange(bp, isWinning, session);
            playerResult.setRankScoreChange(rankScoreChange);

            int goldReward = calculateGoldReward(bp, isWinning);
            playerResult.setGoldReward(goldReward);

            int expReward = calculateExpReward(bp, isWinning);
            playerResult.setExpReward(expReward);

            result.getPlayerResults().add(playerResult);

            log.info("Settlement for player {}: win={}, KDA={}/{}/ {}, rankChange={:+}, goldReward={}, expReward={}",
                    playerId, isWinning, bp.getKillCount(), bp.getDeathCount(), bp.getAssistCount(),
                    rankScoreChange, goldReward, expReward);

            com.moba.battleserver.storage.BattleLogStorage.getInstance().submitBattleEvent(
                    room.getBattleId(),
                    "SETTLEMENT",
                    "player=" + playerId + "|win=" + isWinning + "|k=" + bp.getKillCount() +
                            "|d=" + bp.getDeathCount() + "|a=" + bp.getAssistCount() +
                            "|rankChange=" + rankScoreChange + "|goldReward=" + goldReward
            );
        }

        return result;
    }

    private int calculateRankScoreChange(BattlePlayer player, boolean isWinning, BattleSession session) {
        int baseChange = isWinning ? 20 : -15;

        float kda = (player.getKillCount() + player.getAssistCount() * 0.5f) / Math.max(1, player.getDeathCount());
        float kdaMultiplier = 1.0f + (kda - 2.0f) * 0.1f;
        kdaMultiplier = Math.max(0.5f, Math.min(1.5f, kdaMultiplier));

        return (int) (baseChange * kdaMultiplier);
    }

    private int calculateGoldReward(BattlePlayer player, boolean isWinning) {
        int baseGold = isWinning ? 200 : 100;
        return baseGold + player.getGold() / 10;
    }

    private int calculateExpReward(BattlePlayer player, boolean isWinning) {
        int baseExp = isWinning ? 150 : 80;
        return baseExp + player.getLevel() * 10;
    }

    @Data
    public static class BattleSettlementResult {
        private String battleId;
        private int mapId;
        private long duration;
        private List<Integer> winningTeams;
        private List<Integer> losingTeams;
        private List<PlayerSettlement> playerResults;

        public BattleSettlementResult() {
            this.winningTeams = new ArrayList<>();
            this.losingTeams = new ArrayList<>();
            this.playerResults = new ArrayList<>();
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{").append("\"battleId\":\"").append(battleId).append("\"");
            sb.append(",\"mapId\":").append(mapId);
            sb.append(",\"duration\":").append(duration);
            sb.append(",\"winningTeams\":").append(winningTeams);
            sb.append(",\"losingTeams\":").append(losingTeams);
            sb.append(",\"players\":[");
            for (int i = 0; i < playerResults.size(); i++) {
                PlayerSettlement p = playerResults.get(i);
                sb.append("{\"playerId\":").append(p.getPlayerId());
                sb.append(",\"heroId\":").append(p.getHeroId());
                sb.append(",\"teamId\":").append(p.getTeamId());
                sb.append(",\"winning\":").append(p.isWinning());
                sb.append(",\"kda\":").append(p.getKills()).append("/").append(p.getDeaths()).append("/").append(p.getAssists());
                sb.append(",\"rankChange\":").append(p.getRankScoreChange());
                sb.append(",\"goldReward\":").append(p.getGoldReward());
                sb.append(",\"expReward\":").append(p.getExpReward());
                sb.append("}");
                if (i < playerResults.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    @Data
    public static class PlayerSettlement {
        private long playerId;
        private int heroId;
        private int teamId;
        private boolean winning;
        private int level;
        private int kills;
        private int deaths;
        private int assists;
        private int goldEarned;
        private int rankScoreChange;
        private int goldReward;
        private int expReward;
    }
}
