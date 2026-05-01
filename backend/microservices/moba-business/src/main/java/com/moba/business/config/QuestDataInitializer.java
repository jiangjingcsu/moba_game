package com.moba.business.config;

import com.moba.business.entity.quest.*;
import com.moba.business.repository.QuestTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class QuestDataInitializer implements CommandLineRunner {

    private final QuestTemplateRepository questTemplateRepository;

    @Override
    public void run(String... args) {
        long count = questTemplateRepository.count();
        if (count > 0) {
            log.info("Quest templates already initialized (count={}), skipping", count);
            return;
        }

        log.info("Initializing MOBA quest templates...");

        initDailyQuests();
        initWeeklyQuests();
        initNoviceQuests();
        initAchievementQuests();
        initSeasonQuests();

        log.info("Quest templates initialization completed. Total: {}", questTemplateRepository.count());
    }

    private void initDailyQuests() {
        List<QuestTemplate> templates = List.of(
                build("DAILY_001", "每日首胜", "每天赢得1场对战即可领取奖励", QuestType.DAILY, QuestCategory.BATTLE_WIN, 1, QuestRewardType.GOLD, 200, 1),
                build("DAILY_002", "每日三战", "每天完成3场对战", QuestType.DAILY, QuestCategory.BATTLE_PLAY, 3, QuestRewardType.GOLD, 100, 2),
                build("DAILY_003", "击杀达人", "在对战中累计击杀8名敌方英雄", QuestType.DAILY, QuestCategory.KILL_COUNT, 8, QuestRewardType.GOLD, 150, 3),
                build("DAILY_004", "助攻之王", "在对战中累计获得15次助攻", QuestType.DAILY, QuestCategory.ASSIST_COUNT, 15, QuestRewardType.GOLD, 100, 4),
                build("DAILY_005", "推塔先锋", "在对战中累计摧毁3座防御塔", QuestType.DAILY, QuestCategory.TOWER_DESTROY, 3, QuestRewardType.GOLD, 120, 5),
                build("DAILY_006", "MVP表现", "在对战中获得1次MVP", QuestType.DAILY, QuestCategory.MVP_EARN, 1, QuestRewardType.DIAMOND, 10, 6),
                build("DAILY_007", "多英雄体验", "使用2名不同英雄完成对战", QuestType.DAILY, QuestCategory.HERO_PLAY_DIFFERENT, 2, QuestRewardType.HERO_FRAGMENT, 2, 7),
                build("DAILY_008", "团队协作", "与好友组队完成1场对战", QuestType.DAILY, QuestCategory.FRIEND_PLAY, 1, QuestRewardType.GOLD, 80, 8),
                build("DAILY_009", "一血夺取", "在对战中获得1次一血", QuestType.DAILY, QuestCategory.FIRST_BLOOD, 1, QuestRewardType.GOLD, 60, 9),
                build("DAILY_010", "暴走时刻", "在对战中获得1次三杀", QuestType.DAILY, QuestCategory.TRIPLE_KILL, 1, QuestRewardType.DIAMOND, 5, 10)
        );

        questTemplateRepository.saveAll(templates);
        log.info("Initialized {} daily quest templates", templates.size());
    }

    private void initWeeklyQuests() {
        List<QuestTemplate> templates = List.of(
                build("WEEKLY_001", "每周七胜", "本周累计赢得7场对战", QuestType.WEEKLY, QuestCategory.BATTLE_WIN, 7, QuestRewardType.DIAMOND, 50, 1),
                build("WEEKLY_002", "每周十五战", "本周完成15场对战", QuestType.WEEKLY, QuestCategory.BATTLE_PLAY, 15, QuestRewardType.GOLD, 500, 2),
                build("WEEKLY_003", "击杀狂人", "本周累计击杀40名敌方英雄", QuestType.WEEKLY, QuestCategory.KILL_COUNT, 40, QuestRewardType.GOLD, 300, 3),
                build("WEEKLY_004", "辅助之星", "本周累计获得60次助攻", QuestType.WEEKLY, QuestCategory.ASSIST_COUNT, 60, QuestRewardType.GOLD, 200, 4),
                build("WEEKLY_005", "推塔机器", "本周累计摧毁12座防御塔", QuestType.WEEKLY, QuestCategory.TOWER_DESTROY, 12, QuestRewardType.GOLD, 250, 5),
                build("WEEKLY_006", "MVP常客", "本周获得3次MVP", QuestType.WEEKLY, QuestCategory.MVP_EARN, 3, QuestRewardType.DIAMOND, 30, 6),
                build("WEEKLY_007", "英雄池拓展", "本周使用5名不同英雄完成对战", QuestType.WEEKLY, QuestCategory.HERO_PLAY_DIFFERENT, 5, QuestRewardType.HERO_FRAGMENT, 5, 7),
                build("WEEKLY_008", "五杀传说", "本周获得1次五杀", QuestType.WEEKLY, QuestCategory.PENTA_KILL, 1, QuestRewardType.SKIN_FRAGMENT, 3, 8),
                build("WEEKLY_009", "模式探索者", "本周体验2种不同游戏模式", QuestType.WEEKLY, QuestCategory.GAME_MODE_PLAY, 2, QuestRewardType.GOLD, 150, 9),
                build("WEEKLY_010", "好友同行", "本周与好友组队完成5场对战", QuestType.WEEKLY, QuestCategory.FRIEND_PLAY, 5, QuestRewardType.CHEST, 1, 10),
                build("WEEKLY_011", "伤害输出者", "在单场对战中造成20000点伤害", QuestType.WEEKLY, QuestCategory.DAMAGE_DEAL, 20000, QuestRewardType.GOLD, 100, 11),
                build("WEEKLY_012", "守护天使", "在单场对战中治疗量达到8000点", QuestType.WEEKLY, QuestCategory.HEALING_DONE, 8000, QuestRewardType.GOLD, 100, 12)
        );

        questTemplateRepository.saveAll(templates);
        log.info("Initialized {} weekly quest templates", templates.size());
    }

    private void initNoviceQuests() {
        List<QuestTemplate> templates = List.of(
                buildNovice("NOVICE_001", "初入峡谷", "完成你的第一场对战", QuestCategory.BATTLE_PLAY, 1, QuestRewardType.GOLD, 500, 0, 1),
                buildNovice("NOVICE_002", "初尝胜利", "赢得你的第一场对战", QuestCategory.BATTLE_WIN, 1, QuestRewardType.GOLD, 800, 0, 2),
                buildNovice("NOVICE_003", "首次击杀", "在对战中击杀你的第一名敌方英雄", QuestCategory.KILL_COUNT, 1, QuestRewardType.GOLD, 300, 0, 3),
                buildNovice("NOVICE_004", "一血初体验", "在对战中获得一血", QuestCategory.FIRST_BLOOD, 1, QuestRewardType.DIAMOND, 10, 0, 4),
                buildNovice("NOVICE_005", "推塔入门", "在对战中摧毁你的第一座防御塔", QuestCategory.TOWER_DESTROY, 1, QuestRewardType.GOLD, 200, 2, 5),
                buildNovice("NOVICE_006", "英雄初体验", "使用3名不同英雄完成对战", QuestCategory.HERO_PLAY_DIFFERENT, 3, QuestRewardType.HERO_FRAGMENT, 5, 3, 6),
                buildNovice("NOVICE_007", "助攻练习", "在单场对战中获得5次助攻", QuestCategory.ASSIST_COUNT, 5, QuestRewardType.GOLD, 200, 3, 7),
                buildNovice("NOVICE_008", "三战告捷", "累计赢得3场对战", QuestCategory.BATTLE_WIN, 3, QuestRewardType.DIAMOND, 20, 5, 8),
                buildNovice("NOVICE_009", "模式体验", "体验3v3v3和5v5两种游戏模式", QuestCategory.GAME_MODE_PLAY, 2, QuestRewardType.GOLD, 300, 5, 9),
                buildNovice("NOVICE_010", "崭露头角", "达到5级", QuestCategory.LEVEL_REACH, 5, QuestRewardType.CHEST, 1, 3, 10),
                buildNovice("NOVICE_011", "金币积累", "在单场对战中获取3000金币", QuestCategory.GOLD_EARN, 3000, QuestRewardType.GOLD, 500, 5, 11),
                buildNovice("NOVICE_012", "十战磨砺", "完成10场对战", QuestCategory.BATTLE_PLAY, 10, QuestRewardType.SKIN_FRAGMENT, 2, 7, 12),
                buildNovice("NOVICE_013", "MVP新秀", "获得1次MVP", QuestCategory.MVP_EARN, 1, QuestRewardType.TITLE, 1, 5, 13),
                buildNovice("NOVICE_014", "兵营破坏者", "摧毁1座兵营", QuestCategory.BARRACKS_DESTROY, 1, QuestRewardType.GOLD, 300, 7, 14),
                buildNovice("NOVICE_015", "初出茅庐", "达到10级", QuestCategory.LEVEL_REACH, 10, QuestRewardType.AVATAR_FRAME, 1, 8, 15)
        );

        questTemplateRepository.saveAll(templates);
        log.info("Initialized {} novice quest templates", templates.size());
    }

    private void initAchievementQuests() {
        List<QuestTemplate> templates = List.of(
                build("ACHIEVE_001", "百战老兵", "累计完成100场对战", QuestType.ACHIEVEMENT, QuestCategory.BATTLE_PLAY, 100, QuestRewardType.DIAMOND, 100, 1),
                build("ACHIEVE_002", "常胜将军", "累计赢得50场对战", QuestType.ACHIEVEMENT, QuestCategory.BATTLE_WIN, 50, QuestRewardType.DIAMOND, 80, 2),
                build("ACHIEVE_003", "千战传说", "累计完成1000场对战", QuestType.ACHIEVEMENT, QuestCategory.BATTLE_PLAY, 1000, QuestRewardType.SKIN_FRAGMENT, 10, 3),
                build("ACHIEVE_004", "百胜霸主", "累计赢得100场对战", QuestType.ACHIEVEMENT, QuestCategory.BATTLE_WIN, 100, QuestRewardType.DIAMOND, 200, 4),
                build("ACHIEVE_005", "杀神降临", "累计击杀500名敌方英雄", QuestType.ACHIEVEMENT, QuestCategory.KILL_COUNT, 500, QuestRewardType.TITLE, 1, 5),
                build("ACHIEVE_006", "千杀修罗", "累计击杀1000名敌方英雄", QuestType.ACHIEVEMENT, QuestCategory.KILL_COUNT, 1000, QuestRewardType.AVATAR_FRAME, 1, 6),
                build("ACHIEVE_007", "推塔狂魔", "累计摧毁100座防御塔", QuestType.ACHIEVEMENT, QuestCategory.TOWER_DESTROY, 100, QuestRewardType.DIAMOND, 50, 7),
                build("ACHIEVE_008", "全能选手", "使用20名不同英雄完成对战", QuestType.ACHIEVEMENT, QuestCategory.HERO_PLAY_DIFFERENT, 20, QuestRewardType.HERO_FRAGMENT, 10, 8),
                build("ACHIEVE_009", "MVP收割机", "累计获得30次MVP", QuestType.ACHIEVEMENT, QuestCategory.MVP_EARN, 30, QuestRewardType.TITLE, 1, 9),
                build("ACHIEVE_010", "五杀至尊", "累计获得5次五杀", QuestType.ACHIEVEMENT, QuestCategory.PENTA_KILL, 5, QuestRewardType.SKIN_FRAGMENT, 5, 10),
                build("ACHIEVE_011", "三杀专家", "累计获得20次三杀", QuestType.ACHIEVEMENT, QuestCategory.TRIPLE_KILL, 20, QuestRewardType.DIAMOND, 100, 11),
                build("ACHIEVE_012", "一血猎手", "累计获得50次一血", QuestType.ACHIEVEMENT, QuestCategory.FIRST_BLOOD, 50, QuestRewardType.GOLD, 2000, 12),
                build("ACHIEVE_013", "不屈意志", "累计获得200次助攻", QuestType.ACHIEVEMENT, QuestCategory.ASSIST_COUNT, 200, QuestRewardType.DIAMOND, 50, 13),
                build("ACHIEVE_014", "兵营粉碎者", "累计摧毁50座兵营", QuestType.ACHIEVEMENT, QuestCategory.BARRACKS_DESTROY, 50, QuestRewardType.DIAMOND, 80, 14),
                build("ACHIEVE_015", "黄金猎手", "累计在对战中获取100000金币", QuestType.ACHIEVEMENT, QuestCategory.GOLD_EARN, 100000, QuestRewardType.TITLE, 1, 15),
                build("ACHIEVE_016", "伤害之王", "累计造成500000点伤害", QuestType.ACHIEVEMENT, QuestCategory.DAMAGE_DEAL, 500000, QuestRewardType.AVATAR_FRAME, 1, 16),
                build("ACHIEVE_017", "生命守护者", "累计治疗量达到200000点", QuestType.ACHIEVEMENT, QuestCategory.HEALING_DONE, 200000, QuestRewardType.TITLE, 1, 17),
                build("ACHIEVE_018", "社交达人", "与好友组队完成50场对战", QuestType.ACHIEVEMENT, QuestCategory.FRIEND_PLAY, 50, QuestRewardType.CHEST, 3, 18)
        );

        questTemplateRepository.saveAll(templates);
        log.info("Initialized {} achievement quest templates", templates.size());
    }

    private void initSeasonQuests() {
        List<QuestTemplate> templates = List.of(
                build("SEASON_001", "赛季征程", "本赛季完成30场对战", QuestType.SEASON, QuestCategory.BATTLE_PLAY, 30, QuestRewardType.DIAMOND, 100, 1),
                build("SEASON_002", "赛季精英", "本赛季赢得15场对战", QuestType.SEASON, QuestCategory.BATTLE_WIN, 15, QuestRewardType.DIAMOND, 80, 2),
                build("SEASON_003", "赛季击杀王", "本赛季累计击杀80名敌方英雄", QuestType.SEASON, QuestCategory.KILL_COUNT, 80, QuestRewardType.GOLD, 1000, 3),
                build("SEASON_004", "赛季助攻王", "本赛季累计获得120次助攻", QuestType.SEASON, QuestCategory.ASSIST_COUNT, 120, QuestRewardType.GOLD, 800, 4),
                build("SEASON_005", "赛季推塔王", "本赛季累计摧毁20座防御塔", QuestType.SEASON, QuestCategory.TOWER_DESTROY, 20, QuestRewardType.GOLD, 600, 5),
                build("SEASON_006", "赛季MVP", "本赛季获得5次MVP", QuestType.SEASON, QuestCategory.MVP_EARN, 5, QuestRewardType.DIAMOND, 50, 6),
                build("SEASON_007", "赛季英雄池", "本赛季使用8名不同英雄完成对战", QuestType.SEASON, QuestCategory.HERO_PLAY_DIFFERENT, 8, QuestRewardType.HERO_FRAGMENT, 8, 7),
                build("SEASON_008", "赛季三杀", "本赛季获得3次三杀", QuestType.SEASON, QuestCategory.TRIPLE_KILL, 3, QuestRewardType.SKIN_FRAGMENT, 3, 8),
                build("SEASON_009", "段位攀登者", "达到1200积分段位", QuestType.SEASON, QuestCategory.RANK_REACH, 1200, QuestRewardType.CHEST, 1, 9),
                build("SEASON_010", "段位征服者", "达到1500积分段位", QuestType.SEASON, QuestCategory.RANK_REACH, 1500, QuestRewardType.AVATAR_FRAME, 1, 10),
                build("SEASON_011", "段位王者", "达到2000积分段位", QuestType.SEASON, QuestCategory.RANK_REACH, 2000, QuestRewardType.TITLE, 1, 11),
                build("SEASON_012", "赛季五十战", "本赛季完成50场对战", QuestType.SEASON, QuestCategory.BATTLE_PLAY, 50, QuestRewardType.DIAMOND, 150, 12),
                build("SEASON_013", "赛季常胜", "本赛季赢得25场对战", QuestType.SEASON, QuestCategory.BATTLE_WIN, 25, QuestRewardType.SKIN_FRAGMENT, 5, 13),
                build("SEASON_014", "持久战士", "在单场对战中坚持超过1800秒", QuestType.SEASON, QuestCategory.BATTLE_DURATION, 1800, QuestRewardType.GOLD, 300, 14),
                build("SEASON_015", "赛季五杀", "本赛季获得1次五杀", QuestType.SEASON, QuestCategory.PENTA_KILL, 1, QuestRewardType.CHEST, 2, 15)
        );

        questTemplateRepository.saveAll(templates);
        log.info("Initialized {} season quest templates", templates.size());
    }

    private QuestTemplate build(String questCode, String questName, String description,
                                QuestType questType, QuestCategory category, int targetValue,
                                QuestRewardType rewardType, int rewardAmount, int sortOrder) {
        QuestTemplate template = new QuestTemplate();
        template.setQuestCode(questCode);
        template.setQuestName(questName);
        template.setDescription(description);
        template.setQuestType(questType);
        template.setCategory(category);
        template.setTargetValue(targetValue);
        template.setRewardType(rewardType);
        template.setRewardAmount(rewardAmount);
        template.setSortOrder(sortOrder);
        template.setEnabled(true);
        template.setRequiredLevel(0);
        template.setRequiredQuestOrder(-1);
        return template;
    }

    private QuestTemplate buildNovice(String questCode, String questName, String description,
                                      QuestCategory category, int targetValue,
                                      QuestRewardType rewardType, int rewardAmount,
                                      int requiredLevel, int sortOrder) {
        QuestTemplate template = new QuestTemplate();
        template.setQuestCode(questCode);
        template.setQuestName(questName);
        template.setDescription(description);
        template.setQuestType(QuestType.NOVICE);
        template.setCategory(category);
        template.setTargetValue(targetValue);
        template.setRewardType(rewardType);
        template.setRewardAmount(rewardAmount);
        template.setSortOrder(sortOrder);
        template.setEnabled(true);
        template.setRequiredLevel(requiredLevel);
        template.setRequiredQuestOrder(-1);
        return template;
    }
}
