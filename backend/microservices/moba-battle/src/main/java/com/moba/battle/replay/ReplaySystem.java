package com.moba.battle.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.battle.config.ServerConfig;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class ReplaySystem {
    private static final String REPLAY_DIR = "replays";

    private final String replayDir;
    private final int snapshotInterval;

    public ReplaySystem(ServerConfig serverConfig) {
        this.replayDir = REPLAY_DIR;
        this.snapshotInterval = serverConfig.getReplaySnapshotInterval();
        try {
            Files.createDirectories(Paths.get(replayDir));
        } catch (IOException e) {
            log.error("创建回放目录失败", e);
        }
    }

    public void recordReplay(long battleId, BattleSession session) {
        try {
            ReplayData replayData = buildReplayData(battleId, session);
            String filePath = getReplayFilePath(battleId);
            saveReplay(replayData, filePath);
            log.info("回放已保存: {} ({}帧, {}事件)", battleId, replayData.getTotalFrames(), replayData.getEvents().size());
        } catch (Exception e) {
            log.error("保存回放失败: {}", battleId, e);
        }
    }

    public ReplayData loadReplay(long battleId) {
        try {
            String filePath = getReplayFilePath(battleId);
            return loadReplayFile(filePath);
        } catch (Exception e) {
            log.error("加载回放失败: {}", battleId, e);
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
            log.error("列出回放失败", e);
            return Collections.emptyList();
        }
    }

    public ReplaySession createReplaySession(long battleId) {
        ReplayData replayData = loadReplay(battleId);
        if (replayData == null) return null;

        return new ReplaySession(battleId, replayData);
    }

    private ReplayData buildReplayData(long battleId, BattleSession session) {
        ReplayData data = new ReplayData();
        data.setBattleId(battleId);
        data.setMapId(session.getMapId());
        data.setStartTime(session.getStartTime());
        data.setEndTime(session.getEndTime());

        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            ReplayPlayerInfo playerInfo = new ReplayPlayerInfo();
            playerInfo.setUserId(entry.getKey());
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

    private String getReplayFilePath(long battleId) {
        return replayDir + File.separator + battleId + ".replay";
    }

    private void saveReplay(ReplayData data, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             OutputStreamWriter writer = new OutputStreamWriter(gzos, StandardCharsets.UTF_8)) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(writer, data);
        }
    }

    private ReplayData loadReplayFile(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             InputStreamReader reader = new InputStreamReader(gzis, StandardCharsets.UTF_8)) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(reader, ReplayData.class);
        }
    }

    @Data
    public static class ReplayData implements Serializable {
        private long battleId;
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
        private long userId;
        private int heroId;
        private int teamId;
        private int finalKills;
        private int finalDeaths;
        private int finalAssists;
    }

    @Getter
    public static class ReplaySession {
        private final long battleId;
        private final ReplayData replayData;
        private int currentFrame;

        public ReplaySession(long battleId, ReplayData replayData) {
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
    }
}
