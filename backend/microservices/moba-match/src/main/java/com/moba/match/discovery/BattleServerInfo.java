package com.moba.match.discovery;

import lombok.Data;

@Data
public class BattleServerInfo {

    private String instanceId;
    private String ip;
    private int wsPort;
    private int tcpPort;
    private int roomCount;
    private int maxRooms;
    private int playerCount;
    private int cpuUsage;
    private int memoryUsage;
    private int tickDelayMs;
    private int loadScore;
    private long lastReportTime;
    private boolean healthy;

    public double getAvailabilityScore() {
        double loadFactor = maxRooms > 0 ? (double) roomCount / maxRooms : 1.0;
        double cpuFactor = cpuUsage / 100.0;
        double memFactor = memoryUsage / 100.0;
        double tickFactor = Math.min(1.0, tickDelayMs / 100.0);
        double timeFactor = Math.max(0, 1.0 - (System.currentTimeMillis() - lastReportTime) / 60000.0);

        return loadScore * 0.4
                + (1 - loadFactor) * 25
                + (1 - cpuFactor) * 15
                + (1 - memFactor) * 10
                + (1 - tickFactor) * 5
                + timeFactor * 5;
    }
}
