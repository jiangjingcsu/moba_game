package com.moba.business.service;

import com.moba.business.entity.quest.*;
import com.moba.common.constant.GameMode;

import java.util.List;
import java.util.Map;

public interface QuestService {

    List<PlayerQuest> getPlayerQuests(long userId, QuestType questType);

    List<PlayerQuest> getPlayerActiveQuests(long userId);

    List<PlayerQuest> getPlayerActiveQuests(long userId, QuestType questType);

    PlayerQuest claimReward(long userId, Long playerQuestId);

    void refreshDailyQuests(long userId);

    void refreshWeeklyQuests(long userId);

    void initNoviceQuests(long userId);

    void initSeasonQuests(long userId);

    void onBattleEnd(long userId, BattleQuestContext context);

    void onPlayerLevelUp(long userId, int newLevel);

    void onRankScoreChange(long userId, int newRankScore);

    void checkAndExpireQuests(long userId);

    Map<String, Object> getQuestProgressSummary(long userId);

    record BattleQuestContext(
            boolean isWin,
            GameMode gameMode,
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
