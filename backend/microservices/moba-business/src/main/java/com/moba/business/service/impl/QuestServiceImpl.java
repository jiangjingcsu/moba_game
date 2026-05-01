package com.moba.business.service.impl;

import com.moba.business.entity.User;
import com.moba.business.entity.quest.*;
import com.moba.business.repository.PlayerQuestRepository;
import com.moba.business.repository.QuestTemplateRepository;
import com.moba.business.repository.UserRepository;
import com.moba.business.service.QuestService;
import com.moba.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestServiceImpl implements QuestService {

    private final QuestTemplateRepository questTemplateRepository;
    private final PlayerQuestRepository playerQuestRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUEST_CACHE_PREFIX = "quest:player:";
    private static final long CACHE_EXPIRE_SECONDS = 300;

    @Override
    public List<PlayerQuest> getPlayerQuests(Long playerId, QuestType questType) {
        if (questType != null) {
            return playerQuestRepository.findByPlayerIdAndQuestTypeOrderByCreateTimeAsc(playerId, questType);
        }
        return playerQuestRepository.findByPlayerIdAndStateOrderByCreateTimeAsc(playerId, null);
    }

    @Override
    public List<PlayerQuest> getPlayerActiveQuests(Long playerId) {
        return playerQuestRepository.findByPlayerIdAndStateOrderByCreateTimeAsc(playerId, QuestState.ACTIVE);
    }

    @Override
    public List<PlayerQuest> getPlayerActiveQuests(Long playerId, QuestType questType) {
        return playerQuestRepository.findByPlayerIdAndQuestTypeAndStateOrderByCreateTimeAsc(playerId, questType, QuestState.ACTIVE);
    }

    @Override
    @Transactional
    public PlayerQuest claimReward(Long playerId, Long playerQuestId) {
        PlayerQuest playerQuest = playerQuestRepository.findById(playerQuestId)
                .orElseThrow(() -> BusinessException.notFound("任务不存在"));

        if (!playerQuest.getPlayerId().equals(playerId)) {
            throw BusinessException.forbidden("无权领取该任务奖励");
        }

        if (playerQuest.getState() != QuestState.COMPLETED) {
            throw BusinessException.badRequest("任务未完成，无法领取奖励");
        }

        playerQuest.setState(QuestState.CLAIMED);
        playerQuest.setClaimedAt(LocalDateTime.now());
        PlayerQuest saved = playerQuestRepository.save(playerQuest);

        invalidateQuestCache(playerId);
        log.info("Player {} claimed reward for quest {} ({})", playerId, playerQuest.getQuestCode(), playerQuest.getRewardType());

        return saved;
    }

    @Override
    @Transactional
    public void refreshDailyQuests(Long playerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayEnd = LocalDateTime.of(now.toLocalDate(), LocalTime.MAX);

        playerQuestRepository.expireQuests(
                playerId,
                List.of(QuestType.DAILY),
                QuestState.ACTIVE,
                QuestState.EXPIRED,
                now
        );

        List<PlayerQuest> activeDaily = playerQuestRepository
                .findByPlayerIdAndQuestTypeAndStateIn(playerId, QuestType.DAILY, List.of(QuestState.ACTIVE, QuestState.COMPLETED));

        Set<String> existingCodes = activeDaily.stream()
                .map(PlayerQuest::getQuestCode)
                .collect(Collectors.toSet());

        List<QuestTemplate> dailyTemplates = questTemplateRepository.findByQuestTypeAndEnabledTrueOrderBySortOrderAsc(QuestType.DAILY);

        for (QuestTemplate template : dailyTemplates) {
            if (!existingCodes.contains(template.getQuestCode())) {
                createPlayerQuest(playerId, template, todayEnd);
            }
        }

        invalidateQuestCache(playerId);
        log.info("Refreshed daily quests for player {}", playerId);
    }

    @Override
    @Transactional
    public void refreshWeeklyQuests(Long playerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekEnd = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .with(LocalTime.MIN);

        playerQuestRepository.expireQuests(
                playerId,
                List.of(QuestType.WEEKLY),
                QuestState.ACTIVE,
                QuestState.EXPIRED,
                now
        );

        List<PlayerQuest> activeWeekly = playerQuestRepository
                .findByPlayerIdAndQuestTypeAndStateIn(playerId, QuestType.WEEKLY, List.of(QuestState.ACTIVE, QuestState.COMPLETED));

        Set<String> existingCodes = activeWeekly.stream()
                .map(PlayerQuest::getQuestCode)
                .collect(Collectors.toSet());

        List<QuestTemplate> weeklyTemplates = questTemplateRepository.findByQuestTypeAndEnabledTrueOrderBySortOrderAsc(QuestType.WEEKLY);

        for (QuestTemplate template : weeklyTemplates) {
            if (!existingCodes.contains(template.getQuestCode())) {
                createPlayerQuest(playerId, template, weekEnd);
            }
        }

        invalidateQuestCache(playerId);
        log.info("Refreshed weekly quests for player {}", playerId);
    }

    @Override
    @Transactional
    public void initNoviceQuests(Long playerId) {
        List<PlayerQuest> existing = playerQuestRepository
                .findByPlayerIdAndQuestTypeOrderByCreateTimeAsc(playerId, QuestType.NOVICE);

        if (!existing.isEmpty()) {
            return;
        }

        List<QuestTemplate> noviceTemplates = questTemplateRepository.findByQuestTypeAndEnabledTrueOrderBySortOrderAsc(QuestType.NOVICE);

        for (QuestTemplate template : noviceTemplates) {
            createPlayerQuest(playerId, template, null);
        }

        invalidateQuestCache(playerId);
        log.info("Initialized novice quests for player {}", playerId);
    }

    @Override
    @Transactional
    public void initSeasonQuests(Long playerId) {
        List<PlayerQuest> existing = playerQuestRepository
                .findByPlayerIdAndQuestTypeAndStateIn(playerId, QuestType.SEASON, List.of(QuestState.ACTIVE, QuestState.COMPLETED));

        Set<String> existingCodes = existing.stream()
                .map(PlayerQuest::getQuestCode)
                .collect(Collectors.toSet());

        List<QuestTemplate> seasonTemplates = questTemplateRepository.findByQuestTypeAndEnabledTrueOrderBySortOrderAsc(QuestType.SEASON);

        for (QuestTemplate template : seasonTemplates) {
            if (!existingCodes.contains(template.getQuestCode())) {
                LocalDateTime seasonEnd = LocalDateTime.now().plusMonths(3);
                createPlayerQuest(playerId, template, seasonEnd);
            }
        }

        invalidateQuestCache(playerId);
        log.info("Initialized season quests for player {}", playerId);
    }

    @Override
    @Transactional
    public void onBattleEnd(Long playerId, BattleQuestContext context) {
        checkAndExpireQuests(playerId);

        List<PlayerQuest> activeQuests = playerQuestRepository.findByPlayerIdAndStateOrderByCreateTimeAsc(playerId, QuestState.ACTIVE);

        boolean updated = false;
        for (PlayerQuest quest : activeQuests) {
            int progress = calculateProgress(quest, context);
            if (progress > 0) {
                quest.setCurrentValue(Math.min(quest.getCurrentValue() + progress, quest.getTargetValue()));
                if (quest.getCurrentValue() >= quest.getTargetValue()) {
                    quest.setState(QuestState.COMPLETED);
                    quest.setCompletedAt(LocalDateTime.now());
                    log.info("Player {} completed quest {} ({})", playerId, quest.getQuestCode(), quest.getCategory());
                }
                playerQuestRepository.save(quest);
                updated = true;
            }
        }

        if (updated) {
            invalidateQuestCache(playerId);
        }
    }

    @Override
    @Transactional
    public void onPlayerLevelUp(Long playerId, int newLevel) {
        List<PlayerQuest> activeQuests = playerQuestRepository
                .findByPlayerIdAndCategoryAndState(playerId, QuestCategory.LEVEL_REACH, QuestState.ACTIVE);

        for (PlayerQuest quest : activeQuests) {
            if (newLevel >= quest.getTargetValue()) {
                quest.setCurrentValue(quest.getTargetValue());
                quest.setState(QuestState.COMPLETED);
                quest.setCompletedAt(LocalDateTime.now());
                playerQuestRepository.save(quest);
                log.info("Player {} completed level quest {} at level {}", playerId, quest.getQuestCode(), newLevel);
            }
        }

        checkNoviceQuestUnlock(playerId, newLevel);
        invalidateQuestCache(playerId);
    }

    @Override
    @Transactional
    public void onRankScoreChange(Long playerId, int newRankScore) {
        List<PlayerQuest> activeQuests = playerQuestRepository
                .findByPlayerIdAndCategoryAndState(playerId, QuestCategory.RANK_REACH, QuestState.ACTIVE);

        for (PlayerQuest quest : activeQuests) {
            if (newRankScore >= quest.getTargetValue()) {
                quest.setCurrentValue(quest.getTargetValue());
                quest.setState(QuestState.COMPLETED);
                quest.setCompletedAt(LocalDateTime.now());
                playerQuestRepository.save(quest);
                log.info("Player {} completed rank quest {} at score {}", playerId, quest.getQuestCode(), newRankScore);
            }
        }

        invalidateQuestCache(playerId);
    }

    @Override
    @Transactional
    public void checkAndExpireQuests(Long playerId) {
        LocalDateTime now = LocalDateTime.now();
        playerQuestRepository.expireQuests(
                playerId,
                List.of(QuestType.DAILY, QuestType.WEEKLY, QuestType.SEASON),
                QuestState.ACTIVE,
                QuestState.EXPIRED,
                now
        );
    }

    @Override
    public Map<String, Object> getQuestProgressSummary(Long playerId) {
        String cacheKey = QUEST_CACHE_PREFIX + playerId + ":summary";
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<PlayerQuest> allQuests = playerQuestRepository
                .findByPlayerIdAndStateOrderByCreateTimeAsc(playerId, null);

        Map<String, Object> summary = new LinkedHashMap<>();

        long dailyActive = allQuests.stream().filter(q -> q.getQuestType() == QuestType.DAILY && q.getState() == QuestState.ACTIVE).count();
        long dailyCompleted = allQuests.stream().filter(q -> q.getQuestType() == QuestType.DAILY && q.getState() == QuestState.COMPLETED).count();
        long dailyClaimed = allQuests.stream().filter(q -> q.getQuestType() == QuestType.DAILY && q.getState() == QuestState.CLAIMED).count();

        long weeklyActive = allQuests.stream().filter(q -> q.getQuestType() == QuestType.WEEKLY && q.getState() == QuestState.ACTIVE).count();
        long weeklyCompleted = allQuests.stream().filter(q -> q.getQuestType() == QuestType.WEEKLY && q.getState() == QuestState.COMPLETED).count();
        long weeklyClaimed = allQuests.stream().filter(q -> q.getQuestType() == QuestType.WEEKLY && q.getState() == QuestState.CLAIMED).count();

        long noviceActive = allQuests.stream().filter(q -> q.getQuestType() == QuestType.NOVICE && q.getState() == QuestState.ACTIVE).count();
        long noviceCompleted = allQuests.stream().filter(q -> q.getQuestType() == QuestType.NOVICE && q.getState() == QuestState.COMPLETED).count();
        long noviceClaimed = allQuests.stream().filter(q -> q.getQuestType() == QuestType.NOVICE && q.getState() == QuestState.CLAIMED).count();

        long achievementCompleted = allQuests.stream().filter(q -> q.getQuestType() == QuestType.ACHIEVEMENT && q.getState() == QuestState.COMPLETED).count();
        long achievementClaimed = allQuests.stream().filter(q -> q.getQuestType() == QuestType.ACHIEVEMENT && q.getState() == QuestState.CLAIMED).count();

        long seasonActive = allQuests.stream().filter(q -> q.getQuestType() == QuestType.SEASON && q.getState() == QuestState.ACTIVE).count();
        long seasonCompleted = allQuests.stream().filter(q -> q.getQuestType() == QuestType.SEASON && q.getState() == QuestState.COMPLETED).count();

        summary.put("daily", Map.of("active", dailyActive, "completed", dailyCompleted, "claimed", dailyClaimed));
        summary.put("weekly", Map.of("active", weeklyActive, "completed", weeklyCompleted, "claimed", weeklyClaimed));
        summary.put("novice", Map.of("active", noviceActive, "completed", noviceCompleted, "claimed", noviceClaimed));
        summary.put("achievement", Map.of("completed", achievementCompleted, "claimed", achievementClaimed));
        summary.put("season", Map.of("active", seasonActive, "completed", seasonCompleted));

        redisTemplate.opsForValue().set(cacheKey, summary, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        return summary;
    }

    private PlayerQuest createPlayerQuest(Long playerId, QuestTemplate template, LocalDateTime expireAt) {
        PlayerQuest playerQuest = new PlayerQuest();
        playerQuest.setPlayerId(playerId);
        playerQuest.setQuestTemplateId(template.getId());
        playerQuest.setQuestCode(template.getQuestCode());
        playerQuest.setQuestType(template.getQuestType());
        playerQuest.setCategory(template.getCategory());
        playerQuest.setCurrentValue(0);
        playerQuest.setTargetValue(template.getTargetValue());
        playerQuest.setState(QuestState.ACTIVE);
        playerQuest.setRewardType(template.getRewardType());
        playerQuest.setRewardAmount(template.getRewardAmount());
        playerQuest.setExpireAt(expireAt);
        return playerQuestRepository.save(playerQuest);
    }

    private int calculateProgress(PlayerQuest quest, BattleQuestContext context) {
        return switch (quest.getCategory()) {
            case BATTLE_WIN -> context.isWin() ? 1 : 0;
            case BATTLE_PLAY -> 1;
            case KILL_COUNT -> context.killCount();
            case ASSIST_COUNT -> context.assistCount();
            case DEATH_LIMIT -> context.deathCount() <= quest.getTargetValue() ? 1 : 0;
            case TOWER_DESTROY -> context.towersDestroyed();
            case GOLD_EARN -> context.goldEarned() >= quest.getTargetValue() ? 1 : 0;
            case DAMAGE_DEAL -> context.damageDealt() >= quest.getTargetValue() ? 1 : 0;
            case HEALING_DONE -> context.healingDone() >= quest.getTargetValue() ? 1 : 0;
            case MVP_EARN -> context.isMvp() ? 1 : 0;
            case HERO_PLAY -> 1;
            case HERO_PLAY_DIFFERENT -> 1;
            case TRIPLE_KILL -> context.hasTripleKill() ? 1 : 0;
            case PENTA_KILL -> context.hasPentaKill() ? 1 : 0;
            case FIRST_BLOOD -> context.isFirstBlood() ? 1 : 0;
            case BARRACKS_DESTROY -> context.barracksDestroyed();
            case GAME_MODE_PLAY -> 1;
            case FRIEND_PLAY -> context.hasFriendInTeam() ? 1 : 0;
            case BATTLE_DURATION -> context.battleDurationSeconds() >= quest.getTargetValue() ? 1 : 0;
            default -> 0;
        };
    }

    private void checkNoviceQuestUnlock(Long playerId, int currentLevel) {
        List<QuestTemplate> noviceTemplates = questTemplateRepository
                .findByQuestTypeAndEnabledTrueOrderBySortOrderAsc(QuestType.NOVICE);

        for (QuestTemplate template : noviceTemplates) {
            if (template.getRequiredLevel() != null && template.getRequiredLevel() > 0
                    && currentLevel >= template.getRequiredLevel()) {
                boolean exists = playerQuestRepository.existsByPlayerIdAndQuestCode(playerId, template.getQuestCode());
                if (!exists) {
                    createPlayerQuest(playerId, template, null);
                    log.info("Unlocked novice quest {} for player {} at level {}", template.getQuestCode(), playerId, currentLevel);
                }
            }
        }
    }

    private void invalidateQuestCache(Long playerId) {
        Set<String> keys = redisTemplate.keys(QUEST_CACHE_PREFIX + playerId + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
