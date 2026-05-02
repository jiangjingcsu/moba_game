package com.moba.match.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class DubboConfig {

    @Bean
    public ApplicationConfig applicationConfig(
            @Value("${dubbo.application.name}") String name,
            @Value("${dubbo.application.qos-enable}") boolean qosEnable) {
        ApplicationConfig config = new ApplicationConfig();
        config.setName(name);
        config.setQosEnable(qosEnable);
        return config;
    }

    @Bean
    public RegistryConfig registryConfig(
            @Value("${dubbo.registry.address}") String address,
            @Value("${dubbo.registry.username}") String username,
            @Value("${dubbo.registry.password}") String password,
            @Value("${dubbo.registry.parameters.namespace}") String namespace,
            @Value("${dubbo.registry.register-mode}") String registerMode) {
        RegistryConfig config = new RegistryConfig();
        config.setAddress(address);
        config.setUsername(username);
        config.setPassword(password);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("namespace", namespace);
        config.setParameters(parameters);
        config.setRegisterMode(registerMode);
        return config;
    }

    @Bean
    public ProtocolConfig protocolConfig(
            @Value("${dubbo.protocol.name}") String name,
            @Value("${dubbo.protocol.port}") int port,
            @Value("${dubbo.protocol.serialization}") String serialization) {
        ProtocolConfig config = new ProtocolConfig();
        config.setName(name);
        config.setPort(port);
        config.setSerialization(serialization);
        return config;
    }

    @Bean
    public ProviderConfig providerConfig(
            @Value("${dubbo.provider.prefer-serialization}") String preferSerialization) {
        ProviderConfig config = new ProviderConfig();
        config.setPreferSerialization(preferSerialization);
        return config;
    }

    @Bean
    public ConsumerConfig consumerConfig(
            @Value("${dubbo.consumer.timeout}") int timeout,
            @Value("${dubbo.consumer.retries}") int retries) {
        ConsumerConfig config = new ConsumerConfig();
        config.setTimeout(timeout);
        config.setRetries(retries);
        return config;
    }
}
