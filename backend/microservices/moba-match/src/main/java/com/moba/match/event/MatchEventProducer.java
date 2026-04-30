package com.moba.match.event;

import com.moba.common.event.EventTopics;
import com.moba.common.event.MatchSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(RocketMQTemplate.class)
public class MatchEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public MatchEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void publishMatchSuccess(MatchSuccessEvent event) {
        try {
            rocketMQTemplate.syncSend(
                    EventTopics.MATCH_SUCCESS,
                    MessageBuilder.withPayload(event).build()
            );
            log.info("Match success event published: matchId={}, players={}", event.getMatchId(), event.getPlayerIds().size());
        } catch (Exception e) {
            log.error("Failed to publish match success event: matchId={}", event.getMatchId(), e);
        }
    }
}
