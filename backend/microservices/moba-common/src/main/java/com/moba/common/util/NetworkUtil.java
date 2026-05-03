package com.moba.common.util;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Properties;

@Slf4j
public class NetworkUtil {

    private NetworkUtil() {
    }

    public static NamingService createNamingService(String serverAddr, String namespace, String username, String password) {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            if (namespace != null && !namespace.isEmpty()) {
                properties.setProperty("namespace", namespace);
            }
            properties.setProperty("username", username != null ? username : "nacos");
            properties.setProperty("password", password != null ? password : "nacos");

            NamingService namingService = NamingFactory.createNamingService(properties);
            log.info("Nacos NamingService创建成功: serverAddr={}", serverAddr);
            return namingService;
        } catch (NacosException e) {
            log.warn("Nacos NamingService创建失败: {}", e.getMessage());
            return null;
        }
    }

    public static String getLocalIp(String preferredIp) {
        if (preferredIp != null && !preferredIp.isEmpty()) {
            log.info("使用配置的注册IP: {}", preferredIp);
            return preferredIp;
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String fallbackIp = null;
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
                            || !addr.isSiteLocalAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (ip.startsWith("192.168.")) {
                        log.info("检测到局域网IP: {} (网卡: {})", ip, ni.getDisplayName());
                        return ip;
                    }
                    if (fallbackIp == null) {
                        fallbackIp = ip;
                    }
                }
            }

            if (fallbackIp != null) {
                log.info("未检测到192.168.x.x网段, 使用其他局域网IP: {}", fallbackIp);
                return fallbackIp;
            }

            InetAddress localHost = InetAddress.getLocalHost();
            log.warn("未检测到合适的局域网IP, 使用默认地址: {}", localHost.getHostAddress());
            return localHost.getHostAddress();
        } catch (Exception e) {
            log.warn("获取本机IP失败, 使用回环地址", e);
            return "127.0.0.1";
        }
    }
}
