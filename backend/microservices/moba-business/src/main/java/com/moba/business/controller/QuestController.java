package com.moba.business.controller;

import com.moba.business.entity.quest.*;
import com.moba.business.service.QuestService;
import com.moba.common.protocol.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/quest")
@RequiredArgsConstructor
public class QuestController {

    private final QuestService questService;

    @GetMapping("/list")
    public ApiResponse getPlayerQuests(
            @RequestHeader("X-Player-Id") String playerIdStr,
            @RequestParam(required = false) String questType) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            QuestType type = questType != null ? QuestType.valueOf(questType.toUpperCase()) : null;
            List<PlayerQuest> quests = questService.getPlayerQuests(playerId, type);
            return ApiResponse.success(quests);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest("无效的任务类型: " + questType);
        } catch (Exception e) {
            log.error("Get player quests failed", e);
            return ApiResponse.error("获取任务列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/active")
    public ApiResponse getActiveQuests(
            @RequestHeader("X-Player-Id") String playerIdStr,
            @RequestParam(required = false) String questType) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            List<PlayerQuest> quests;
            if (questType != null) {
                QuestType type = QuestType.valueOf(questType.toUpperCase());
                quests = questService.getPlayerActiveQuests(playerId, type);
            } else {
                quests = questService.getPlayerActiveQuests(playerId);
            }
            return ApiResponse.success(quests);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest("无效的任务类型: " + questType);
        } catch (Exception e) {
            log.error("Get active quests failed", e);
            return ApiResponse.error("获取活跃任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/claim/{questId}")
    public ApiResponse claimReward(
            @RequestHeader("X-Player-Id") String playerIdStr,
            @PathVariable Long questId) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            PlayerQuest quest = questService.claimReward(playerId, questId);
            return ApiResponse.success(Map.of(
                    "questId", quest.getId(),
                    "questCode", quest.getQuestCode(),
                    "rewardType", quest.getRewardType().name(),
                    "rewardAmount", quest.getRewardAmount(),
                    "state", quest.getState().name()
            ));
        } catch (Exception e) {
            log.error("Claim reward failed for quest {}", questId, e);
            return ApiResponse.error("领取奖励失败: " + e.getMessage());
        }
    }

    @PostMapping("/refresh/daily")
    public ApiResponse refreshDailyQuests(@RequestHeader("X-Player-Id") String playerIdStr) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            questService.refreshDailyQuests(playerId);
            List<PlayerQuest> quests = questService.getPlayerActiveQuests(playerId, QuestType.DAILY);
            return ApiResponse.success(Map.of("message", "每日任务已刷新", "quests", quests));
        } catch (Exception e) {
            log.error("Refresh daily quests failed", e);
            return ApiResponse.error("刷新每日任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/refresh/weekly")
    public ApiResponse refreshWeeklyQuests(@RequestHeader("X-Player-Id") String playerIdStr) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            questService.refreshWeeklyQuests(playerId);
            List<PlayerQuest> quests = questService.getPlayerActiveQuests(playerId, QuestType.WEEKLY);
            return ApiResponse.success(Map.of("message", "每周任务已刷新", "quests", quests));
        } catch (Exception e) {
            log.error("Refresh weekly quests failed", e);
            return ApiResponse.error("刷新每周任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/init/novice")
    public ApiResponse initNoviceQuests(@RequestHeader("X-Player-Id") String playerIdStr) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            questService.initNoviceQuests(playerId);
            List<PlayerQuest> quests = questService.getPlayerActiveQuests(playerId, QuestType.NOVICE);
            return ApiResponse.success(Map.of("message", "新手任务已初始化", "quests", quests));
        } catch (Exception e) {
            log.error("Init novice quests failed", e);
            return ApiResponse.error("初始化新手任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/init/season")
    public ApiResponse initSeasonQuests(@RequestHeader("X-Player-Id") String playerIdStr) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            questService.initSeasonQuests(playerId);
            List<PlayerQuest> quests = questService.getPlayerActiveQuests(playerId, QuestType.SEASON);
            return ApiResponse.success(Map.of("message", "赛季任务已初始化", "quests", quests));
        } catch (Exception e) {
            log.error("Init season quests failed", e);
            return ApiResponse.error("初始化赛季任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/battle/report")
    public ApiResponse reportBattleResult(
            @RequestHeader("X-Player-Id") String playerIdStr,
            @RequestBody Map<String, Object> battleResult) {
        try {
            long playerId = Long.parseLong(playerIdStr);

            QuestService.BattleQuestContext context = new QuestService.BattleQuestContext(
                    Boolean.TRUE.equals(battleResult.get("isWin")),
                    battleResult.containsKey("gameMode") ? ((Number) battleResult.get("gameMode")).intValue() : 0,
                    battleResult.containsKey("heroId") ? ((Number) battleResult.get("heroId")).intValue() : 0,
                    battleResult.containsKey("killCount") ? ((Number) battleResult.get("killCount")).intValue() : 0,
                    battleResult.containsKey("deathCount") ? ((Number) battleResult.get("deathCount")).intValue() : 0,
                    battleResult.containsKey("assistCount") ? ((Number) battleResult.get("assistCount")).intValue() : 0,
                    battleResult.containsKey("damageDealt") ? ((Number) battleResult.get("damageDealt")).intValue() : 0,
                    battleResult.containsKey("damageTaken") ? ((Number) battleResult.get("damageTaken")).intValue() : 0,
                    battleResult.containsKey("healingDone") ? ((Number) battleResult.get("healingDone")).intValue() : 0,
                    battleResult.containsKey("goldEarned") ? ((Number) battleResult.get("goldEarned")).intValue() : 0,
                    battleResult.containsKey("towersDestroyed") ? ((Number) battleResult.get("towersDestroyed")).intValue() : 0,
                    battleResult.containsKey("barracksDestroyed") ? ((Number) battleResult.get("barracksDestroyed")).intValue() : 0,
                    Boolean.TRUE.equals(battleResult.get("isMvp")),
                    Boolean.TRUE.equals(battleResult.get("isFirstBlood")),
                    Boolean.TRUE.equals(battleResult.get("hasTripleKill")),
                    Boolean.TRUE.equals(battleResult.get("hasPentaKill")),
                    battleResult.containsKey("battleDurationSeconds") ? ((Number) battleResult.get("battleDurationSeconds")).longValue() : 0L,
                    Boolean.TRUE.equals(battleResult.get("hasFriendInTeam"))
            );

            questService.onBattleEnd(playerId, context);

            List<PlayerQuest> activeQuests = questService.getPlayerActiveQuests(playerId);
            List<PlayerQuest> completedQuests = questService.getPlayerQuests(playerId, null).stream()
                    .filter(q -> q.getState() == QuestState.COMPLETED)
                    .toList();

            return ApiResponse.success(Map.of(
                    "message", "战斗结果已处理",
                    "activeQuests", activeQuests,
                    "completedQuests", completedQuests
            ));
        } catch (Exception e) {
            log.error("Report battle result failed", e);
            return ApiResponse.error("上报战斗结果失败: " + e.getMessage());
        }
    }

    @GetMapping("/summary")
    public ApiResponse getQuestSummary(@RequestHeader("X-Player-Id") String playerIdStr) {
        try {
            long playerId = Long.parseLong(playerIdStr);
            Map<String, Object> summary = questService.getQuestProgressSummary(playerId);
            return ApiResponse.success(summary);
        } catch (Exception e) {
            log.error("Get quest summary failed", e);
            return ApiResponse.error("获取任务概览失败: " + e.getMessage());
        }
    }
}
