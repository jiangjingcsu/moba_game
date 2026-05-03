package com.moba.battle.battle;

import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.MapTemplate;
import com.moba.battle.model.TerrainRegion;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MapTerrainSystem {

    private final MapTemplate template;
    private final Set<Long> playersInBush;
    private final Set<Long> playersInRiver;
    private final Set<Long> playersInBase;
    private final Map<Long, Float> originalSpeeds;

    public MapTerrainSystem(MapTemplate template) {
        this.template = template;
        this.playersInBush = new HashSet<>();
        this.playersInRiver = new HashSet<>();
        this.playersInBase = new HashSet<>();
        this.originalSpeeds = new HashMap<>();
    }

    public void processTerrainEffects(BattleSession session) {
        if (template == null || template.getTerrain() == null) return;

        Set<Long> currentInBush = new HashSet<>();
        Set<Long> currentInRiver = new HashSet<>();
        Set<Long> currentInBase = new HashSet<>();

        for (BattlePlayer player : session.getBattlePlayers().values()) {
            if (player.isDead()) {
                player.setStealthed(false);
                continue;
            }

            float px = player.getPosition().x;
            float py = player.getPosition().y;

            boolean inBush = isInTerrain(px, py, template.getTerrain().getBushes());
            boolean inRiver = isInTerrain(px, py, template.getTerrain().getRiver());
            boolean inBase = isInTerrain(px, py, template.getTerrain().getBases());

            if (inBush) {
                currentInBush.add(player.getUserId());
                player.setStealthed(true);
            } else {
                player.setStealthed(false);
            }

            if (inRiver) {
                currentInRiver.add(player.getUserId());
                applyRiverSlow(player);
            } else {
                removeRiverSlow(player);
            }

            if (inBase) {
                TerrainRegion baseRegion = findBaseForPosition(px, py);
                if (baseRegion != null && baseRegion.getTeamId() == player.getTeamId()) {
                    currentInBase.add(player.getUserId());
                    applyBaseHeal(player, baseRegion.getHealRate());
                }
            }
        }

        playersInBush.clear();
        playersInBush.addAll(currentInBush);
        playersInRiver.clear();
        playersInRiver.addAll(currentInRiver);
        playersInBase.clear();
        playersInBase.addAll(currentInBase);
    }

    private boolean isInTerrain(float px, float py, List<TerrainRegion> regions) {
        if (regions == null) return false;
        for (TerrainRegion region : regions) {
            if (region.contains(px, py)) return true;
        }
        return false;
    }

    private TerrainRegion findBaseForPosition(float px, float py) {
        if (template.getTerrain().getBases() == null) return null;
        for (TerrainRegion base : template.getTerrain().getBases()) {
            if (base.contains(px, py)) return base;
        }
        return null;
    }

    private void applyRiverSlow(BattlePlayer player) {
        long userId = player.getUserId();
        if (!originalSpeeds.containsKey(userId)) {
            originalSpeeds.put(userId, (float) player.getMoveSpeed());
        }

        if (template.getTerrain().getRiver() != null) {
            for (TerrainRegion river : template.getTerrain().getRiver()) {
                if (river.contains(player.getPosition().x, player.getPosition().y)) {
                    float factor = river.getSpeedFactor();
                    if (factor <= 0) factor = 0.7f;
                    float originalSpeed = originalSpeeds.get(userId);
                    int slowedSpeed = (int) (originalSpeed * factor);
                    if (player.getMoveSpeed() != slowedSpeed) {
                        player.setMoveSpeed(slowedSpeed);
                    }
                    break;
                }
            }
        }
    }

    private void removeRiverSlow(BattlePlayer player) {
        long userId = player.getUserId();
        Float originalSpeed = originalSpeeds.remove(userId);
        if (originalSpeed != null) {
            player.setMoveSpeed(originalSpeed.intValue());
        }
    }

    private void applyBaseHeal(BattlePlayer player, int healRate) {
        if (healRate <= 0) healRate = 50;
        if (player.getCurrentHp() < player.getMaxHp()) {
            int newHp = Math.min(player.getMaxHp(), player.getCurrentHp() + healRate / 15);
            player.setCurrentHp(newHp);
        }
        if (player.getCurrentMp() < player.getMaxMp()) {
            int newMp = Math.min(player.getMaxMp(), player.getCurrentMp() + healRate / 10);
            player.setCurrentMp(newMp);
        }
    }

    public boolean isPlayerInBush(long userId) {
        return playersInBush.contains(userId);
    }

    public boolean isPlayerInRiver(long userId) {
        return playersInRiver.contains(userId);
    }

    public boolean isPlayerInBase(long userId) {
        return playersInBase.contains(userId);
    }

    public boolean isInEnemyBase(BattlePlayer player) {
        if (template.getTerrain() == null || template.getTerrain().getBases() == null) return false;
        for (TerrainRegion base : template.getTerrain().getBases()) {
            if (base.contains(player.getPosition().x, player.getPosition().y)
                    && base.getTeamId() != player.getTeamId()) {
                return true;
            }
        }
        return false;
    }

    public MapTemplate getTemplate() {
        return template;
    }
}
