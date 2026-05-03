package com.moba.battle.manager;

import com.moba.battle.model.BattlePlayer;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.Player;
import com.moba.battle.storage.BattleLogStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SettlementSystem {

    private final BattleLogStorage battleLogStorage;

    public SettlementSystem(BattleLogStorage battleLogStorage) {
        this.battleLogStorage = battleLogStorage;
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
            long userId = entry.getKey();
            BattlePlayer bp = entry.getValue();
            PlayerSettlement playerResult = new PlayerSettlement();
            playerResult.setUserId(userId);
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

            log.info("玩家{}结算: 胜利={}, KDA={}/{}/{}, 积分变化={:+}, 金币奖励={}, 经验奖励={}",
                    userId, isWinning, bp.getKillCount(), bp.getDeathCount(), bp.getAssistCount(),
                    rankScoreChange, goldReward, expReward);

            battleLogStorage.submitBattleEvent(
                    room.getBattleId(),
                    "SETTLEMENT",
                    "player=" + userId + "|win=" + isWinning + "|k=" + bp.getKillCount() +
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
        private long battleId;
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

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        public String toJson() {
            try {
                Map<String, Object> result = new HashMap<>();
                result.put("battleId", battleId);
                result.put("mapId", mapId);
                result.put("duration", duration);
                result.put("winningTeams", winningTeams);
                result.put("losingTeams", losingTeams);

                List<Map<String, Object>> players = new ArrayList<>();
                for (PlayerSettlement p : playerResults) {
                    Map<String, Object> player = new HashMap<>();
                    player.put("userId", p.getUserId());
                    player.put("heroId", p.getHeroId());
                    player.put("teamId", p.getTeamId());
                    player.put("winning", p.isWinning());
                    player.put("kda", p.getKills() + "/" + p.getDeaths() + "/" + p.getAssists());
                    player.put("rankChange", p.getRankScoreChange());
                    player.put("goldReward", p.getGoldReward());
                    player.put("expReward", p.getExpReward());
                    players.add(player);
                }
                result.put("players", players);

                return OBJECT_MAPPER.writeValueAsString(result);
            } catch (Exception e) {
                return "{\"error\":\"serialization failed\"}";
            }
        }
    }

    @Data
    public static class PlayerSettlement {
        private long userId;
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
