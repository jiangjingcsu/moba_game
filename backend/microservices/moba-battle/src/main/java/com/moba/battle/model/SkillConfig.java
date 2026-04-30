package com.moba.battle.model;

import com.moba.battle.battle.SkillCollisionSystem;
import com.moba.battle.battle.SkillCollisionSystem.SkillCastInfo;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SkillConfig {
    private int skillId;
    private String skillName;
    private SkillType type;
    private int cooldown;
    private int mpCost;
    private int castRange;
    private int damage;
    private int damageRadius;
    private int rectWidth;
    private int rectHeight;
    private int fanAngle;
    private boolean canHitSelf;
    private boolean canHitAlly;
    private boolean canHitEnemy;
    private int levelUpBonusDamage;

    public static Map<Integer, SkillConfig> loadAllSkills() {
        Map<Integer, SkillConfig> skills = new HashMap<>();

        SkillConfig s1 = new SkillConfig();
        s1.setSkillId(1);
        s1.setSkillName("冲锋斩");
        s1.setType(SkillType.LINE);
        s1.setCooldown(5000);
        s1.setMpCost(50);
        s1.setCastRange(500);
        s1.setDamage(200);
        s1.setRectWidth(100);
        s1.setRectHeight(400);
        s1.setCanHitSelf(false);
        s1.setCanHitAlly(false);
        s1.setCanHitEnemy(true);
        s1.setLevelUpBonusDamage(30);
        skills.put(1, s1);

        SkillConfig s2 = new SkillConfig();
        s2.setSkillId(2);
        s2.setSkillName("旋转打击");
        s2.setType(SkillType.CIRCLE_AREA);
        s2.setCooldown(8000);
        s2.setMpCost(100);
        s2.setCastRange(0);
        s2.setDamage(150);
        s2.setDamageRadius(300);
        s2.setCanHitSelf(true);
        s2.setCanHitAlly(false);
        s2.setCanHitEnemy(true);
        s2.setLevelUpBonusDamage(25);
        skills.put(2, s2);

        SkillConfig s3 = new SkillConfig();
        s3.setSkillId(3);
        s3.setSkillName("致命打击");
        s3.setType(SkillType.INSTANT);
        s3.setCooldown(3000);
        s3.setMpCost(30);
        s3.setCastRange(200);
        s3.setDamage(300);
        s3.setDamageRadius(50);
        s3.setCanHitSelf(false);
        s3.setCanHitAlly(false);
        s3.setCanHitEnemy(true);
        s3.setLevelUpBonusDamage(50);
        skills.put(3, s3);

        SkillConfig s4 = new SkillConfig();
        s4.setSkillId(4);
        s4.setSkillName("烈焰风暴");
        s4.setType(SkillType.CIRCLE_AREA);
        s4.setCooldown(12000);
        s4.setMpCost(200);
        s4.setCastRange(800);
        s4.setDamage(400);
        s4.setDamageRadius(400);
        s4.setCanHitSelf(false);
        s4.setCanHitAlly(false);
        s4.setCanHitEnemy(true);
        s4.setLevelUpBonusDamage(80);
        skills.put(4, s4);

        SkillConfig s5 = new SkillConfig();
        s5.setSkillId(5);
        s5.setSkillName("盾墙");
        s5.setType(SkillType.SELF_BUFF);
        s5.setCooldown(20000);
        s5.setMpCost(100);
        s5.setCastRange(0);
        s5.setDamage(0);
        s5.setCanHitSelf(true);
        s5.setCanHitAlly(false);
        s5.setCanHitEnemy(false);
        s5.setLevelUpBonusDamage(0);
        skills.put(5, s5);

        SkillConfig s6 = new SkillConfig();
        s6.setSkillId(6);
        s6.setSkillName("旋风斩");
        s6.setType(SkillType.FAN_AREA);
        s6.setCooldown(6000);
        s6.setMpCost(80);
        s6.setCastRange(0);
        s6.setDamage(180);
        s6.setDamageRadius(350);
        s6.setFanAngle(120);
        s6.setCanHitSelf(true);
        s6.setCanHitAlly(false);
        s6.setCanHitEnemy(true);
        s6.setLevelUpBonusDamage(35);
        skills.put(6, s6);

        return skills;
    }

    public enum SkillType {
        CIRCLE_AREA,
        RECT_AREA,
        FAN_AREA,
        LINE,
        INSTANT,
        TRAIL,
        SELF_BUFF
    }

    public SkillCastInfo toSkillCastInfo(BattlePlayer caster, long targetId, int targetX, int targetY, int facing, int frameNumber) {
        SkillCastInfo info = new SkillCastInfo();
        info.setCasterId(caster.getPlayerId());
        info.setTargetId(targetId);
        info.setCasterTeamId(caster.getTeamId());
        info.setCasterX(caster.getPosition().x);
        info.setCasterY(caster.getPosition().y);
        info.setTargetX(targetX);
        info.setTargetY(targetY);
        info.setSkillId(this.skillId);
        info.setSkillType(SkillCastInfo.SkillType.valueOf(this.type.name()));
        info.setRadius(this.damageRadius);
        info.setRectWidth(this.rectWidth);
        info.setRectHeight(this.rectHeight);
        info.setFanAngle(this.fanAngle);
        info.setFacing(facing);
        info.setCanHitSelf(this.canHitSelf);
        info.setCanHitAlly(this.canHitAlly);
        info.setCanHitEnemy(this.canHitEnemy);
        info.setFrameNumber(frameNumber);
        return info;
    }
}

