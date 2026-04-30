package com.moba.battleserver.service;

import com.moba.battleserver.ServiceLocator;
import com.moba.battleserver.battle.LockstepEngine;
import com.moba.battleserver.manager.*;
import com.moba.battleserver.model.BattlePlayer;
import com.moba.battleserver.model.BattleSession;
import com.moba.battleserver.model.FrameState;
import com.moba.battleserver.model.Player;
import com.moba.battleserver.monitor.ServerMonitor;
import com.moba.battleserver.replay.ReplaySystem;
import com.moba.battleserver.storage.BattleLogStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BattleEndHandler implements LockstepEngine.BattleEventListener {
    private final PlayerManager playerManager;
    private final SettlementSystem settlementSystem;
    private final ReplaySystem replaySystem;
    private final BattleLogStorage battleLogStorage;
    private final SpectatorManager spectatorManager;
    private final ScheduledExecutorService scheduler;

    public BattleEndHandler(PlayerManager playerManager, SettlementSystem settlementSystem,
                            ReplaySystem replaySystem, BattleLogStorage battleLogStorage,
                            SpectatorManager spectatorManager) {
        this.playerManager = playerManager;
        this.settlementSystem = settlementSystem;
        this.replaySystem = replaySystem;
        this.battleLogStorage = battleLogStorage;
        this.spectatorManager = spectatorManager;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void onFrameUpdate(int frameNumber, FrameState state) {
    }

    @Override
    public void onPlayerMove(long playerId, int x, int y) {
    }

    @Override
    public void onPlayerAttack(long attackerId, long targetId, int damage) {
    }

    @Override
    public void onSkillCast(long playerId, int skillId, long targetId, int damage) {
    }

    @Override
    public void onPlayerKill(long killerId, long victimId) {
        log.info("Player {} killed player {}", killerId, victimId);
    }

    @Override
    public void onPlayerRespawn(long playerId) {
        log.info("Player {} respawned", playerId);
    }

    @Override
    public void onItemUse(long playerId, int itemId) {
    }

    @Override
    public void onItemBuy(long playerId, int itemId, int price) {
    }

    @Override
    public void onGameOver() {
        BattleRoom room = findFinishedRoom();
        if (room != null) {
            handleBattleEnd(room);
        }
    }

    public void handleBattleEnd(BattleRoom room) {
        room.setState(BattleRoom.RoomState.FINISHED);
        room.getSession().finish();
        log.info("Battle {} ended, calculating settlement", room.getBattleId());

        SettlementSystem.BattleSettlementResult settlement = settlementSystem.calculateSettlement(room);
        log.info("Settlement result: {}", settlement.toJson());

        replaySystem.recordReplay(room.getBattleId(), room.getSession());

        BattleStateBroadcaster broadcaster = ServiceLocator.getInstance().getBattleStateBroadcaster();
        broadcaster.broadcastBattleEnd(room);

        battleLogStorage.submitBattleEvent(room.getBattleId(), "GAME_OVER", "battle ended");

        scheduler.schedule(() -> {
            RoomManager roomManager = ServiceLocator.getInstance().getRoomManager();
            roomManager.removeRoom(room.getBattleId());
            log.info("Battle room {} cleaned up", room.getBattleId());
        }, 30, TimeUnit.SECONDS);
    }

    private BattleRoom findFinishedRoom() {
        RoomManager roomManager = ServiceLocator.getInstance().getRoomManager();
        for (BattleRoom room : roomManager.getAllRooms()) {
            if (room.getSession().getState() == BattleSession.BattleState.FINISHED) {
                return room;
            }
        }
        return null;
    }
}
