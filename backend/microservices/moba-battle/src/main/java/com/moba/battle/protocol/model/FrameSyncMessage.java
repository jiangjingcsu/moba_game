package com.moba.battle.protocol.model;

import com.moba.battle.model.FrameInput;
import com.moba.battle.model.FrameState;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class FrameSyncMessage {

    private int frameNumber;
    private String type;
    private List<InputEntry> inputs;
    private List<EventEntry> events;
    private String hash;
    private Map<Long, PlayerCorrection> corrections;
    private Map<Long, PlayerSnapshot> snapshot;

    @Data
    public static class InputEntry {
        private long playerId;
        private String inputType;
        private String data;

        public static InputEntry fromFrameInput(FrameInput fi) {
            InputEntry entry = new InputEntry();
            entry.setPlayerId(fi.getPlayerId());
            entry.setInputType(fi.getType().name());
            entry.setData(fi.getData() != null ? new String(fi.getData()) : "");
            return entry;
        }
    }

    @Data
    public static class EventEntry {
        private String eventType;
        private long sourceId;
        private long targetId;
        private int value;

        public static EventEntry kill(long killerId, long victimId) {
            EventEntry e = new EventEntry();
            e.setEventType("KILL");
            e.setSourceId(killerId);
            e.setTargetId(victimId);
            return e;
        }

        public static EventEntry death(long playerId, long killerId) {
            EventEntry e = new EventEntry();
            e.setEventType("DEATH");
            e.setSourceId(playerId);
            e.setTargetId(killerId);
            return e;
        }

        public static EventEntry respawn(long playerId) {
            EventEntry e = new EventEntry();
            e.setEventType("RESPAWN");
            e.setSourceId(playerId);
            return e;
        }

        public static EventEntry skillHit(long casterId, int skillId, long targetId, int damage) {
            EventEntry e = new EventEntry();
            e.setEventType("SKILL_HIT");
            e.setSourceId(casterId);
            e.setTargetId(targetId);
            e.setValue(damage);
            return e;
        }

        public static EventEntry attack(long attackerId, long targetId, int damage) {
            EventEntry e = new EventEntry();
            e.setEventType("ATTACK");
            e.setSourceId(attackerId);
            e.setTargetId(targetId);
            e.setValue(damage);
            return e;
        }

        public static EventEntry move(long playerId, float x, float y) {
            EventEntry e = new EventEntry();
            e.setEventType("MOVE");
            e.setSourceId(playerId);
            e.setValue((int) x);
            return e;
        }

        public static EventEntry towerDestroy(int towerId, int teamId) {
            EventEntry e = new EventEntry();
            e.setEventType("TOWER_DESTROY");
            e.setSourceId(towerId);
            e.setTargetId(teamId);
            return e;
        }
    }

    @Data
    public static class PlayerCorrection {
        private float x;
        private float y;
        private int hp;
        private int mp;

        public static PlayerCorrection fromPlayer(FrameState state, long playerId) {
            FrameState.FixedPosition pos = state.getPlayerPositions().get(playerId);
            Integer hp = state.getPlayerHp().get(playerId);
            Integer mp = state.getPlayerMp().get(playerId);
            if (pos == null && hp == null) return null;

            PlayerCorrection c = new PlayerCorrection();
            if (pos != null) {
                c.setX(pos.x);
                c.setY(pos.y);
            }
            if (hp != null) c.setHp(hp);
            if (mp != null) c.setMp(mp);
            return c;
        }
    }

    @Data
    public static class PlayerSnapshot {
        private long playerId;
        private int heroId;
        private int teamId;
        private float x;
        private float y;
        private int hp;
        private int maxHp;
        private int mp;
        private int maxMp;
        private int level;
        private int kills;
        private int deaths;
        private boolean dead;
        private boolean stealthed;
    }

    public static FrameSyncMessage inputSync(int frameNumber, List<FrameInput> frameInputs) {
        FrameSyncMessage msg = new FrameSyncMessage();
        msg.setFrameNumber(frameNumber);
        msg.setType("INPUT_SYNC");
        List<InputEntry> entries = new ArrayList<>();
        if (frameInputs != null) {
            for (FrameInput fi : frameInputs) {
                entries.add(InputEntry.fromFrameInput(fi));
            }
        }
        msg.setInputs(entries);
        return msg;
    }

    public static FrameSyncMessage hashCheck(int frameNumber, long hash) {
        FrameSyncMessage msg = new FrameSyncMessage();
        msg.setFrameNumber(frameNumber);
        msg.setType("HASH_CHECK");
        msg.setHash(Long.toHexString(hash));
        return msg;
    }

    public static FrameSyncMessage stateCorrection(int frameNumber, FrameState state, Map<Long, PlayerCorrection> corrections) {
        FrameSyncMessage msg = new FrameSyncMessage();
        msg.setFrameNumber(frameNumber);
        msg.setType("STATE_CORRECTION");
        msg.setCorrections(corrections);
        return msg;
    }

    public static FrameSyncMessage fullSnapshot(int frameNumber, FrameState state) {
        FrameSyncMessage msg = new FrameSyncMessage();
        msg.setFrameNumber(frameNumber);
        msg.setType("FULL_SNAPSHOT");
        Map<Long, PlayerSnapshot> snapshots = new HashMap<>();
        for (Map.Entry<Long, FrameState.FixedPosition> entry : state.getPlayerPositions().entrySet()) {
            PlayerSnapshot ps = new PlayerSnapshot();
            ps.setPlayerId(entry.getKey());
            ps.setX(entry.getValue().x);
            ps.setY(entry.getValue().y);
            Integer hp = state.getPlayerHp().get(entry.getKey());
            Integer mp = state.getPlayerMp().get(entry.getKey());
            ps.setHp(hp != null ? hp : 0);
            ps.setMp(mp != null ? mp : 0);
            snapshots.put(entry.getKey(), ps);
        }
        msg.setSnapshot(snapshots);
        return msg;
    }

    public static FrameSyncMessage eventNotify(int frameNumber, List<EventEntry> events) {
        FrameSyncMessage msg = new FrameSyncMessage();
        msg.setFrameNumber(frameNumber);
        msg.setType("EVENT");
        msg.setEvents(events);
        return msg;
    }
}
