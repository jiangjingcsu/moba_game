package com.moba.battle.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.manager.PlayerManager;
import com.moba.battle.manager.RoomManager;
import com.moba.battle.model.*;
import com.moba.battle.protocol.core.GamePacket;
import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.model.FrameSyncMessage;
import com.moba.battle.protocol.model.FrameSyncMessage.EventEntry;
import com.moba.battle.protocol.model.FrameSyncMessage.PlayerCorrection;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FrameSyncBroadcaster {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String battleId;
    private final int hashCheckInterval;
    private final List<EventEntry> pendingEvents;
    private final Map<Long, Long> clientHashes;
    private final Map<Long, Integer> clientFrames;

    public FrameSyncBroadcaster(String battleId, ServerConfig serverConfig) {
        this.battleId = battleId;
        this.hashCheckInterval = serverConfig.getHashCheckIntervalFrames();
        this.pendingEvents = Collections.synchronizedList(new ArrayList<>());
        this.clientHashes = new ConcurrentHashMap<>();
        this.clientFrames = new ConcurrentHashMap<>();
    }

    public void onFrameUpdate(int frameNumber, FrameState state, List<FrameInput> inputs) {
        broadcastInputSync(frameNumber, inputs);

        if (!pendingEvents.isEmpty()) {
            broadcastEvents(frameNumber);
        }

        if (frameNumber % hashCheckInterval == 0) {
            broadcastHashCheck(frameNumber, state);
        }
    }

    private void broadcastInputSync(int frameNumber, List<FrameInput> inputs) {
        FrameSyncMessage msg = FrameSyncMessage.inputSync(frameNumber, inputs);
        broadcastToBattlePlayers(MessageType.BATTLE_FRAME_SYNC_NOTIFY, msg);
    }

    private void broadcastEvents(int frameNumber) {
        List<EventEntry> events = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        FrameSyncMessage msg = FrameSyncMessage.eventNotify(frameNumber, events);
        broadcastToBattlePlayers(MessageType.BATTLE_EVENT_NOTIFY, msg);
    }

    private void broadcastHashCheck(int frameNumber, FrameState state) {
        long hash = state.computeHash();
        FrameSyncMessage msg = FrameSyncMessage.hashCheck(frameNumber, hash);
        broadcastToBattlePlayers(MessageType.BATTLE_HASH_CHECK_NOTIFY, msg);
    }

    public void onClientHashReport(long playerId, int frameNumber, String clientHash) {
        BattleRoom room = RoomManager.getInstance().getRoom(battleId);
        if (room == null) return;

        Long serverHash = room.getEngine().getFrameHashes().get(frameNumber);
        if (serverHash == null) return;

        String serverHashHex = Long.toHexString(serverHash);
        if (!serverHashHex.equals(clientHash)) {
            log.warn("哈希不匹配: playerId={}, 帧={}, 客户端={}, 服务端={}",
                    playerId, frameNumber, clientHash, serverHashHex);
            sendStateCorrection(playerId, frameNumber, room.getEngine());
        }
    }

    private void sendStateCorrection(long playerId, int frameNumber, LockstepEngine engine) {
        List<FrameState> states = engine.getFrameStates();
        if (frameNumber < 0 || frameNumber >= states.size()) return;

        FrameState state = null;
        for (FrameState fs : states) {
            if (fs.getFrameNumber() == frameNumber) {
                state = fs;
                break;
            }
        }
        if (state == null) return;

        Map<Long, PlayerCorrection> corrections = new HashMap<>();
        for (Long pid : state.getPlayerPositions().keySet()) {
            PlayerCorrection c = PlayerCorrection.fromPlayer(state, pid);
            if (c != null) {
                corrections.put(pid, c);
            }
        }

        FrameSyncMessage msg = FrameSyncMessage.stateCorrection(frameNumber, state, corrections);
        sendToPlayer(playerId, MessageType.BATTLE_STATE_CORRECTION_NOTIFY, msg);
    }

    public void sendFullSnapshot(long playerId) {
        BattleRoom room = RoomManager.getInstance().getRoom(battleId);
        if (room == null) return;

        LockstepEngine engine = room.getEngine();
        List<FrameState> states = engine.getFrameStates();
        if (states.isEmpty()) return;

        FrameState latest = states.get(states.size() - 1);
        FrameSyncMessage msg = FrameSyncMessage.fullSnapshot(latest.getFrameNumber(), latest);
        sendToPlayer(playerId, MessageType.BATTLE_STATE_NOTIFY, msg);
    }

    public void addEvent(EventEntry event) {
        pendingEvents.add(event);
    }

    private void broadcastToBattlePlayers(MessageType messageType, FrameSyncMessage msg) {
        try {
            byte[] data = OBJECT_MAPPER.writeValueAsBytes(msg);
            BattleRoom room = RoomManager.getInstance().getRoom(battleId);
            if (room == null) return;

            BattleSession session = room.getSession();
            for (Long playerId : session.getBattlePlayers().keySet()) {
                Player player = PlayerManager.getInstance().getPlayerById(playerId).orElse(null);
                if (player != null && player.isConnected()) {
                    GamePacket packet = GamePacket.notify(messageType, data);
                    player.sendToClient(packet);
                }
            }
        } catch (Exception e) {
            log.error("广播帧同步失败: battleId={}, type={}", battleId, msg.getType(), e);
        }
    }

    private void sendToPlayer(long playerId, MessageType messageType, FrameSyncMessage msg) {
        try {
            byte[] data = OBJECT_MAPPER.writeValueAsBytes(msg);
            Player player = PlayerManager.getInstance().getPlayerById(playerId).orElse(null);
            if (player != null && player.isConnected()) {
                GamePacket packet = GamePacket.notify(messageType, data);
                player.sendToClient(packet);
            }
        } catch (Exception e) {
            log.error("发送帧同步消息失败: playerId={}, type={}", playerId, msg.getType(), e);
        }
    }
}
