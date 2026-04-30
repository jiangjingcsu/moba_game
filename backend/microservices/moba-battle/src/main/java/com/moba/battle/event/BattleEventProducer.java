package com.moba.battle.event;

import com.moba.common.event.BattleEndEvent;
import com.moba.common.event.EventTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(RocketMQTemplate.class)
public class BattleEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public BattleEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void publishBattleEnd(BattleEndEvent event) {
        try {
            rocketMQTemplate.syncSend(
                    EventTopics.BATTLE_END,
                    MessageBuilder.withPayload(event).build()
            );
            String battleId = event.getResult() != null ? event.getResult().getBattleId() : "unknown";
            log.info("Battle end event published: battleId={}", battleId);
        } catch (Exception e) {
            String battleId = event.getResult() != null ? event.getResult().getBattleId() : "unknown";
            log.error("Failed to publish battle end event: battleId={}", battleId, e);
        }
    }
}
