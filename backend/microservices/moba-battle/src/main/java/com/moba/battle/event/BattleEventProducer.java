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

    private DefaultMQProducer producer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean available = false;

    public BattleEventProducer(ServerConfig serverConfig) {
        String nameServer = serverConfig.getRocketmqNameServer();
        if (nameServer != null && !nameServer.isEmpty()) {
            try {
                DefaultMQProducer p = new DefaultMQProducer(serverConfig.getRocketmqProducerGroup());
                p.setNamesrvAddr(nameServer);
                p.setSendMsgTimeout(serverConfig.getRocketmqSendMsgTimeout());
                p.start();
                this.producer = p;
                this.available = true;
                log.info("RocketMQ生产者已启动, nameServer={}", nameServer);
            } catch (Exception e) {
                log.warn("RocketMQ生产者启动失败, 事件发布已禁用: {}", e.getMessage());
                this.producer = null;
            }
        } else {
            this.producer = null;
            log.info("RocketMQ nameServer未配置, 事件发布已禁用");
        }
    }

    public void publishBattleEnd(BattleEndEvent event) {
        if (!available || producer == null) {
            log.warn("RocketMQ生产者不可用, 跳过战斗结束事件发布");
            return;
        }

        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            Message msg = new Message(EventTopics.BATTLE_END, body);
            producer.send(msg);
            String battleId = event.getResult() != null ? event.getResult().getBattleId() : "unknown";
            log.info("战斗结束事件已发布: battleId={}", battleId);
        } catch (Exception e) {
            String battleId = event.getResult() != null ? event.getResult().getBattleId() : "unknown";
            log.error("发布战斗结束事件失败: battleId={}", battleId, e);
        }
    }

    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
            log.info("RocketMQ生产者已关闭");
        }
    }
}
