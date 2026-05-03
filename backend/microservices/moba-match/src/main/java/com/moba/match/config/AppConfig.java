package com.moba.match.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.netty.protocol.config.MessageProtocolAutoConfiguration;
import com.moba.netty.spring.SpringContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ComponentScan("com.moba.match")
@Import(MessageProtocolAutoConfiguration.class)
@EnableScheduling
public class AppConfig {

    @Bean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
