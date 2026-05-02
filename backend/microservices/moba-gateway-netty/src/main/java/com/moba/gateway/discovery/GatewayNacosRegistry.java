package com.moba.gateway.discovery;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.moba.gateway.config.GatewayConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GatewayNacosRegistry {

    private final GatewayConfig config;
    private final NacosServiceDiscovery serviceDiscovery;
    private volatile boolean registered = false;

    public GatewayNacosRegistry(GatewayConfig config, NacosServiceDiscovery serviceDiscovery) {
        this.config = config;
        this.serviceDiscovery = serviceDiscovery;
    }

    public void start() {
        if (!config.isNacosEnabled()) {
            log.info("Nacos注册已禁用，跳过网关注册");
            return;
        }

        NamingService namingService = serviceDiscovery.getNamingService();
        if (namingService == null) {
            log.warn("NamingService不可用，跳过网关注册");
            return;
        }

        try {
            String localIp = getLocalIp();

            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(config.getPort());
            instance.setWeight(1.0);
            instance.setHealthy(true);
            instance.setEnabled(true);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("protocol", config.getGatewayProtocol());
            metadata.put("wsPath", config.getWsPath());
            metadata.put("version", config.getGatewayVersion());
            instance.setMetadata(metadata);

            namingService.registerInstance(config.getGatewayServiceName(), config.getNacosGroup(), instance);
            registered = true;
            log.info("网关已注册到Nacos: serviceName={}, group={}, ip={}, port={}",
                    config.getGatewayServiceName(), config.getNacosGroup(), localIp, config.getPort());
        } catch (Exception e) {
            log.warn("网关注册Nacos失败, 服务将继续运行但无法被服务发现: {}", e.getMessage());
        }
    }

    public void stop() {
        if (!registered) return;

        NamingService namingService = serviceDiscovery.getNamingService();
        if (namingService == null) return;

        try {
            String localIp = getLocalIp();
            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(config.getPort());
            namingService.deregisterInstance(config.getGatewayServiceName(), config.getNacosGroup(), instance);
            log.info("网关已从Nacos注销");
        } catch (NacosException e) {
            log.warn("网关从Nacos注销失败: {}", e.getMessage());
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
