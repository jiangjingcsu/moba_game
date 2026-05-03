package com.moba.battle.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.battle.battle.LockstepEngine;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.manager.BattleRoom;
import com.moba.battle.manager.PlayerManager;
import com.moba.battle.manager.RoomManager;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.FrameInput;
import com.moba.battle.model.FrameState;
import com.moba.battle.model.Player;
import com.moba.battle.protocol.model.FrameSyncMessage;
import com.moba.battle.protocol.model.FrameSyncMessage.EventEntry;
import com.moba.battle.protocol.model.FrameSyncMessage.PlayerCorrection;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FrameSyncBroadcaster {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final byte CMD_FRAME_SYNC = ProtocolConstants.CMD_BATTLE_FRAME_SYNC;
    private static final byte CMD_EVENT_NOTIFY = ProtocolConstants.CMD_BATTLE_EVENT_NOTIFY;
    private static final byte CMD_HASH_CHECK = ProtocolConstants.CMD_BATTLE_HASH_CHECK;
    private static final byte CMD_STATE_CORRECTION = ProtocolConstants.CMD_BATTLE_STATE_CORRECTION;
    private static final byte CMD_STATE_NOTIFY = ProtocolConstants.CMD_BATTLE_STATE_NOTIFY;

    private final long battleId;
    private final int hashCheckInterval;
    private final List<EventEntry> pendingEvents;
    private final Map<Long, Long> clientHashes;
    private final Map<Long, Integer> clientFrames;
    private final RoomManager roomManager;
    private final PlayerManager playerManager;

    public FrameSyncBroadcaster(long battleId, ServerConfig serverConfig,
                                RoomManager roomManager, PlayerManager playerManager) {
        this.battleId = battleId;
        this.hashCheckInterval = serverConfig.getHashCheckIntervalFrames();
        this.pendingEvents = Collections.synchronizedList(new ArrayList<>());
        this.clientHashes = new ConcurrentHashMap<>();
        this.clientFrames = new ConcurrentHashMap<>();
        this.roomManager = roomManager;
        this.playerManager = playerManager;
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
        broadcastToBattlePlayers(CMD_FRAME_SYNC, msg);
    }

    private void broadcastEvents(int frameNumber) {
        List<EventEntry> events = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        FrameSyncMessage msg = FrameSyncMessage.eventNotify(frameNumber, events);
        broadcastToBattlePlayers(CMD_EVENT_NOTIFY, msg);
    }

    private void broadcastHashCheck(int frameNumber, FrameState state) {
        long hash = state.computeHash();
        FrameSyncMessage msg = FrameSyncMessage.hashCheck(frameNumber, hash);
        broadcastToBattlePlayers(CMD_HASH_CHECK, msg);
    }

    public void onClientHashReport(long userId, int frameNumber, String clientHash) {
        BattleRoom room = roomManager.getRoom(battleId);
        if (room == null) return;

        Long serverHash = room.getEngine().getFrameHashes().get(frameNumber);
        if (serverHash == null) return;

        String serverHashHex = Long.toHexString(serverHash);
        if (!serverHashHex.equals(clientHash)) {
            log.warn("哈希不匹配: userId={}, 帧={}, 客户端={}, 服务端={}",
                    userId, frameNumber, clientHash, serverHashHex);
            sendStateCorrection(userId, frameNumber, room.getEngine());
        }
    }

    private void sendStateCorrection(long userId, int frameNumber, LockstepEngine engine) {
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
        sendToPlayer(userId, CMD_STATE_CORRECTION, msg);
    }

    public void sendFullSnapshot(long userId) {
        BattleRoom room = roomManager.getRoom(battleId);
        if (room == null) return;

        LockstepEngine engine = room.getEngine();
        List<FrameState> states = engine.getFrameStates();
        if (states.isEmpty()) return;

        FrameState latest = states.get(states.size() - 1);
        FrameSyncMessage msg = FrameSyncMessage.fullSnapshot(latest.getFrameNumber(), latest);
        sendToPlayer(userId, CMD_STATE_NOTIFY, msg);
    }

    public void addEvent(EventEntry event) {
        pendingEvents.add(event);
    }

    private void broadcastToBattlePlayers(byte cmdId, FrameSyncMessage msg) {
        try {
            String jsonData = OBJECT_MAPPER.writeValueAsString(msg);
            MessagePacket packet = MessagePacket.of(ProtocolConstants.EXTENSION_BATTLE, cmdId, jsonData);
            BattleRoom room = roomManager.getRoom(battleId);
            if (room == null) return;

            BattleSession session = room.getSession();
            for (Long userId : session.getBattlePlayers().keySet()) {
                Player player = playerManager.getPlayerById(userId).orElse(null);
                if (player != null && player.isConnected()) {
                    player.sendToClient(packet);
                }
            }
        } catch (Exception e) {
            log.error("广播帧同步失败: battleId={}, type={}", battleId, msg.getType(), e);
        }
    }

    private void sendToPlayer(long userId, byte cmdId, FrameSyncMessage msg) {
        try {
            String jsonData = OBJECT_MAPPER.writeValueAsString(msg);
            MessagePacket packet = MessagePacket.of(ProtocolConstants.EXTENSION_BATTLE, cmdId, jsonData);
            Player player = playerManager.getPlayerById(userId).orElse(null);
            if (player != null && player.isConnected()) {
                player.sendToClient(packet);
            }
        } catch (Exception e) {
            log.error("发送帧同步消息失败: userId={}, type={}", userId, msg.getType(), e);
        }
    }
}
