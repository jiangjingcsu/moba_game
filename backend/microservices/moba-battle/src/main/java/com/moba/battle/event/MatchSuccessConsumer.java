package com.moba.battle.event;

import com.moba.battle.config.ServerConfig;
import com.moba.common.event.EventTopics;
import com.moba.common.event.MatchSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class MatchSuccessConsumer {

    private DefaultMQPushConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatchSuccessConsumer(ServerConfig serverConfig) {
        String nameServer = serverConfig.getRocketmqNameServer();
        if (nameServer != null && !nameServer.isEmpty()) {
            try {
                this.consumer = new DefaultMQPushConsumer(serverConfig.getRocketmqConsumerGroup());
                this.consumer.setNamesrvAddr(nameServer);
                this.consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
                this.consumer.subscribe(EventTopics.MATCH_SUCCESS, "*");
                this.consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                    for (org.apache.rocketmq.common.message.MessageExt msg : msgs) {
                        try {
                            MatchSuccessEvent event = objectMapper.readValue(msg.getBody(), MatchSuccessEvent.class);
                            onMessage(event);
                        } catch (Exception e) {
                            log.error("处理匹配成功消息失败", e);
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                });
                this.consumer.start();
                log.info("RocketMQ消费者已启动, nameServer={}", nameServer);
            } catch (Exception e) {
                log.warn("RocketMQ消费者启动失败, 匹配事件消费已禁用: {}", e.getMessage());
                this.consumer = null;
            }
        } else {
            this.consumer = null;
            log.info("RocketMQ nameServer未配置, 匹配事件消费已禁用");
        }
    }

    public void onMessage(MatchSuccessEvent event) {
        log.info("收到匹配成功事件: matchId={}, players={}", event.getMatchId(), event.getPlayerIds().size());
        log.info("战斗已通过Dubbo RPC创建, 跳过重复创建 matchId={}", event.getMatchId());
    }

    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("RocketMQ消费者已关闭");
        }
    }
}
