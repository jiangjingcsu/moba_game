package com.moba.battle.config;

import com.moba.battle.network.NettyServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ComponentScan(basePackages = "com.moba.battle")
public class BattleBeanConfig {

    @Bean
    public NettyServer nettyServer(ServerConfig serverConfig) {
        return new NettyServer(serverConfig);
    }
}
