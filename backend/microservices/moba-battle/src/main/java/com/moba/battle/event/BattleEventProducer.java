package com.moba.battle.event;

import com.moba.battle.config.ServerConfig;
import com.moba.common.event.BattleEndEvent;
import com.moba.common.event.EventTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class BattleEventProducer {

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean available = false;

    public BattleEventProducer(ServerConfig serverConfig) {
        String nameServer = serverConfig.getRocketmqNameServer();
        if (nameServer != null && !nameServer.isEmpty()) {
            try {
                producer = new DefaultMQProducer(serverConfig.getRocketmqProducerGroup());
                producer.setNamesrvAddr(nameServer);
                producer.setSendMsgTimeout(3000);
                producer.start();
                available = true;
                log.info("RocketMQ producer started, nameServer={}", nameServer);
            } catch (Exception e) {
                log.warn("RocketMQ producer start failed, event publishing disabled: {}", e.getMessage());
                producer = null;
            }
        } else {
            producer = null;
            log.info("RocketMQ nameServer not configured, event publishing disabled");
        }
    }

    public void publishBattleEnd(BattleEndEvent event) {
        if (!available || producer == null) {
            log.warn("RocketMQ producer not available, skipping battle end event publish");
            return;
        }

        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            Message msg = new Message(EventTopics.BATTLE_END, body);
            producer.send(msg);
            String battleId = event.getResult() != null ? event.getResult().getBattleId() : "unknown";
            log.info("Battle end event published: battleId={}", battleId);
        } catch (Exception e) {
            String battleId = event.getResult() != null ? event.getResult().getBattleId() : "unknown";
            log.error("Failed to publish battle end event: battleId={}", battleId, e);
        }
    }

    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
            log.info("RocketMQ producer shutdown");
        }
    }
}
