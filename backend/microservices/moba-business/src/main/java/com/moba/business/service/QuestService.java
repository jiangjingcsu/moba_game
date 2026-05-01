package com.moba.business.service;

import com.moba.business.entity.quest.*;

import java.util.List;
import java.util.Map;

public interface QuestService {

    List<PlayerQuest> getPlayerQuests(Long playerId, QuestType questType);

    List<PlayerQuest> getPlayerActiveQuests(Long playerId);

    List<PlayerQuest> getPlayerActiveQuests(Long playerId, QuestType questType);

    PlayerQuest claimReward(Long playerId, Long playerQuestId);

    void refreshDailyQuests(Long playerId);

    void refreshWeeklyQuests(Long playerId);

    void initNoviceQuests(Long playerId);

    void initSeasonQuests(Long playerId);

    void onBattleEnd(Long playerId, BattleQuestContext context);

    void onPlayerLevelUp(Long playerId, int newLevel);

    void onRankScoreChange(Long playerId, int newRankScore);

    void checkAndExpireQuests(Long playerId);

    Map<String, Object> getQuestProgressSummary(Long playerId);

    record BattleQuestContext(
            boolean isWin,
            int gameMode,
            int heroId,
            int killCount,
            int deathCount,
            int assistCount,
            int damageDealt,
            int damageTaken,
            int healingDone,
            int goldEarned,
            int towersDestroyed,
            int barracksDestroyed,
            boolean isMvp,
            boolean isFirstBlood,
            boolean hasTripleKill,
            boolean hasPentaKill,
            long battleDurationSeconds,
            boolean hasFriendInTeam
    ) {}
}
