package com.moba.battle.monitor;

import com.moba.battle.config.SpringContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ServerMonitor {
    private final ScheduledExecutorService scheduler;

    private final AtomicInteger tickDelayMs;
    private final AtomicInteger roomCount;
    private final AtomicInteger playerCount;
    private final AtomicInteger desyncCount;
    private final AtomicInteger reconnectCount;
    private final AtomicLong totalTickTime;
    private final AtomicLong tickCount;
    private final AtomicInteger cpuUsagePercent;
    private final AtomicInteger memoryUsagePercent;

    private final Map<String, MetricHistory> metricHistory;
    private static final int HISTORY_SIZE = 60;

    public ServerMonitor() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.tickDelayMs = new AtomicInteger(0);
        this.roomCount = new AtomicInteger(0);
        this.playerCount = new AtomicInteger(0);
        this.desyncCount = new AtomicInteger(0);
        this.reconnectCount = new AtomicInteger(0);
        this.totalTickTime = new AtomicLong(0);
        this.tickCount = new AtomicLong(0);
        this.cpuUsagePercent = new AtomicInteger(0);
        this.memoryUsagePercent = new AtomicInteger(0);
        this.metricHistory = new ConcurrentHashMap<>();

        startAlertCheck();
    }

    public static ServerMonitor getInstance() {
        return SpringContextHolder.getBean(ServerMonitor.class);
    }

    private void startAlertCheck() {
        scheduler.scheduleAtFixedRate(this::checkAlerts, 10, 10, TimeUnit.SECONDS);
    }

    public void recordTick(long durationMs) {
        tickDelayMs.set((int) durationMs);
        totalTickTime.addAndGet(durationMs);
        tickCount.incrementAndGet();
    }

    public void recordDesync() {
        desyncCount.incrementAndGet();
    }

    public void recordReconnect() {
        reconnectCount.incrementAndGet();
    }

    public void updateRoomCount(int count) {
        roomCount.set(count);
    }

    public void updatePlayerCount(int count) {
        playerCount.set(count);
    }

    public void updateCpuUsage(int percent) {
        cpuUsagePercent.set(percent);
    }

    public void updateMemoryUsage(int percent) {
        memoryUsagePercent.set(percent);
    }

    public ServerMetrics getCurrentMetrics() {
        ServerMetrics metrics = new ServerMetrics();
        metrics.setTickDelayMs(tickDelayMs.get());
        metrics.setRoomCount(roomCount.get());
        metrics.setPlayerCount(playerCount.get());
        metrics.setDesyncCount(desyncCount.get());
        metrics.setReconnectCount(reconnectCount.get());
        metrics.setCpuUsagePercent(cpuUsagePercent.get());
        metrics.setMemoryUsagePercent(memoryUsagePercent.get());

        long total = tickCount.get();
        if (total > 0) {
            metrics.setAvgTickDelayMs((int) (totalTickTime.get() / total));
        }
        metrics.setTotalTicks(total);

        return metrics;
    }

    private void checkAlerts() {
        ServerMetrics metrics = getCurrentMetrics();

        if (metrics.getCpuUsagePercent() > 80) {
            log.warn("ALERT: CPU usage high: {}%", metrics.getCpuUsagePercent());
            recordAlert("CPU_HIGH", metrics.getCpuUsagePercent() + "%");
        }

        if (metrics.getMemoryUsagePercent() > 90) {
            log.warn("ALERT: Memory usage high: {}%", metrics.getMemoryUsagePercent());
            recordAlert("MEMORY_HIGH", metrics.getMemoryUsagePercent() + "%");
        }

        if (metrics.getTickDelayMs() > 100) {
            log.warn("ALERT: Tick delay exceeded 100ms: {}ms", metrics.getTickDelayMs());
            recordAlert("TICK_DELAY_HIGH", metrics.getTickDelayMs() + "ms");
        }

        long totalTicks = metrics.getTotalTicks();
        if (totalTicks > 0) {
            float desyncRate = (float) metrics.getDesyncCount() / totalTicks;
            if (desyncRate > 0.001f) {
                log.error("ALERT: Desync rate exceeded 0.1%: {}%", desyncRate * 100);
                recordAlert("DESYNC_RATE_HIGH", String.format("%.3f%%", desyncRate * 100));
            }
        }
    }

    private void recordAlert(String type, String value) {
        MetricHistory history = metricHistory.computeIfAbsent(type, k -> new MetricHistory());
        history.addRecord(System.currentTimeMillis(), value);
    }

    public Map<String, MetricHistory> getAlertHistory() {
        return Collections.unmodifiableMap(metricHistory);
    }

    public void reset() {
        tickDelayMs.set(0);
        desyncCount.set(0);
        reconnectCount.set(0);
        totalTickTime.set(0);
        tickCount.set(0);
    }

    @Data
    public static class ServerMetrics {
        private int tickDelayMs;
        private int avgTickDelayMs;
        private int roomCount;
        private int playerCount;
        private int desyncCount;
        private int reconnectCount;
        private int cpuUsagePercent;
        private int memoryUsagePercent;
        private long totalTicks;
    }

    @Data
    public static class MetricHistory {
        private final List<MetricRecord> records;

        public MetricHistory() {
            this.records = Collections.synchronizedList(new ArrayList<>());
        }

        public void addRecord(long timestamp, String value) {
            records.add(new MetricRecord(timestamp, value));
            if (records.size() > HISTORY_SIZE) {
                records.remove(0);
            }
        }
    }

    @Data
    public static class MetricRecord {
        private final long timestamp;
        private final String value;

        public MetricRecord(long timestamp, String value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
