package com.moba.battle.manager;

import com.moba.battle.model.BattlePlayer;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.FrameState;
import com.moba.battle.model.Player;
import com.moba.battle.model.Spectator;
import com.moba.battle.storage.BattleLogStorage;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SpectatorManager {
    private final Map<Long, List<Spectator>> roomSpectators;

    private final RoomManager roomManager;
    private final PlayerManager playerManager;

    public SpectatorManager(RoomManager roomManager, PlayerManager playerManager) {
        this.roomSpectators = new ConcurrentHashMap<>();
        this.roomManager = roomManager;
        this.playerManager = playerManager;
    }

    public Spectator addSpectator(long battleId, long spectatorId, boolean isFriendSpectate) {
        BattleRoom room = roomManager.getRoom(battleId);
        if (room == null) {
            log.warn("战斗房间未找到: {}", battleId);
            return null;
        }

        if (!room.isRunning() && room.getState() != BattleRoom.RoomState.LOADING) {
            log.warn("战斗不可观战: {}", battleId);
            return null;
        }

        Spectator spectator = new Spectator(spectatorId, battleId);
        spectator.setFriendSpectate(isFriendSpectate);
        spectator.setCurrentFrame(room.getEngine().getCurrentFrame());

        roomSpectators.computeIfAbsent(battleId, k -> new CopyOnWriteArrayList<>()).add(spectator);
        room.addSpectator();

        log.info("观战者{}加入战斗{} (好友={})", spectatorId, battleId, isFriendSpectate);
        return spectator;
    }

    public void removeSpectator(long battleId, long spectatorId) {
        List<Spectator> spectators = roomSpectators.get(battleId);
        if (spectators != null) {
            spectators.removeIf(s -> s.getSpectatorId() == spectatorId);
            if (spectators.isEmpty()) {
                roomSpectators.remove(battleId);
            }

            BattleRoom room = roomManager.getRoom(battleId);
            if (room != null) {
                room.removeSpectator();
            }
        }
    }

    public List<Spectator> getSpectators(long battleId) {
        return roomSpectators.getOrDefault(battleId, Collections.emptyList());
    }

    public int getSpectatorCount(long battleId) {
        List<Spectator> spectators = roomSpectators.get(battleId);
        return spectators != null ? spectators.size() : 0;
    }

    public void broadcastFrameState(long battleId, FrameState state) {
        List<Spectator> spectators = roomSpectators.get(battleId);
        if (spectators == null || spectators.isEmpty()) return;

        for (Spectator spectator : spectators) {
            sendFrameStateToSpectator(spectator, state);
        }
    }

    private void sendFrameStateToSpectator(Spectator spectator, FrameState state) {
        switch (spectator.getMode()) {
            case FREE:
                sendFullState(spectator, state);
                break;
            case FOLLOW:
                sendPlayerFocusState(spectator, state);
                break;
            case TEAM:
                sendTeamState(spectator, state);
                break;
        }
        spectator.setCurrentFrame(state.getFrameNumber());
    }

    private void sendFullState(Spectator spectator, FrameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("SPEC|FULL|").append(state.getFrameNumber());
        if (state.getPlayerPositions() != null) {
            for (Map.Entry<Long, FrameState.FixedPosition> entry : state.getPlayerPositions().entrySet()) {
                sb.append("|").append(entry.getKey()).append(":").append(entry.getValue().x).append(",").append(entry.getValue().y);
            }
        }
        if (state.getPlayerHp() != null) {
            for (Map.Entry<Long, Integer> entry : state.getPlayerHp().entrySet()) {
                sb.append("|HP:").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        sendToSpectator(spectator, sb.toString());
    }

    private void sendPlayerFocusState(Spectator spectator, FrameState state) {
        BattleRoom room = roomManager.getRoom(spectator.getBattleId());
        if (room == null) return;

        BattlePlayer target = room.getSession().getPlayer(spectator.getFollowUserId());
        if (target == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("SPEC|FOCUS|").append(state.getFrameNumber());
        sb.append("|target=").append(target.getUserId());
        sb.append("|x=").append(target.getPosition().x);
        sb.append("|y=").append(target.getPosition().y);
        sb.append("|hp=").append(target.getCurrentHp());
        sb.append("|mp=").append(target.getCurrentMp());
        sb.append("|level=").append(target.getLevel());
        sb.append("|kills=").append(target.getKillCount());
        sb.append("|deaths=").append(target.getDeathCount());
        sendToSpectator(spectator, sb.toString());
    }

    private void sendTeamState(Spectator spectator, FrameState state) {
        BattleRoom room = roomManager.getRoom(spectator.getBattleId());
        if (room == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("SPEC|TEAM|").append(state.getFrameNumber());

        for (Map.Entry<Long, BattlePlayer> entry : room.getSession().getBattlePlayers().entrySet()) {
            BattlePlayer player = entry.getValue();
            sb.append("|P:").append(player.getUserId());
            sb.append(",team=").append(player.getTeamId());
            sb.append(",x=").append(player.getPosition().x);
            sb.append(",y=").append(player.getPosition().y);
            sb.append(",hp=").append(player.getCurrentHp());
            sb.append(",dead=").append(player.isDead());
        }
        sendToSpectator(spectator, sb.toString());
    }

    private void sendToSpectator(Spectator spectator, String data) {
        Player spectatorPlayer = playerManager.getPlayerById(spectator.getSpectatorId()).orElse(null);
        if (spectatorPlayer != null && spectatorPlayer.isConnected()) {
            MessagePacket packet = MessagePacket.of(ProtocolConstants.EXTENSION_BATTLE, ProtocolConstants.CMD_BATTLE_STATE_NOTIFY, data);
            spectatorPlayer.sendToClient(packet);
        }
    }
}
