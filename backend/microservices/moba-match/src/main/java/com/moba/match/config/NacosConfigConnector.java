package com.moba.match.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class NacosConfigConnector {

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

    @Value("${nacos.config.dataId}")
    private String configDataId;

    @Value("${nacos.config.timeoutMs}")
    private long configTimeoutMs;

    private volatile ConfigService configService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private ScheduledExecutorService retryScheduler;

    @PostConstruct
    public void init() {
        retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nacos-config-retry");
            t.setDaemon(true);
            return t;
        });

        try {
            connect();
            log.info("NacosConfigConnector 已创建: serverAddr={}", serverAddr);
        } catch (NacosException e) {
            log.warn("Nacos ConfigService初始化失败: {}", e.getMessage());
            scheduleRetry();
        }
    }

    private void connect() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("namespace", namespace);
        properties.setProperty("username", username);
        properties.setProperty("password", password);
        configService = NacosFactory.createConfigService(properties);
    }

    private void scheduleRetry() {
        retryScheduler.scheduleAtFixedRate(() -> {
            try {
                if (configService == null) {
                    connect();
                    log.info("Nacos ConfigService 重连成功");
                }
            } catch (Exception ex) {
                log.warn("Nacos ConfigService 重连失败: {}", ex.getMessage());
            }
        }, 10, 30, TimeUnit.SECONDS);
    }

    public String getConfig() {
        if (configService == null) return null;
        try {
            return configService.getConfig(configDataId, group, configTimeoutMs);
        } catch (NacosException e) {
            log.warn("从Nacos加载配置失败: {}", e.getMessage());
            return null;
        }
    }

    public void addConfigListener(Consumer<String> onChange) {
        if (configService == null) return;
        try {
            configService.addListener(configDataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor();
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("收到Nacos配置变更通知: dataId={}", configDataId);
                    onChange.accept(configInfo);
                }
            });
            log.info("Nacos配置监听已注册: dataId={}, group={}", configDataId, group);
        } catch (NacosException e) {
            log.warn("注册Nacos配置监听失败: {}", e.getMessage());
        }
    }

    public ObjectMapper getYamlMapper() {
        return yamlMapper;
    }

    @PreDestroy
    public void destroy() {
        if (retryScheduler != null) {
            retryScheduler.shutdown();
        }
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (NacosException e) {
                log.warn("Nacos ConfigService关闭失败", e);
            }
        }
    }
}
