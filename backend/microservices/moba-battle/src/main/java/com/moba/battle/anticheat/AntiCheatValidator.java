package com.moba.battle.anticheat;

import com.moba.battle.model.BattlePlayer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AntiCheatValidator {
    private final Map<Long, PlayerMoveRecord> moveRecords;
    private final Map<Long, SkillCastRecord> skillRecords;

    public AntiCheatValidator() {
        this.moveRecords = new ConcurrentHashMap<>();
        this.skillRecords = new ConcurrentHashMap<>();
    }

    private static final int MOVE_VIOLATION_THRESHOLD = 10;
    private static final int SKILL_VIOLATION_THRESHOLD = 5;

    public boolean validateMove(long userId, BattlePlayer player, float targetX, float targetY, long deltaTimeMs) {
        PlayerMoveRecord record = moveRecords.computeIfAbsent(userId, k -> new PlayerMoveRecord());

        int dx = (int)targetX - player.getPosition().x;
        int dy = (int)targetY - player.getPosition().y;
        int distance = (int) Math.sqrt(dx * dx + dy * dy);

        int maxDistance = (int)((player.getMoveSpeed() / 1000.0f) * deltaTimeMs * 1.2f);

        boolean isValid = distance <= maxDistance;

        record.lastMoveTime = System.currentTimeMillis();
        record.lastValidPositionX = (int)targetX;
        record.lastValidPositionY = (int)targetY;

        if (!isValid) {
            log.warn("移动速度验证失败: player={}, distance={}, max={}, pos=({}, {})",
                    userId, distance, maxDistance, targetX, targetY);
            record.violationCount++;
            if (record.violationCount >= MOVE_VIOLATION_THRESHOLD) {
                log.error("玩家{}移动违规次数达到阈值{}, 标记为可疑并拒绝操作", userId, MOVE_VIOLATION_THRESHOLD);
                return false;
            }
        }

        return isValid;
    }

    public boolean validateSkillCast(long userId, BattlePlayer player, int skillId) {
        long currentTime = System.currentTimeMillis();

        BattlePlayer.Skill skill = player.getSkills().get(skillId);
        if (skill == null) return false;

        SkillCastRecord record = skillRecords.computeIfAbsent(userId, k -> new SkillCastRecord());

        long timeSinceLastCast = currentTime - skill.getLastCastTime();
        if (timeSinceLastCast < skill.getCooldown()) {
            log.warn("技能冷却违规: player={}, skill={}, elapsed={}, cd={}",
                    userId, skillId, timeSinceLastCast, skill.getCooldown());
            record.violationCount++;
            return false;
        }

        if (player.getCurrentMp() < skill.getMpCost()) {
            log.warn("技能魔法值不足: player={}, skill={}, mp={}, cost={}",
                    userId, skillId, player.getCurrentMp(), skill.getMpCost());
            record.violationCount++;
            return false;
        }

        skillRecords.put(userId, record);
        return true;
    }

    public boolean validateDamage(long attackerId, long targetId, int damage, BattlePlayer attacker, BattlePlayer target) {
        int expectedMinDamage = Math.max(1, attacker.getAttackPower() - target.getDefense() / 10 - 10);
        int expectedMaxDamage = Math.max(1, attacker.getAttackPower() - target.getDefense() / 10 + 10);

        if (damage < expectedMinDamage || damage > expectedMaxDamage) {
            log.warn("伤害验证失败: attacker={}, target={}, damage={}, expected=[{}, {}]",
                    attackerId, targetId, damage, expectedMinDamage, expectedMaxDamage);
            return false;
        }
        return true;
    }

    public boolean checkPlayerSuspicion(long userId) {
        PlayerMoveRecord moveRecord = moveRecords.get(userId);
        if (moveRecord != null && moveRecord.violationCount >= MOVE_VIOLATION_THRESHOLD) {
            log.error("玩家{}被标记为可疑行为: {}次移动违规",
                    userId, moveRecord.violationCount);
            return true;
        }

        SkillCastRecord skillRecord = skillRecords.get(userId);
        if (skillRecord != null && skillRecord.violationCount >= SKILL_VIOLATION_THRESHOLD) {
            log.error("玩家{}被标记为可疑技能使用: {}次冷却违规",
                    userId, skillRecord.violationCount);
            return true;
        }

        return false;
    }

    public void clearPlayerRecord(long userId) {
        moveRecords.remove(userId);
        skillRecords.remove(userId);
    }

    private static class PlayerMoveRecord {
        long lastMoveTime;
        int lastValidPositionX;
        int lastValidPositionY;
        int violationCount;
    }

    private static class SkillCastRecord {
        int violationCount;
    }
}
