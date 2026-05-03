package com.moba.match.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.moba.match.config.NacosServiceRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BattleServiceDiscovery {

    @Value("${nacos.find.battle.service.group}")
    private String group;

    @Value("${nacos.find.battle.service.name}")
    private String battleServiceName;

    private final NacosServiceRegistry nacosServiceRegistry;
    private final Map<String, BattleServerInfo> serverCache = new java.util.concurrent.ConcurrentHashMap<>();
    private ScheduledExecutorService refreshScheduler;

    public BattleServiceDiscovery(NacosServiceRegistry nacosServiceRegistry) {
        this.nacosServiceRegistry = nacosServiceRegistry;
    }

    @PostConstruct
    public void init() {
        NamingService namingService = nacosServiceRegistry.getNamingService();
        if (namingService == null) {
            log.warn("NacosServiceRegistry中NamingService不可用, 战斗服务发现无法启动");
            return;
        }

        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "battle-service-refresh");
            t.setDaemon(true);
            return t;
        });

        refreshBattleServers();
        refreshScheduler.scheduleAtFixedRate(this::refreshBattleServers, 5, 10, TimeUnit.SECONDS);
        log.info("战斗服务发现刷新已启动, 间隔=10秒");
    }

    private void refreshBattleServers() {
        NamingService namingService = nacosServiceRegistry.getNamingService();
        if (namingService == null) return;

        try {
            List<Instance> wsInstances = namingService.selectInstances(battleServiceName, group, true);

            serverCache.clear();

            for (Instance wsInst : wsInstances) {
                BattleServerInfo info = new BattleServerInfo();
                info.setInstanceId(wsInst.getInstanceId());
                info.setIp(wsInst.getIp());
                info.setWsPort(wsInst.getPort());
                info.setHealthy(wsInst.isHealthy());

                Map<String, String> wsMeta = wsInst.getMetadata();
                info.setRoomCount(parseIntSafe(wsMeta.get("roomCount")));
                info.setMaxRooms(parseIntSafe(wsMeta.get("maxRooms")));
                info.setPlayerCount(parseIntSafe(wsMeta.get("playerCount")));
                info.setCpuUsage(parseIntSafe(wsMeta.get("cpuUsage")));
                info.setMemoryUsage(parseIntSafe(wsMeta.get("memoryUsage")));
                info.setTickDelayMs(parseIntSafe(wsMeta.get("tickDelayMs")));
                info.setLoadScore(parseIntSafe(wsMeta.getOrDefault("loadScore", "100")));
                info.setLastReportTime(parseLongSafe(wsMeta.getOrDefault("lastReportTime", "0")));

                String tcpPortStr = wsMeta.get("battleTcpPort");
                if (tcpPortStr != null) {
                    info.setTcpPort(parseIntSafe(tcpPortStr));
                }

                serverCache.put(wsInst.getInstanceId(), info);
            }

            log.debug("刷新战斗服务列表: {} 个健康实例", serverCache.size());
        } catch (NacosException e) {
            log.warn("刷新战斗服务列表失败: {}", e.getMessage());
        }
    }

    public BattleServerInfo selectBestBattleServer() {
        if (serverCache.isEmpty()) {
            refreshBattleServers();
        }

        if (serverCache.isEmpty()) {
            log.warn("没有可用的战斗服务器");
            return null;
        }

        List<BattleServerInfo> healthyServers = serverCache.values().stream()
                .filter(BattleServerInfo::isHealthy)
                .filter(s -> s.getRoomCount() < s.getMaxRooms())
                .sorted((a, b) -> Double.compare(b.getAvailabilityScore(), a.getAvailabilityScore()))
                .toList();

        if (healthyServers.isEmpty()) {
            log.warn("所有战斗服务器已满载");
            return null;
        }

        BattleServerInfo best = healthyServers.get(0);
        log.info("选择最佳战斗服务器: {}:{} (负载分数={}, 房间={}/{}, 可用性={})",
                best.getIp(), best.getWsPort(), best.getLoadScore(),
                best.getRoomCount(), best.getMaxRooms(), best.getAvailabilityScore());
        return best;
    }

    public List<BattleServerInfo> getAllBattleServers() {
        return new ArrayList<>(serverCache.values());
    }

    public int getAvailableServerCount() {
        return (int) serverCache.values().stream()
                .filter(BattleServerInfo::isHealthy)
                .filter(s -> s.getRoomCount() < s.getMaxRooms())
                .count();
    }

    @PreDestroy
    public void shutdown() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdown();
        }
    }

    private int parseIntSafe(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLongSafe(String value) {
        try {
            return value != null ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
