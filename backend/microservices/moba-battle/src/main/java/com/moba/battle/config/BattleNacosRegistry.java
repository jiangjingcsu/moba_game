package com.moba.battle.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.moba.battle.monitor.ServerMonitor;
import com.moba.common.util.NetworkUtil;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BattleNacosRegistry {

    private final ServerConfig serverConfig;
    private final ServerMonitor serverMonitor;
    private NamingService namingService;
    private volatile boolean registered = false;
    private final int reportIntervalSeconds;

    private final ScheduledExecutorService reportScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nacos-load-report");
        t.setDaemon(true);
        return t;
    });

    public BattleNacosRegistry(ServerConfig serverConfig, ServerMonitor serverMonitor) {
        this.serverConfig = serverConfig;
        this.serverMonitor = serverMonitor;
        this.reportIntervalSeconds = serverConfig.getLoadReportIntervalSeconds();
    }

    public NamingService getNamingService() {
        return namingService;
    }

    @PostConstruct
    public void init() {
        namingService = NetworkUtil.createNamingService(
                serverConfig.getNacosServerAddr(),
                serverConfig.getNacosNamespace(),
                serverConfig.getNacosUsername(),
                serverConfig.getNacosPassword()
        );
    }

    @EventListener
    public void onContextReady(org.springframework.context.event.ContextRefreshedEvent event) {
        if (namingService == null) {
            return;
        }

        try {
            String localIp = getLocalIp();
            registerInstance(localIp);
            registered = true;
            log.info("战斗服务已注册到Nacos: serviceName={}, group={}, WS端口={}",
                    serverConfig.getWsServiceName(), serverConfig.getNacosGroup(), serverConfig.getPort());
            startLoadReporting();
        } catch (Exception e) {
            log.warn("战斗服务注册Nacos失败, 服务将继续运行但无法被服务发现: {}", e.getMessage());
            namingService = null;
        }
    }

    private void registerInstance(String ip) throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(serverConfig.getPort());
        instance.setWeight(1.0);
        instance.setHealthy(true);
        instance.setEnabled(true);

        Map<String, String> metadata = buildBaseMetadata();
        metadata.put("protocol", "websocket");
        metadata.put("wsPath", serverConfig.getWsPath());
        metadata.put("battleTcpPort", String.valueOf(serverConfig.getBattleServerPort()));
        instance.setMetadata(metadata);

        namingService.registerInstance(serverConfig.getWsServiceName(), serverConfig.getNacosGroup(), instance);
        log.info("战斗服务实例已注册: {}:{}|{}", ip, serverConfig.getPort(), serverConfig.getWsServiceName());
    }

    private Map<String, String> buildBaseMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("maxRooms", String.valueOf(serverConfig.getMaxRooms()));
        metadata.put("roomCount", "0");
        metadata.put("playerCount", "0");
        metadata.put("cpuUsage", "0");
        metadata.put("memoryUsage", "0");
        metadata.put("tickDelayMs", "0");
        metadata.put("loadScore", "100");
        return metadata;
    }

    private void startLoadReporting() {
        reportScheduler.scheduleAtFixedRate(this::reportLoadInfo, reportIntervalSeconds, reportIntervalSeconds, TimeUnit.SECONDS);
        log.info("负载信息上报已启动, 间隔={}秒", reportIntervalSeconds);
    }

    private void reportLoadInfo() {
        if (!registered || namingService == null) return;

        try {
            ServerMonitor.ServerMetrics metrics = serverMonitor.getCurrentMetrics();

            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int cpuUsage = 0;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                cpuUsage = (int) sunOsBean.getCpuLoad() * 100;
                if (cpuUsage < 0) cpuUsage = 0;
            }

            Runtime runtime = Runtime.getRuntime();
            long totalMem = runtime.totalMemory();
            long freeMem = runtime.freeMemory();
            int memoryUsage = (int) ((totalMem - freeMem) * 100 / totalMem);

            int roomCount = metrics.getRoomCount();
            int maxRooms = serverConfig.getMaxRooms();
            int playerCount = metrics.getPlayerCount();
            int tickDelay = metrics.getAvgTickDelayMs();

            int loadScore = calculateLoadScore(roomCount, maxRooms, cpuUsage, memoryUsage, tickDelay);

            String localIp = getLocalIp();

            updateInstanceMetadata(serverConfig.getWsServiceName(), localIp, serverConfig.getPort(),
                    roomCount, playerCount, cpuUsage, memoryUsage, tickDelay, loadScore);

            serverMonitor.updateCpuUsage(cpuUsage);
            serverMonitor.updateMemoryUsage(memoryUsage);

            log.debug("负载信息已上报: 房间={}/{}, 玩家={}, CPU={}%, 内存={}%, tick={}ms, 负载分数={}",
                    roomCount, maxRooms, playerCount, cpuUsage, memoryUsage, tickDelay, loadScore);
        } catch (Exception e) {
            log.warn("负载信息上报失败: {}", e.getMessage());
        }
    }

    private int calculateLoadScore(int roomCount, int maxRooms, int cpuUsage, int memoryUsage, int tickDelay) {
        double roomLoad = maxRooms > 0 ? (double) roomCount / maxRooms : 0;
        double cpuLoad = cpuUsage / 100.0;
        double memLoad = memoryUsage / 100.0;
        double tickLoad = Math.min(1.0, tickDelay / 100.0);

        double score = 100 - (roomLoad * 40 + cpuLoad * 25 + memLoad * 20 + tickLoad * 15);
        return Math.max(0, Math.min(100, (int) score));
    }

    private void updateInstanceMetadata(String serviceName, String ip, int port,
                                         int roomCount, int playerCount, int cpuUsage,
                                         int memoryUsage, int tickDelay, int loadScore) throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("roomCount", String.valueOf(roomCount));
        metadata.put("playerCount", String.valueOf(playerCount));
        metadata.put("cpuUsage", String.valueOf(cpuUsage));
        metadata.put("memoryUsage", String.valueOf(memoryUsage));
        metadata.put("tickDelayMs", String.valueOf(tickDelay));
        metadata.put("loadScore", String.valueOf(loadScore));
        metadata.put("maxRooms", String.valueOf(serverConfig.getMaxRooms()));
        metadata.put("lastReportTime", String.valueOf(System.currentTimeMillis()));
        instance.setMetadata(metadata);

        namingService.registerInstance(serviceName, serverConfig.getNacosGroup(), instance);
    }

    @PreDestroy
    public void deregister() {
        reportScheduler.shutdown();

        if (namingService == null) return;

        try {
            String localIp = getLocalIp();

            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(serverConfig.getPort());
            namingService.deregisterInstance(serverConfig.getWsServiceName(), serverConfig.getNacosGroup(), instance);

            log.info("战斗服务已从Nacos注销");
        } catch (NacosException e) {
            log.error("战斗服务从Nacos注销失败: {}", e.getMessage());
        }

        try {
            namingService.shutDown();
        } catch (NacosException e) {
            log.warn("Nacos NamingService关闭失败", e);
        }
    }

    private String getLocalIp() {
        return NetworkUtil.getLocalIp(serverConfig.getRegisterIp());
    }
}
