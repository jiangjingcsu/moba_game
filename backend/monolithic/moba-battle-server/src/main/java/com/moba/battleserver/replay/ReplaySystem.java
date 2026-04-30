package com.moba.battleserver.replay;

import com.moba.battleserver.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class ReplaySystem {
    private static final int SNAPSHOT_INTERVAL = 60;
    private static final String REPLAY_DIR = "replays";

    private final String replayDir;

    public ReplaySystem() {
        this.replayDir = REPLAY_DIR;
        try {
            Files.createDirectories(Paths.get(replayDir));
        } catch (IOException e) {
            log.error("Failed to create replay directory", e);
        }
    }

    public void recordReplay(String battleId, BattleSession session) {
        try {
            ReplayData replayData = buildReplayData(battleId, session);
            String filePath = getReplayFilePath(battleId);
            saveReplay(replayData, filePath);
            log.info("Replay saved: {} ({} frames, {} events)", battleId, replayData.getTotalFrames(), replayData.getEvents().size());
        } catch (Exception e) {
            log.error("Failed to save replay for battle: {}", battleId, e);
        }
    }

    public ReplayData loadReplay(String battleId) {
        try {
            String filePath = getReplayFilePath(battleId);
            return loadReplayFile(filePath);
        } catch (Exception e) {
            log.error("Failed to load replay for battle: {}", battleId, e);
            return null;
        }
    }

    public List<String> listReplays() {
        try {
            List<String> replays = new ArrayList<>();
            Files.list(Paths.get(replayDir))
                    .filter(p -> p.toString().endsWith(".replay"))
                    .forEach(p -> replays.add(p.getFileName().toString()));
            return replays;
        } catch (IOException e) {
            log.error("Failed to list replays", e);
            return Collections.emptyList();
        }
    }

    public ReplaySession createReplaySession(String battleId) {
        ReplayData replayData = loadReplay(battleId);
        if (replayData == null) return null;

        return new ReplaySession(battleId, replayData);
    }

    private ReplayData buildReplayData(String battleId, BattleSession session) {
        ReplayData data = new ReplayData();
        data.setBattleId(battleId);
        data.setMapId(session.getMapId());
        data.setStartTime(session.getStartTime());
        data.setEndTime(session.getEndTime());

        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            ReplayPlayerInfo playerInfo = new ReplayPlayerInfo();
            playerInfo.setPlayerId(entry.getKey());
            playerInfo.setHeroId(entry.getValue().getHeroId());
            playerInfo.setTeamId(entry.getValue().getTeamId());
            playerInfo.setFinalKills(entry.getValue().getKillCount());
            playerInfo.setFinalDeaths(entry.getValue().getDeathCount());
            playerInfo.setFinalAssists(entry.getValue().getAssistCount());
            data.getPlayers().add(playerInfo);
        }

        data.setEvents(new ArrayList<>(session.getBattleEvents()));

        return data;
    }

    private String getReplayFilePath(String battleId) {
        return replayDir + File.separator + battleId + ".replay";
    }

    private void saveReplay(ReplayData data, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             ObjectOutputStream oos = new ObjectOutputStream(gzos)) {
            oos.writeObject(data);
        }
    }

    @SuppressWarnings("unchecked")
    private ReplayData loadReplayFile(String filePath) throws IOException, ClassNotFoundException {
        try (FileInputStream fis = new FileInputStream(filePath);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             ObjectInputStream ois = new ObjectInputStream(gzis)) {
            return (ReplayData) ois.readObject();
        }
    }

    @Data
    public static class ReplayData implements Serializable {
        private String battleId;
        private int mapId;
        private long startTime;
        private long endTime;
        private List<ReplayPlayerInfo> players;
        private List<BattleSession.BattleEvent> events;
        private int totalFrames;

        public ReplayData() {
            this.players = new ArrayList<>();
            this.events = new ArrayList<>();
        }
    }

    @Data
    public static class ReplayPlayerInfo implements Serializable {
        private long playerId;
        private int heroId;
        private int teamId;
        private int finalKills;
        private int finalDeaths;
        private int finalAssists;
    }

    public static class ReplaySession {
        private final String battleId;
        private final ReplayData replayData;
        private int currentFrame;

        public ReplaySession(String battleId, ReplayData replayData) {
            this.battleId = battleId;
            this.replayData = replayData;
            this.currentFrame = 0;
        }

        public void seekToFrame(int frameNumber) {
            this.currentFrame = frameNumber;
        }

        public void playNextFrame() {
            currentFrame++;
        }

        public String getBattleId() { return battleId; }
        public ReplayData getReplayData() { return replayData; }
        public int getCurrentFrame() { return currentFrame; }
    }
}
