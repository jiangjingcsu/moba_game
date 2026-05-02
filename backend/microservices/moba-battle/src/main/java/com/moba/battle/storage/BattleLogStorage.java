package com.moba.battle.storage;

import com.moba.battle.config.SpringContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
public class BattleLogStorage {
    private final String storageDir;
    private final ExecutorService asyncExecutor;
    private final BlockingQueue<BattleLogEntry> logQueue;
    private volatile boolean running;

    public BattleLogStorage() {
        this.storageDir = "battle_logs";
        this.asyncExecutor = Executors.newSingleThreadExecutor();
        this.logQueue = new LinkedBlockingQueue<>();
        this.running = true;

        try {
            Files.createDirectories(Paths.get(storageDir));
        } catch (IOException e) {
            log.error("创建存储目录失败", e);
        }

        startLogConsumer();
    }

    public static BattleLogStorage getInstance() {
        return SpringContextHolder.getBean(BattleLogStorage.class);
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

    public void submitBattleEvent(String battleId, String eventType, String data) {
        BattleLogEntry entry = new BattleLogEntry();
        entry.setBattleId(battleId);
        entry.setEventType(eventType);
        entry.setData(data);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setLogType(BattleLogEntry.LogType.EVENT);
        submitLog(entry);
    }

    public void submitBattleSnapshot(String battleId, int frameNumber, String stateJson) {
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
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date(entry.getTimestamp()));
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

    public List<BattleLogEntry> loadBattleLogs(String battleId, String dateStr) {
        List<BattleLogEntry> logs = new ArrayList<>();
        String filePath = storageDir + File.separator + dateStr + File.separator + battleId + ".log.gz";

        try (FileInputStream fis = new FileInputStream(filePath);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzis))) {

            String line;
            while ((line = reader.readLine()) != null) {
                BattleLogEntry entry = deserializeLogEntry(line);
                if (entry != null) {
                    logs.add(entry);
                }
            }
        } catch (IOException e) {
            log.error("加载战斗日志失败 {}: {}", battleId, dateStr, e);
        }

        return logs;
    }

    private BattleLogEntry deserializeLogEntry(String line) {
        try {
            String[] parts = line.split("\\|", 5);
            if (parts.length < 5) return null;

            BattleLogEntry entry = new BattleLogEntry();
            entry.setTimestamp(Long.parseLong(parts[0]));
            entry.setLogType(BattleLogEntry.LogType.valueOf(parts[1]));
            entry.setEventType(parts[2]);
            entry.setFrameNumber(Integer.parseInt(parts[3]));
            entry.setData(parts[4]);
            return entry;
        } catch (Exception e) {
            log.error("反序列化日志条目失败: {}", line, e);
            return null;
        }
    }

    public void stop() {
        running = false;
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
        }
    }

    @Data
    public static class BattleLogEntry {
        private String battleId;
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
