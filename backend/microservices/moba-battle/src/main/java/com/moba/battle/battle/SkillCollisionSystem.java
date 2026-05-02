package com.moba.battle.battle;

import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Data
@Slf4j
public class SkillCollisionSystem {
    private final BattleSession session;
    private final GridCollisionDetector gridDetector;

    public SkillCollisionSystem(BattleSession session, GridCollisionDetector gridDetector) {
        this.session = session;
        this.gridDetector = gridDetector;
    }

    public GridCollisionDetector getGridDetector() {
        return gridDetector;
    }

    public List<Long> checkSkillHit(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();

        switch (skillInfo.getSkillType()) {
            case CIRCLE_AREA:
                hitPlayers = checkCircleArea(skillInfo);
                break;
            case RECT_AREA:
                hitPlayers = checkRectArea(skillInfo);
                break;
            case FAN_AREA:
                hitPlayers = checkFanArea(skillInfo);
                break;
            case LINE:
                hitPlayers = checkLineArea(skillInfo);
                break;
            case INSTANT:
                hitPlayers = checkInstant(skillInfo);
                break;
            case TRAIL:
                hitPlayers = checkTrail(skillInfo);
                break;
            case SELF_BUFF:
                hitPlayers = checkSelfBuff(skillInfo);
                break;
        }

        return hitPlayers;
    }

    private List<Long> checkCircleArea(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();
        int centerX = skillInfo.getTargetX();
        int centerY = skillInfo.getTargetY();
        int radius = skillInfo.getRadius();

        List<BattlePlayer> nearbyPlayers = gridDetector.getPlayersInRadius(
                skillInfo.getCasterId(), radius + 500);

        for (BattlePlayer player : nearbyPlayers) {
            if (player.getPlayerId() == skillInfo.getCasterId()) {
                if (!skillInfo.isCanHitSelf()) continue;
            }

            if (player.getTeamId() == skillInfo.getCasterTeamId() && !skillInfo.isCanHitAlly()) {
                continue;
            }
            if (player.getTeamId() != skillInfo.getCasterTeamId() && !skillInfo.isCanHitEnemy()) {
                continue;
            }

            if (player.isDead()) continue;

            int dx = player.getPosition().x - centerX;
            int dy = player.getPosition().y - centerY;
            int distance = (int) Math.sqrt(dx * dx + dy * dy);

            if (distance <= radius + player.getCollisionRadius()) {
                hitPlayers.add(player.getPlayerId());
                log.debug("圆形技能命中: skill={}, caster={}, target={}, distance={}, radius={}",
                        skillInfo.getSkillId(), skillInfo.getCasterId(), player.getPlayerId(), distance, radius);
            }
        }

        return hitPlayers;
    }

    private List<Long> checkRectArea(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();
        int rectX = skillInfo.getTargetX();
        int rectY = skillInfo.getTargetY();
        int rectWidth = skillInfo.getRectWidth();
        int rectHeight = skillInfo.getRectHeight();
        int facing = skillInfo.getFacing();

        int halfW = rectWidth / 2;
        int halfH = rectHeight / 2;

        int cos = cos(facing);
        int sin = sin(facing);

        List<BattlePlayer> nearbyPlayers = gridDetector.getPlayersInRect(
                skillInfo.getCasterId(), rectWidth, rectHeight);

        for (BattlePlayer player : nearbyPlayers) {
            if (player.getPlayerId() == skillInfo.getCasterId() && !skillInfo.isCanHitSelf()) {
                continue;
            }

            if (player.getTeamId() == skillInfo.getCasterTeamId() && !skillInfo.isCanHitAlly()) {
                continue;
            }
            if (player.getTeamId() != skillInfo.getCasterTeamId() && !skillInfo.isCanHitEnemy()) {
                continue;
            }

            if (player.isDead()) continue;

            int dx = player.getPosition().x - rectX;
            int dy = player.getPosition().y - rectY;

            int localX = (dx * cos + dy * sin) >> 16;
            int localY = (-dx * sin + dy * cos) >> 16;

            int collisionRadius = player.getCollisionRadius();

            if (Math.abs(localX) <= halfW + collisionRadius &&
                Math.abs(localY) <= halfH + collisionRadius) {
                hitPlayers.add(player.getPlayerId());
            }
        }

        return hitPlayers;
    }

    private List<Long> checkFanArea(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();
        int centerX = skillInfo.getCasterX();
        int centerY = skillInfo.getCasterY();
        int radius = skillInfo.getRadius();
        int facing = skillInfo.getFacing();
        int angle = skillInfo.getFanAngle();

        List<BattlePlayer> nearbyPlayers = gridDetector.getPlayersInRadius(
                skillInfo.getCasterId(), radius + 500);

        int halfAngle = angle / 2;

        for (BattlePlayer player : nearbyPlayers) {
            if (player.getPlayerId() == skillInfo.getCasterId() && !skillInfo.isCanHitSelf()) {
                continue;
            }

            if (player.getTeamId() == skillInfo.getCasterTeamId() && !skillInfo.isCanHitAlly()) {
                continue;
            }
            if (player.getTeamId() != skillInfo.getCasterTeamId() && !skillInfo.isCanHitEnemy()) {
                continue;
            }

            if (player.isDead()) continue;

            int dx = player.getPosition().x - centerX;
            int dy = player.getPosition().y - centerY;
            int distance = (int) Math.sqrt(dx * dx + dy * dy);

            if (distance > radius + player.getCollisionRadius()) {
                continue;
            }

            int targetAngle = (int) (Math.atan2(dy, dx) * 180 / Math.PI);
            int angleDiff = normalizeAngle(targetAngle - facing);

            if (Math.abs(angleDiff) <= halfAngle) {
                hitPlayers.add(player.getPlayerId());
                log.debug("扇形技能命中: player={}, angleDiff={}, halfAngle={}",
                        player.getPlayerId(), angleDiff, halfAngle);
            }
        }

        return hitPlayers;
    }

    private List<Long> checkLineArea(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();
        int startX = skillInfo.getCasterX();
        int startY = skillInfo.getCasterY();
        int endX = skillInfo.getTargetX();
        int endY = skillInfo.getTargetY();
        int width = skillInfo.getRectWidth();

        int dx = endX - startX;
        int dy = endY - startY;
        int lineLength = (int) Math.sqrt(dx * dx + dy * dy);

        if (lineLength == 0) return hitPlayers;

        int cos = (dx << 16) / lineLength;
        int sin = (dy << 16) / lineLength;

        int halfWidth = width / 2;
        int extendedEndX = startX + dx + cos * 500;
        int extendedEndY = startY + dy + sin * 500;

        List<BattlePlayer> nearbyPlayers = gridDetector.getPlayersInRect(
                skillInfo.getCasterId(), width, lineLength + 1000);

        for (BattlePlayer player : nearbyPlayers) {
            if (player.getPlayerId() == skillInfo.getCasterId() && !skillInfo.isCanHitSelf()) {
                continue;
            }

            if (player.getTeamId() == skillInfo.getCasterTeamId() && !skillInfo.isCanHitAlly()) {
                continue;
            }
            if (player.getTeamId() != skillInfo.getCasterTeamId() && !skillInfo.isCanHitEnemy()) {
                continue;
            }

            if (player.isDead()) continue;

            int px = player.getPosition().x - startX;
            int py = player.getPosition().y - startY;

            int projLen = (px * dx + py * dy) / lineLength;

            int closestX = startX + (dx * projLen) / lineLength;
            int closestY = startY + (dy * projLen) / lineLength;

            int distToLine = (int) Math.sqrt(
                    (player.getPosition().x - closestX) * (player.getPosition().x - closestX) +
                    (player.getPosition().y - closestY) * (player.getPosition().y - closestY)
            );

            if (distToLine <= halfWidth + player.getCollisionRadius() &&
                projLen >= -500 && projLen <= lineLength + 500) {
                hitPlayers.add(player.getPlayerId());
            }
        }

        return hitPlayers;
    }

    private List<Long> checkInstant(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();

        BattlePlayer target = session.getPlayer(skillInfo.getTargetId());
        if (target == null || target.isDead()) return hitPlayers;

        if (skillInfo.getCasterId() == target.getPlayerId() && !skillInfo.isCanHitSelf()) {
            return hitPlayers;
        }

        if (target.getTeamId() == skillInfo.getCasterTeamId() && !skillInfo.isCanHitAlly()) {
            return hitPlayers;
        }
        if (target.getTeamId() != skillInfo.getCasterTeamId() && !skillInfo.isCanHitEnemy()) {
            return hitPlayers;
        }

        int dx = target.getPosition().x - skillInfo.getCasterX();
        int dy = target.getPosition().y - skillInfo.getCasterY();
        int distance = (int) Math.sqrt(dx * dx + dy * dy);

        if (distance <= skillInfo.getRadius() + target.getCollisionRadius()) {
            hitPlayers.add(target.getPlayerId());
        }

        return hitPlayers;
    }

    private List<Long> checkTrail(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();
        int startX = skillInfo.getCasterX();
        int startY = skillInfo.getCasterY();
        int endX = skillInfo.getTargetX();
        int endY = skillInfo.getTargetY();
        int trailWidth = skillInfo.getRectWidth();

        int dx = endX - startX;
        int dy = endY - startY;
        int totalLength = (int) Math.sqrt(dx * dx + dy * dy);

        int segments = Math.max(1, totalLength / 100);

        for (int i = 0; i <= segments; i++) {
            int t = i * 100;
            int segX = startX + (dx * t) / totalLength;
            int segY = startY + (dy * t) / totalLength;

            List<BattlePlayer> nearby = gridDetector.getPlayersInRadius(segX, segY, trailWidth);
            for (BattlePlayer player : nearby) {
                if (player.isDead()) continue;
                if (!hitPlayers.contains(player.getPlayerId())) {
                    if (player.getTeamId() == skillInfo.getCasterTeamId() && !skillInfo.isCanHitAlly()) {
                        continue;
                    }
                    if (player.getTeamId() != skillInfo.getCasterTeamId() && !skillInfo.isCanHitEnemy()) {
                        continue;
                    }

                    int pdx = player.getPosition().x - segX;
                    int pdy = player.getPosition().y - segY;
                    int dist = (int) Math.sqrt(pdx * pdx + pdy * pdy);

                    if (dist <= trailWidth / 2 + player.getCollisionRadius()) {
                        hitPlayers.add(player.getPlayerId());
                    }
                }
            }
        }

        return hitPlayers;
    }

    private List<Long> checkSelfBuff(SkillCastInfo skillInfo) {
        List<Long> hitPlayers = new ArrayList<>();
        hitPlayers.add(skillInfo.getCasterId());
        return hitPlayers;
    }

    private int normalizeAngle(int angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    private int cos(int degrees) {
        return (int) (Math.cos(Math.toRadians(degrees)) * 65536);
    }

    private int sin(int degrees) {
        return (int) (Math.sin(Math.toRadians(degrees)) * 65536);
    }

    @Data
    public static class SkillCastInfo {
        private long casterId;
        private long targetId;
        private int casterTeamId;
        private int casterX;
        private int casterY;
        private int targetX;
        private int targetY;
        private int skillId;
        private SkillType skillType;
        private int radius;
        private int rectWidth;
        private int rectHeight;
        private int fanAngle;
        private int facing;
        private boolean canHitSelf;
        private boolean canHitAlly;
        private boolean canHitEnemy;
        private int frameNumber;

        public enum SkillType {
            CIRCLE_AREA,
            RECT_AREA,
            FAN_AREA,
            LINE,
            INSTANT,
            TRAIL,
            SELF_BUFF
        }
    }
}
