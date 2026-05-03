package com.moba.business.handler;

import com.moba.business.service.QuestService;
import com.moba.common.constant.GameMode;
import com.moba.common.dto.BattleResultDTO;
import com.moba.common.dto.PlayerStatDTO;
import com.moba.common.dto.TeamStatDTO;
import com.moba.common.event.BattleEndEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class BattleSettlementHandler {

    private final QuestService questService;

    public void handleBattleEnd(BattleEndEvent event) {
        BattleResultDTO result = event.getResult();
        if (result == null) {
            log.warn("战斗结算事件结果为空, eventId={}", event.getEventId());
            return;
        }

        long battleId = result.getBattleId();
        log.info("开始处理战斗结算: battleId={}, gameMode={}, duration={}ms",
                battleId, result.getGameMode(), result.getDuration());

        List<PlayerStatDTO> players = result.getPlayers();
        if (players == null || players.isEmpty()) {
            log.warn("战斗结算玩家列表为空, battleId={}", battleId);
            return;
        }

        Map<Integer, TeamStatDTO> teamStats = result.getTeamStats();
        int winnerTeamId = result.getWinnerTeamId();
        GameMode gameMode = result.getGameMode();

        List<PlayerStatDTO> realPlayers = players.stream()
                .filter(p -> !p.isAI())
                .toList();

        if (realPlayers.isEmpty()) {
            log.info("战斗无真实玩家参与, battleId={}", battleId);
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (PlayerStatDTO player : realPlayers) {
            try {
                processPlayerSettlement(player, gameMode, winnerTeamId, teamStats, result.getDuration());
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("玩家战斗结算处理异常: userId={}, battleId={}", player.getUserId(), battleId, e);
            }
        }

        log.info("战斗结算处理完成: battleId={}, 总玩家={}, 成功={}, 失败={}",
                battleId, realPlayers.size(), successCount, failCount);
    }

    private void processPlayerSettlement(PlayerStatDTO player, GameMode gameMode,
                                         int winnerTeamId, Map<Integer, TeamStatDTO> teamStats,
                                         long durationMs) {
        long userId = player.getUserId();
        boolean isWin = player.isWinner();
        int teamId = player.getTeamId();

        int towersDestroyed = 0;
        int barracksDestroyed = 0;
        if (teamStats != null && teamStats.containsKey(teamId)) {
            TeamStatDTO myTeamStat = teamStats.get(teamId);
            towersDestroyed = myTeamStat.getTowerDestroyed();
            barracksDestroyed = myTeamStat.getBarracksDestroyed();
        }

        boolean isMvp = determineMvp(player, isWin);
        boolean isFirstBlood = false;
        boolean hasTripleKill = player.getKillCount() >= 3;
        boolean hasPentaKill = player.getKillCount() >= 5;
        boolean hasFriendInTeam = false;

        QuestService.BattleQuestContext context = new QuestService.BattleQuestContext(
                isWin,
                gameMode,
                player.getHeroId(),
                player.getKillCount(),
                player.getDeathCount(),
                player.getAssistCount(),
                player.getDamageDealt(),
                player.getDamageTaken(),
                player.getHealing(),
                player.getGoldEarned(),
                towersDestroyed,
                barracksDestroyed,
                isMvp,
                isFirstBlood,
                hasTripleKill,
                hasPentaKill,
                durationMs / 1000,
                hasFriendInTeam
        );

        questService.onBattleEnd(userId, context);
        log.debug("玩家战斗结算任务处理完成: userId={}, isWin={}, kills={}, deaths={}, assists={}",
                userId, isWin, player.getKillCount(), player.getDeathCount(), player.getAssistCount());
    }

    private boolean determineMvp(PlayerStatDTO player, boolean isWinner) {
        if (!isWinner) {
            return false;
        }
        int score = player.getKillCount() * 3 + player.getAssistCount() + (player.getDamageDealt() / 1000);
        return score >= 20;
    }
}
