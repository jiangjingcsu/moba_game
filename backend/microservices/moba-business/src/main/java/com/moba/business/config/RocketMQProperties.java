package com.moba.business.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rocketmq")
public class RocketMQProperties {

    private String nameServer;
    private String consumerGroup = "moba-business-consumer";
    private int consumeThreadMin = 2;
    private int consumeThreadMax = 4;
    private int consumeTimeout = 15;
}
