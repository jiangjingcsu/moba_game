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

    private final DefaultMQPushConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatchSuccessConsumer(ServerConfig serverConfig) {
        String nameServer = serverConfig.getRocketmqNameServer();
        if (nameServer != null && !nameServer.isEmpty()) {
            try {
                consumer = new DefaultMQPushConsumer("moba-battle-consumer");
                consumer.setNamesrvAddr(nameServer);
                consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
                consumer.subscribe(EventTopics.MATCH_SUCCESS, "*");
                consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                    for (org.apache.rocketmq.common.message.MessageExt msg : msgs) {
                        try {
                            MatchSuccessEvent event = objectMapper.readValue(msg.getBody(), MatchSuccessEvent.class);
                            onMessage(event);
                        } catch (Exception e) {
                            log.error("Failed to process match success message", e);
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                });
                consumer.start();
                log.info("RocketMQ consumer started, nameServer={}", nameServer);
            } catch (Exception e) {
                log.warn("RocketMQ consumer start failed: {}", e.getMessage());
                throw new RuntimeException("Failed to start RocketMQ consumer", e);
            }
        } else {
            consumer = null;
            log.info("RocketMQ nameServer not configured, match event consuming disabled");
        }
    }

    public void onMessage(MatchSuccessEvent event) {
        log.info("Received match success event: matchId={}, players={}", event.getMatchId(), event.getPlayerIds().size());
        log.info("Battle already created via Dubbo RPC, skipping duplicate creation for matchId={}", event.getMatchId());
    }

    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("RocketMQ consumer shutdown");
        }
    }
}
