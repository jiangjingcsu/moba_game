package com.moba.battleserver.service;

import com.moba.battleserver.anticheat.AntiCheatValidator;
import com.moba.battleserver.battle.LockstepEngine;
import com.moba.battleserver.manager.BattleRoom;
import com.moba.battleserver.manager.PlayerManager;
import com.moba.battleserver.manager.RoomManager;
import com.moba.battleserver.model.BattlePlayer;
import com.moba.battleserver.model.FrameInput;
import com.moba.battleserver.model.Player;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
public class BattleInputHandler {
    private final AntiCheatValidator antiCheatValidator;

    public BattleInputHandler(AntiCheatValidator antiCheatValidator) {
        this.antiCheatValidator = antiCheatValidator;
    }

    public void handlePlayerAction(ChannelHandlerContext ctx, RoomManager roomManager,
                                   PlayerManager playerManager, byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length < 2) return;

        long playerId = resolvePlayerId(parts[0], ctx, playerManager);
        if (playerId <= 0) return;

        String actionType = parts[1].toUpperCase();

        BattleRoom room = roomManager.getPlayerRoom(playerId);
        if (room == null || !room.isRunning()) return;

        BattlePlayer battlePlayer = room.getSession().getPlayer(playerId);
        if (battlePlayer == null) return;

        LockstepEngine engine = room.getEngine();
        FrameInput frameInput = new FrameInput();
        frameInput.setPlayerId(playerId);
        frameInput.setData(data);

        switch (actionType) {
            case "MOVE":
                if (parts.length >= 4) {
                    float targetX = Float.parseFloat(parts[2]);
                    float targetY = Float.parseFloat(parts[3]);
                    if (!antiCheatValidator.validateMove(playerId, battlePlayer, targetX, targetY, 100)) {
                        log.warn("Move validation failed for player {}", playerId);
                        return;
                    }
                    frameInput.setType(FrameInput.InputType.MOVE);
                    engine.submitInput(frameInput);
                }
                break;
            case "ATTACK":
                if (parts.length >= 3) {
                    frameInput.setType(FrameInput.InputType.ATTACK);
                    engine.submitInput(frameInput);
                }
                break;
            case "USE_ITEM":
                frameInput.setType(FrameInput.InputType.USE_ITEM);
                engine.submitInput(frameInput);
                break;
        }
    }

    public void handleSkillCast(ChannelHandlerContext ctx, RoomManager roomManager,
                                PlayerManager playerManager, AntiCheatValidator validator,
                                byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");
        if (parts.length < 3) return;

        long playerId;
        try {
            playerId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            Optional<Player> playerOpt = playerManager.getPlayerByChannel(ctx);
            if (playerOpt.isEmpty()) return;
            playerId = playerOpt.get().getPlayerId();
        }

        int skillId = Integer.parseInt(parts[1]);
        long targetId = Long.parseLong(parts[2]);

        BattleRoom room = roomManager.getPlayerRoom(playerId);
        if (room == null || !room.isRunning()) return;

        BattlePlayer battlePlayer = room.getSession().getPlayer(playerId);
        if (battlePlayer == null || battlePlayer.isDead()) return;

        if (!validator.validateSkillCast(playerId, battlePlayer, skillId)) {
            log.warn("Skill cast validation failed for player {}", playerId);
            return;
        }

        FrameInput frameInput = new FrameInput();
        frameInput.setPlayerId(playerId);
        frameInput.setType(FrameInput.InputType.SKILL_CAST);
        frameInput.setData(data);
        room.getEngine().submitInput(frameInput);
    }

    private long resolvePlayerId(String idStr, ChannelHandlerContext ctx, PlayerManager playerManager) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            Optional<Player> playerOpt = playerManager.getPlayerByChannel(ctx);
            return playerOpt.map(Player::getPlayerId).orElse(-1L);
        }
    }
}
