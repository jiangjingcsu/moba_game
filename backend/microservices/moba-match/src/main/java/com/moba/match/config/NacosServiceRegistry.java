package com.moba.match.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
public class NacosServiceRegistry {

    @Value("${nacos.server-addr}")
    private String serverAddr;

    @Value("${nacos.username}")
    private String username;

    @Value("${nacos.password}")
    private String password;

    @Value("${nacos.namespace}")
    private String namespace;

    @Value("${nacos.group}")
    private String group;

    @Value("${nacos.service-name}")
    private String serviceName;

    @Value("${nacos.rankTier}")
    private String rankTier;

    @Value("${match.websocket.port}")
    private int websocketPort;

    @Value("${match.websocket.path}")
    private String wsPath;

    @Value("${nacos.register.enabled}")
    private boolean registerEnabled;

    private NamingService namingService;

    public NamingService getNamingService() {
        return namingService;
    }

    @PostConstruct
    public void init() {
        if (!registerEnabled) {
            log.info("Nacos服务注册已禁用");
            return;
        }

        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            properties.setProperty("namespace", namespace);
            properties.setProperty("username", username);
            properties.setProperty("password", password);

            namingService = NamingFactory.createNamingService(properties);
            log.info("Nacos命名服务已创建: serverAddr={}", serverAddr);
        } catch (NacosException e) {
            log.warn("Nacos NamingService创建失败: {}", e.getMessage());
        }
    }

    @EventListener
    public void onContextReady(org.springframework.context.event.ContextRefreshedEvent event) {
        if (!registerEnabled || namingService == null) {
            return;
        }

        try {
            registerWebSocketInstance();
            log.info("匹配服务已注册到Nacos: serviceName={}, group={}, rankTier={}",
                    serviceName, group, rankTier);
        } catch (Exception e) {
            log.warn("匹配服务注册Nacos失败, 服务将继续运行但无法被服务发现: {}", e.getMessage());
            namingService = null;
        }
    }

    private void registerWebSocketInstance() throws NacosException {
        Instance instance = new Instance();
        instance.setIp(getLocalIp());
        instance.setPort(websocketPort);
        instance.setWeight(1.0);
        instance.setHealthy(true);
        instance.setEnabled(true);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("rankTier", rankTier);
        metadata.put("protocol", "websocket");
        metadata.put("wsPath", wsPath);
        metadata.put("version", "1.0.0");
        instance.setMetadata(metadata);

        namingService.registerInstance(serviceName, group, instance);
        log.info("WebSocket实例已注册: {}:{}|{}|rankTier={}",
                instance.getIp(), websocketPort, serviceName, rankTier);
    }

    @PreDestroy
    public void deregister() {
        if (namingService == null) {
            return;
        }

        try {
            String localIp = getLocalIp();

            Instance wsInstance = new Instance();
            wsInstance.setIp(localIp);
            wsInstance.setPort(websocketPort);
            namingService.deregisterInstance(serviceName, group, wsInstance);

            log.info("匹配服务已从Nacos注销");
        } catch (NacosException e) {
            log.error("匹配服务从Nacos注销失败: {}", e.getMessage());
        }

        try {
            namingService.shutDown();
        } catch (NacosException e) {
            log.warn("Nacos命名服务关闭失败", e);
        }
    }

    private String getLocalIp() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            if (!localHost.isLoopbackAddress()
                    && localHost.getHostAddress().startsWith("192.168.")) {
                return localHost.getHostAddress();
            }

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                String displayName = ni.getDisplayName().toLowerCase();
                String name = ni.getName().toLowerCase();
                if (displayName.contains("virtual") || displayName.contains("vmware")
                        || displayName.contains("veth") || displayName.contains("docker")
                        || displayName.contains("wsl") || displayName.contains("hyper-v")
                        || name.startsWith("veth") || name.startsWith("docker")
                        || name.startsWith("br-") || name.contains("ws")) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                            || addr.isSiteLocalAddress() == false) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (ip.startsWith("192.168.")) {
                        log.info("检测到局域网IP: {} (网卡: {})", ip, ni.getDisplayName());
                        return ip;
                    }
                }
            }

            log.warn("未检测到192.168.x.x局域网IP, 使用默认地址");
            return localHost.getHostAddress();
        } catch (Exception e) {
            log.warn("获取本机IP失败, 使用回环地址", e);
            return "127.0.0.1";
        }
    }
}
