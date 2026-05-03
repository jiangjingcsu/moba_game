package com.moba.business.consumer;

import com.moba.business.handler.BattleSettlementHandler;
import com.moba.common.event.BattleEndEvent;
import com.moba.common.event.EventTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = EventTopics.BATTLE_END,
        consumerGroup = "${rocketmq.consumer-group:moba-business-consumer}",
        consumeThreadNumber = 4
)
@RequiredArgsConstructor
public class BattleEndConsumer implements RocketMQListener<BattleEndEvent> {

    private final BattleSettlementHandler battleSettlementHandler;

    @Override
    public void onMessage(BattleEndEvent event) {
        if (event == null) {
            log.warn("收到空战斗结算消息, 跳过处理");
            return;
        }

        String eventId = event.getEventId();
        long battleId = event.getResult() != null ? event.getResult().getBattleId() : 0;

        log.info("收到战斗结算消息: eventId={}, battleId={}", eventId, battleId);

        try {
            battleSettlementHandler.handleBattleEnd(event);
        } catch (Exception e) {
            log.error("战斗结算消息处理失败: eventId={}, battleId={}", eventId, battleId, e);
            throw e;
        }
    }
}
