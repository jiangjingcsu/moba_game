package com.moba.battle.event;

import com.moba.common.event.EventTopics;
import com.moba.common.event.MatchSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = EventTopics.MATCH_SUCCESS,
        consumerGroup = "moba-battle-consumer"
)
public class MatchSuccessConsumer implements RocketMQListener<MatchSuccessEvent> {

    @Override
    public void onMessage(MatchSuccessEvent event) {
        log.info("Received match success event: matchId={}, players={}", event.getMatchId(), event.getPlayerIds().size());
        log.info("Battle already created via Dubbo RPC, skipping duplicate creation for matchId={}", event.getMatchId());
    }
}
