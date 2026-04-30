package com.moba.battleserver.service;

import com.moba.battleserver.ai.AIController;
import com.moba.battleserver.battle.GridCollisionDetector;
import com.moba.battleserver.battle.LockstepEngine;
import com.moba.battleserver.battle.SkillCollisionSystem;
import com.moba.battleserver.manager.BattleRoom;
import com.moba.battleserver.manager.MapManager;
import com.moba.battleserver.manager.PlayerManager;
import com.moba.battleserver.manager.RoomManager;
import com.moba.battleserver.model.*;
import com.moba.battleserver.model.MOBAMap.GameMode;
import com.moba.battleserver.replay.ReplaySystem;
import com.moba.battleserver.storage.BattleLogStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BattleCreator {
    private final PlayerManager playerManager;
    private final MapManager mapManager;
    private final AIController aiController;
    private final ReplaySystem replaySystem;
    private final GridCollisionDetector collisionDetector;
    private final ScheduledExecutorService scheduler;

    public BattleCreator(PlayerManager playerManager, MapManager mapManager,
                         AIController aiController, ReplaySystem replaySystem,
                         GridCollisionDetector collisionDetector) {
        this.playerManager = playerManager;
        this.mapManager = mapManager;
        this.aiController = aiController;
        this.replaySystem = replaySystem;
        this.collisionDetector = collisionDetector;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public BattleRoom createBattle(RoomManager roomManager, String battleId,
                                   List<Long> playerIds, int teamCount,
                                   int neededBots, int aiLevel,
                                   BattleStateBroadcaster broadcaster,
                                   BattleEndHandler endHandler) {
        BattleRoom room = roomManager.createRoom(battleId, playerIds, teamCount);
        if (room == null) {
            log.error("Failed to create room: {}", battleId);
            return null;
        }

        GameMode mode = teamCount == 3 ? GameMode.MODE_3V3V3 : GameMode.MODE_5V5;
        room.setGameMode(mode);

        MOBAMap mobaMap = mapManager.createMap(battleId, 1, mode);
        room.getSession().setMapWidth(mobaMap.getWidth());
        room.getSession().setMapHeight(mobaMap.getHeight());

        room.getEngine().setEventListener(endHandler);

        if (neededBots > 0) {
            aiController.createBotsForBattle(battleId, room.getSession(),
                    playerIds.size() / teamCount, neededBots, aiLevel);
            room.getEngine().enableAI(aiController, aiLevel);
            log.info("AI bots created for battle {}, count={}, level={}", battleId, neededBots, aiLevel);
        }

        for (Long playerId : playerIds) {
            Optional<Player> playerOpt = playerManager.getPlayerById(playerId);
            playerOpt.ifPresent(p -> {
                p.setState(Player.PlayerState.IN_BATTLE);
                p.setCurrentBattleId(Long.parseLong(battleId.replace("BATTLE_", "")));
            });
        }

        scheduler.schedule(() -> {
                room.setState(BattleRoom.RoomState.LOADING);
                room.getSession().setState(BattleSession.BattleState.LOADING);
                broadcaster.notifyPlayersLoading(room);

                scheduler.schedule(() -> {
                    room.start();
                    room.getSession().start();
                    roomManager.startRoomTick(room);
                    log.info("Battle {} started with {} players ({} bots), seed={}",
                            battleId, playerIds.size(), neededBots, room.getEngine().getRandomSeed());
                }, 3, TimeUnit.SECONDS);
            }, 500, TimeUnit.MILLISECONDS);

        return room;
    }
}
