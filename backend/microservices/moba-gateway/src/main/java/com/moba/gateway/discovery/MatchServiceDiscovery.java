package com.moba.gateway.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class MatchServiceDiscovery {

    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.namespace:public}")
    private String namespace;

    @Value("${nacos.group:DEFAULT_GROUP}")
    private String group;

    @Value("${nacos.username:nacos}")
    private String username;

    @Value("${nacos.password:nacos}")
    private String password;

    @Value("${gateway.match.service-name:moba-match}")
    private String serviceName;

    @Value("${gateway.match.refresh-interval-seconds:5}")
    private int refreshIntervalSeconds;

    private NamingService namingService;
    private final Map<String, MatchServerInfo> serverCache = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private ScheduledExecutorService refreshScheduler;

    @Data
    public static class MatchServerInfo {
        private String instanceId;
        private String ip;
        private int wsPort;
        private String wsPath;
        private String rankTier;
        private boolean healthy;
        private int connectionCount;
        private long lastRefreshTime;
    }

    @PostConstruct
    public void init() {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            properties.setProperty("namespace", namespace);
            properties.setProperty("username", username);
            properties.setProperty("password", password);

            namingService = NamingFactory.createNamingService(properties);
            log.info("匹配服务发现: NamingService已创建, serverAddr={}", serverAddr);

            refreshMatchServers();

            refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "match-service-refresh");
                t.setDaemon(true);
                return t;
            });
            refreshScheduler.scheduleAtFixedRate(this::refreshMatchServers,
                    refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
            log.info("匹配服务发现刷新已启动, 间隔={}秒", refreshIntervalSeconds);
        } catch (NacosException e) {
            log.error("匹配服务发现初始化失败: {}", e.getMessage());
        }
    }

    private void refreshMatchServers() {
        if (namingService == null) return;

        try {
            List<Instance> instances = namingService.selectInstances(serviceName, group, true);

            Map<String, MatchServerInfo> newCache = new ConcurrentHashMap<>();
            for (Instance inst : instances) {
                MatchServerInfo info = new MatchServerInfo();
                info.setInstanceId(inst.getInstanceId());
                info.setIp(inst.getIp());
                info.setWsPort(inst.getPort());
                info.setHealthy(inst.isHealthy());
                info.setLastRefreshTime(System.currentTimeMillis());

                Map<String, String> metadata = inst.getMetadata();
                info.setWsPath(metadata.getOrDefault("wsPath", "/ws/match"));
                info.setRankTier(metadata.getOrDefault("rankTier", "bronze"));
                info.setConnectionCount(parseIntSafe(metadata.getOrDefault("connectionCount", "0")));

                newCache.put(inst.getInstanceId(), info);
            }

            serverCache.clear();
            serverCache.putAll(newCache);

            log.debug("刷新匹配服务器: {}个健康实例", serverCache.size());
        } catch (NacosException e) {
            log.warn("刷新匹配服务器失败: {}", e.getMessage());
        }
    }

    public MatchServerInfo selectServer() {
        if (serverCache.isEmpty()) {
            refreshMatchServers();
        }

        List<MatchServerInfo> healthyServers = serverCache.values().stream()
                .filter(MatchServerInfo::isHealthy)
                .sorted(Comparator.comparingInt(MatchServerInfo::getConnectionCount))
                .toList();

        if (healthyServers.isEmpty()) {
            log.warn("无可用匹配服务器");
            return null;
        }

        int index = roundRobinIndex.getAndIncrement() % healthyServers.size();
        MatchServerInfo selected = healthyServers.get(index);
        log.debug("已选择匹配服务器: {}:{} (连接数={}, 段位={})",
                selected.getIp(), selected.getWsPort(), selected.getConnectionCount(), selected.getRankTier());
        return selected;
    }

    public MatchServerInfo selectServerByRankTier(String rankTier) {
        if (serverCache.isEmpty()) {
            refreshMatchServers();
        }

        List<MatchServerInfo> matchedServers = serverCache.values().stream()
                .filter(MatchServerInfo::isHealthy)
                .filter(s -> rankTier == null || rankTier.equalsIgnoreCase(s.getRankTier()))
                .sorted(Comparator.comparingInt(MatchServerInfo::getConnectionCount))
                .toList();

        if (matchedServers.isEmpty()) {
            return selectServer();
        }

        int index = roundRobinIndex.getAndIncrement() % matchedServers.size();
        return matchedServers.get(index);
    }

    public List<MatchServerInfo> getAllServers() {
        return new ArrayList<>(serverCache.values());
    }

    public int getAvailableServerCount() {
        return (int) serverCache.values().stream()
                .filter(MatchServerInfo::isHealthy)
                .count();
    }

    @PreDestroy
    public void shutdown() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdown();
        }
        if (namingService != null) {
            try {
                namingService.shutDown();
            } catch (NacosException e) {
                log.warn("NamingService关闭失败", e);
            }
        }
    }

    private int parseIntSafe(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
