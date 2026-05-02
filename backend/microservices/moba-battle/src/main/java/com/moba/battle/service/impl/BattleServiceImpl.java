package com.moba.battle.service.impl;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.BattleRoom;
import com.moba.common.dto.BattleResultDTO;
import com.moba.common.dto.CreateBattleRequest;
import com.moba.common.dto.CreateBattleResponse;
import com.moba.common.service.BattleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class BattleServiceImpl implements BattleService {

    private ServiceConfig<BattleService> serviceConfig;

    public void startDubboService(ServerConfig serverConfig) {
        try {
            ApplicationConfig applicationConfig = new ApplicationConfig();
            applicationConfig.setName(serverConfig.getDubboServiceName());
            applicationConfig.setMetadataType("local");
            applicationConfig.setQosEnable(false);

            RegistryConfig registryConfig = new RegistryConfig();
            registryConfig.setAddress("nacos://" + serverConfig.getNacosServerAddr());
            registryConfig.setGroup(serverConfig.getNacosGroup());
            registryConfig.setParameters(java.util.Map.of(
                    "namespace", serverConfig.getNacosNamespace(),
                    "username", serverConfig.getNacosUsername(),
                    "password", serverConfig.getNacosPassword()
            ));
            registryConfig.setTimeout(serverConfig.getDubboRegistryTimeout());
            registryConfig.setRegisterMode("instance");

            ProtocolConfig protocolConfig = new ProtocolConfig();
            protocolConfig.setName("dubbo");
            protocolConfig.setPort(serverConfig.getDubboPort());
            protocolConfig.setSerialization("hessian2");

            serviceConfig = new ServiceConfig<>();
            serviceConfig.setApplication(applicationConfig);
            serviceConfig.setRegistry(registryConfig);
            serviceConfig.setProtocol(protocolConfig);
            serviceConfig.setInterface(BattleService.class);
            serviceConfig.setRef(this);
            serviceConfig.setParameters(Map.of("serialization", "hessian2"));

            serviceConfig.export();
            log.info("Dubbo服务已导出, 端口={}, 注册模式=instance", serverConfig.getDubboPort());
        } catch (Exception e) {
            log.warn("Dubbo服务导出失败, 服务将继续运行但无法通过RPC调用: {}", e.getMessage());
            serviceConfig = null;
        }
    }

    public void stopDubboService() {
        if (serviceConfig != null) {
            serviceConfig.unexport();
            log.info("Dubbo服务已取消导出");
        }
    }

    @Override
    public CreateBattleResponse createBattle(CreateBattleRequest request) {
        try {
            String battleId = request.getBattleId();
            if (battleId == null || battleId.isEmpty()) {
                battleId = "BATTLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            }

            BattleRoom room = BattleManager.getInstance().createBattle(
                    battleId,
                    request.getPlayerIds(),
                    request.getTeamCount(),
                    request.getNeededBots(),
                    request.getAiLevel()
            );

            if (room != null) {
                log.info("通过Dubbo创建战斗: {}, 玩家: {}", battleId, request.getPlayerIds().size());
                return CreateBattleResponse.ok(battleId);
            } else {
                return CreateBattleResponse.fail("创建战斗房间失败");
            }
        } catch (Exception e) {
            log.error("创建战斗异常", e);
            return CreateBattleResponse.fail(e.getMessage());
        }
    }

    @Override
    public BattleResultDTO getBattleResult(String battleId) {
        BattleRoom room = BattleManager.getInstance().getBattleRoom(battleId);
        if (room == null || room.getSession() == null) {
            return null;
        }

        BattleResultDTO result = new BattleResultDTO();
        result.setBattleId(battleId);
        result.setGameMode(room.getGameMode() != null ? room.getGameMode().ordinal() : 0);
        result.setStartTime(room.getSession().getStartTime());
        result.setEndTime(room.getSession().getEndTime());
        result.setDuration(room.getSession().getEndTime() - room.getSession().getStartTime());
        result.setWinnerTeamId(-1);
        return result;
    }

    @Override
    public boolean isBattleRunning(String battleId) {
        BattleRoom room = BattleManager.getInstance().getBattleRoom(battleId);
        return room != null && room.isRunning();
    }

    @Override
    public int getRoomCount() {
        return BattleManager.getInstance().getRoomCount();
    }

    @Override
    public int getTotalPlayers() {
        return BattleManager.getInstance().getTotalPlayers();
    }
}
