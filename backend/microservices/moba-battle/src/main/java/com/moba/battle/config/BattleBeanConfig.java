package com.moba.battle.config;

import com.moba.battle.network.NettyServer;
import com.moba.netty.protocol.config.MessageProtocolAutoConfiguration;
import com.moba.netty.protocol.dispatcher.MessageDispatcher;
import com.moba.netty.session.SessionManager;
import com.moba.netty.spring.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Configuration
@ComponentScan(basePackages = "com.moba.battle")
@Import(MessageProtocolAutoConfiguration.class)
public class BattleBeanConfig {

    @Bean
    public NettyServer nettyServer(ServerConfig serverConfig,
                                   MessageDispatcher messageDispatcher,
                                   SessionManager sessionManager) {
        return new NettyServer(serverConfig, messageDispatcher, sessionManager);
    }

    @Bean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }
}
