package com.moba.gateway.route;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.moba.gateway.config.GatewayConfig;
import com.moba.gateway.discovery.NacosServiceDiscovery;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RankTierRouter {

    private final GatewayConfig config;
    private final NacosServiceDiscovery serviceDiscovery;

    public RankTierRouter(GatewayConfig config, NacosServiceDiscovery serviceDiscovery) {
        this.config = config;
        this.serviceDiscovery = serviceDiscovery;
    }

    public String resolveMatchServiceKey(int rankScore) {
        String rankTier = config.resolveRankTier(rankScore);

        if (serviceDiscovery.isNacosAvailable()) {
            Instance instance = serviceDiscovery.selectInstanceByRankTier(config.getMatchServiceName(), rankTier);
            if (instance != null) {
                String key = instance.getIp() + ":" + instance.getPort();
                log.debug("段位分数{} -> 段位{} -> 匹配服务实例{}", rankScore, rankTier, key);
                return key;
            }
            log.warn("Nacos中未找到段位{}对应的匹配服务实例", rankTier);
        }

        List<Instance> instances = serviceDiscovery.getInstances(config.getMatchServiceName());
        if (!instances.isEmpty()) {
            Instance inst = instances.get(0);
            return inst.getIp() + ":" + inst.getPort();
        }

        throw new IllegalStateException("无可用匹配服务实例, serviceName=" + config.getMatchServiceName());
    }

    public String resolveRankTier(int rankScore) {
        return config.resolveRankTier(rankScore);
    }
}
