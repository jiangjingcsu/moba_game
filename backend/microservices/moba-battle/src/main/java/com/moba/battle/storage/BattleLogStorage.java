package com.moba.battle.storage;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BattleLogStorage implements DisposableBean {
    private final String storageDir;
    private final ExecutorService asyncExecutor;
    private final BlockingQueue<BattleLogEntry> logQueue;
    private volatile boolean running;
    private final DateTimeFormatter dateFormatter;

    public BattleLogStorage() {
        this.storageDir = "battle_logs";
        this.asyncExecutor = Executors.newSingleThreadExecutor();
        this.logQueue = new LinkedBlockingQueue<>(10000);
        this.running = true;
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        try {
            Files.createDirectories(Paths.get(storageDir));
        } catch (IOException e) {
            log.error("创建存储目录失败", e);
        }

        startLogConsumer();
    }

    private void startLogConsumer() {
        asyncExecutor.submit(() -> {
            while (running) {
                try {
                    BattleLogEntry entry = logQueue.poll(1, TimeUnit.SECONDS);
                    if (entry != null) {
                        saveLogEntry(entry);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("保存战斗日志异常", e);
                }
            }
        });
    }

    public void submitLog(BattleLogEntry entry) {
        logQueue.offer(entry);
    }

    public void submitBattleEvent(long battleId, String eventType, String data) {
        BattleLogEntry entry = new BattleLogEntry();
        entry.setBattleId(battleId);
        entry.setEventType(eventType);
        entry.setData(data);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setLogType(BattleLogEntry.LogType.EVENT);
        submitLog(entry);
    }

    public void submitBattleSnapshot(long battleId, int frameNumber, String stateJson) {
        BattleLogEntry entry = new BattleLogEntry();
        entry.setBattleId(battleId);
        entry.setEventType("STATE_SNAPSHOT");
        entry.setData(stateJson);
        entry.setFrameNumber(frameNumber);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setLogType(BattleLogEntry.LogType.SNAPSHOT);
        submitLog(entry);
    }

    private void saveLogEntry(BattleLogEntry entry) {
        try {
            String dateStr = dateFormatter.format(Instant.ofEpochMilli(entry.getTimestamp()));
            String dateDir = storageDir + File.separator + dateStr;
            Files.createDirectories(Paths.get(dateDir));

            String filePath = dateDir + File.separator + entry.getBattleId() + ".log";
            File file = new File(filePath);

            try (FileOutputStream fos = new FileOutputStream(file, true);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                String logLine = serializeLogEntry(entry);
                bos.write((logLine + "\n").getBytes());
            }
        } catch (IOException e) {
            log.error("保存战斗日志失败: {}", entry.getBattleId(), e);
        }
    }

    private String serializeLogEntry(BattleLogEntry entry) {
        return entry.getTimestamp() + "|" + entry.getLogType() + "|" + entry.getEventType() + "|" + entry.getFrameNumber() + "|" + entry.getData();
    }

    @Override
    public void destroy() {
        running = false;
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Data
    public static class BattleLogEntry {
        private long battleId;
        private long timestamp;
        private LogType logType;
        private String eventType;
        private int frameNumber;
        private String data;

        public enum LogType {
            EVENT,
            SNAPSHOT,
            PLAYER_ACTION,
            SYSTEM
        }
    }
}
