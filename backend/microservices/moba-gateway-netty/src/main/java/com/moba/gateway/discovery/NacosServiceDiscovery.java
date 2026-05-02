package com.moba.gateway.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.moba.gateway.config.GatewayConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NacosServiceDiscovery {

    private final GatewayConfig config;
    private NamingService namingService;
    private boolean nacosAvailable = false;
    private final Map<String, List<Instance>> serviceInstances = new ConcurrentHashMap<>();
    private final Map<String, EventListener> listeners = new ConcurrentHashMap<>();

    public NacosServiceDiscovery(GatewayConfig config) {
        this.config = config;
    }

    public void start() {
        if (!config.isNacosEnabled()) {
            log.info("Nacos服务发现已禁用");
            return;
        }

        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", config.getNacosServerAddr());
            properties.setProperty("namespace", config.getNacosNamespace());
            properties.setProperty("username", config.getNacosUsername());
            properties.setProperty("password", config.getNacosPassword());

            namingService = NamingFactory.createNamingService(properties);
            nacosAvailable = true;
            log.info("Nacos NamingService创建成功: serverAddr={}", config.getNacosServerAddr());

            subscribeService(config.getMatchServiceName());
            subscribeService(config.getBattleServiceName());

        } catch (NacosException e) {
            log.error("Nacos服务发现启动失败", e);
            nacosAvailable = false;
        }
    }

    private void subscribeService(String serviceName) {
        if (!nacosAvailable) return;

        try {
            List<Instance> instances = namingService.selectInstances(serviceName, config.getNacosGroup(), true);
            serviceInstances.put(serviceName, new ArrayList<>(instances));
            log.info("发现{}服务实例: {}个", serviceName, instances.size());
            for (Instance inst : instances) {
                log.info("  - {}:{} metadata={}", inst.getIp(), inst.getPort(), inst.getMetadata());
            }

            EventListener listener = new EventListener() {
                @Override
                public void onEvent(Event event) {
                    if (event instanceof NamingEvent namingEvent) {
                        List<Instance> updated = namingEvent.getInstances();
                        serviceInstances.put(serviceName, new ArrayList<>(updated));
                        log.info("{}服务实例变更: {}个健康实例", serviceName, updated.size());
                    }
                }
            };

            namingService.subscribe(serviceName, config.getNacosGroup(), listener);
            listeners.put(serviceName, listener);

        } catch (NacosException e) {
            log.error("订阅{}服务失败", serviceName, e);
        }
    }

    public List<Instance> getInstances(String serviceName) {
        List<Instance> instances = serviceInstances.get(serviceName);
        if (instances == null) {
            if (nacosAvailable) {
                try {
                    instances = namingService.selectInstances(serviceName, config.getNacosGroup(), true);
                    serviceInstances.put(serviceName, new ArrayList<>(instances));
                } catch (NacosException e) {
                    log.error("查询{}服务实例失败", serviceName, e);
                    return Collections.emptyList();
                }
            } else {
                return Collections.emptyList();
            }
        }
        return instances;
    }

    public List<Instance> getInstancesByRankTier(String serviceName, String rankTier) {
        List<Instance> instances = getInstances(serviceName);
        List<Instance> filtered = new ArrayList<>();
        for (Instance instance : instances) {
            Map<String, String> metadata = instance.getMetadata();
            if (metadata != null && rankTier.equals(metadata.get("rankTier"))) {
                filtered.add(instance);
            }
        }
        return filtered;
    }

    public Instance selectInstanceByRankTier(String serviceName, String rankTier) {
        List<Instance> instances = getInstancesByRankTier(serviceName, rankTier);
        if (instances.isEmpty()) {
            instances = getInstances(serviceName);
        }
        if (instances.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (Instance inst : instances) {
            totalWeight += (int) inst.getWeight();
        }
        int random = new Random().nextInt(totalWeight);
        int current = 0;
        for (Instance inst : instances) {
            current += (int) inst.getWeight();
            if (random < current) {
                return inst;
            }
        }
        return instances.get(0);
    }

    public boolean isNacosAvailable() {
        return nacosAvailable;
    }

    public NamingService getNamingService() {
        return namingService;
    }

    public void stop() {
        if (namingService != null) {
            for (Map.Entry<String, EventListener> entry : listeners.entrySet()) {
                try {
                    namingService.unsubscribe(entry.getKey(), config.getNacosGroup(), entry.getValue());
                } catch (NacosException e) {
                    log.error("取消订阅{}服务失败", entry.getKey(), e);
                }
            }
            listeners.clear();
        }
        nacosAvailable = false;
    }
}
