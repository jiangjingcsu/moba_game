package com.moba.battle.protocol.handler;

import com.moba.battle.protocol.core.MessageType;
import com.moba.battle.protocol.handler.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class HandlerRegistrar {

    @PostConstruct
    public void registerAllHandlers() {
        MessageHandlerRegistry registry = MessageHandlerRegistry.getInstance();

        registry.register(MessageType.LOGIN_REQ, new LoginHandler());
        registry.register(MessageType.BATTLE_ENTER_REQ, new BattleEnterHandler());
        registry.register(MessageType.BATTLE_READY_REQ, new BattleReadyHandler());
        registry.register(MessageType.BATTLE_ACTION_REQ, new BattleActionHandler());
        registry.register(MessageType.BATTLE_SKILL_CAST_REQ, new SkillCastHandler());
        registry.register(MessageType.RECONNECT_REQ, new ReconnectHandler());

        log.info("All message handlers registered");
    }
}
