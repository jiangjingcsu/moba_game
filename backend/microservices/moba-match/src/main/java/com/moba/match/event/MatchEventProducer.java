package com.moba.match.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.common.event.EventTopics;
import com.moba.common.event.MatchSuccessEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MatchEventProducer {

    private final ObjectMapper objectMapper;

    @Value("${rocketmq.name-server:}")
    private String nameServer;

    @Value("${rocketmq.producer-group}")
    private String producerGroup;

    @Value("${rocketmq.send-timeout-ms}")
    private int sendTimeoutMs;

    private DefaultMQProducer producer;

    public MatchEventProducer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (nameServer == null || nameServer.isEmpty()) {
            log.warn("RocketMQ未配置nameServer, MatchEventProducer将不可用");
            return;
        }
        try {
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(nameServer);
            producer.setSendMsgTimeout(sendTimeoutMs);
            producer.start();
            log.info("RocketMQ生产者已启动: group={}, nameServer={}", producerGroup, nameServer);
        } catch (Exception e) {
            log.error("RocketMQ生产者启动失败", e);
            producer = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
            log.info("RocketMQ生产者已关闭");
        }
    }

    public void publishMatchSuccess(MatchSuccessEvent event) {
        if (producer == null) {
            log.warn("RocketMQ生产者不可用, 跳过事件发布, matchId={}", event.getMatchId());
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            Message msg = new Message(EventTopics.MATCH_SUCCESS, json.getBytes(StandardCharsets.UTF_8));
            SendResult result = producer.send(msg);
            log.info("匹配成功事件已发布: matchId={}, 玩家数={}, 发送状态={}",
                    event.getMatchId(), event.getUserIds().size(), result.getSendStatus());
        } catch (Exception e) {
            log.error("发布匹配成功事件失败: matchId={}", event.getMatchId(), e);
        }
    }
}
