package com.moba.battle.anticheat;

import com.moba.battle.config.SpringContextHolder;
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

    public static AntiCheatValidator getInstance() {
        return SpringContextHolder.getBean(AntiCheatValidator.class);
    }

    public boolean validateMove(long playerId, BattlePlayer player, float targetX, float targetY, long deltaTimeMs) {
        PlayerMoveRecord record = moveRecords.computeIfAbsent(playerId, k -> new PlayerMoveRecord());

        int dx = (int)targetX - player.getPosition().x;
        int dy = (int)targetY - player.getPosition().y;
        int distance = (int) Math.sqrt(dx * dx + dy * dy);

        int maxDistance = (int)((player.getMoveSpeed() / 1000.0f) * deltaTimeMs * 1.2f);

        boolean isValid = distance <= maxDistance;

        record.lastMoveTime = System.currentTimeMillis();
        record.lastValidPositionX = (int)targetX;
        record.lastValidPositionY = (int)targetY;

        if (!isValid) {
            log.warn("Move speed validation failed: player={}, distance={}, max={}, pos=({}, {})",
                    playerId, distance, maxDistance, targetX, targetY);
            record.violationCount++;
        }

        return isValid;
    }

    public boolean validateSkillCast(long playerId, BattlePlayer player, int skillId) {
        long currentTime = System.currentTimeMillis();

        BattlePlayer.Skill skill = player.getSkills().get(skillId);
        if (skill == null) return false;

        SkillCastRecord record = skillRecords.computeIfAbsent(playerId, k -> new SkillCastRecord());

        long timeSinceLastCast = currentTime - skill.getLastCastTime();
        if (timeSinceLastCast < skill.getCooldown()) {
            log.warn("Skill CD violation: player={}, skill={}, elapsed={}, cd={}",
                    playerId, skillId, timeSinceLastCast, skill.getCooldown());
            record.violationCount++;
            return false;
        }

        if (player.getCurrentMp() < skill.getMpCost()) {
            log.warn("Insufficient MP for skill: player={}, skill={}, mp={}, cost={}",
                    playerId, skillId, player.getCurrentMp(), skill.getMpCost());
            record.violationCount++;
            return false;
        }

        skillRecords.put(playerId, record);
        return true;
    }

    public boolean validateDamage(long attackerId, long targetId, int damage, BattlePlayer attacker, BattlePlayer target) {
        int expectedMinDamage = Math.max(1, attacker.getAttackPower() - target.getDefense() / 10 - 10);
        int expectedMaxDamage = Math.max(1, attacker.getAttackPower() - target.getDefense() / 10 + 10);

        if (damage < expectedMinDamage || damage > expectedMaxDamage) {
            log.warn("Damage validation failed: attacker={}, target={}, damage={}, expected=[{}, {}]",
                    attackerId, targetId, damage, expectedMinDamage, expectedMaxDamage);
            return false;
        }
        return true;
    }

    public boolean checkPlayerSuspicion(long playerId) {
        PlayerMoveRecord moveRecord = moveRecords.get(playerId);
        if (moveRecord != null && moveRecord.violationCount > 10) {
            log.error("Player {} flagged for suspicious behavior: {} move violations",
                    playerId, moveRecord.violationCount);
            return true;
        }

        SkillCastRecord skillRecord = skillRecords.get(playerId);
        if (skillRecord != null && skillRecord.violationCount > 5) {
            log.error("Player {} flagged for suspicious skill usage: {} CD violations",
                    playerId, skillRecord.violationCount);
            return true;
        }

        return false;
    }

    public void clearPlayerRecord(long playerId) {
        moveRecords.remove(playerId);
        skillRecords.remove(playerId);
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
