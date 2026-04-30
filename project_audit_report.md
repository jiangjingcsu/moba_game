# MOBA 游戏项目 前后端全面审计报告

> 生成时间：2026-04-27  
> 项目路径：`d:\workspace\moba_game`  
> 分析范围：全部前端代码 + 全部后端微服务代码

---

## 目录

1. [架构层面问题](#1-架构层面问题)
2. [后端严重问题](#2-后端严重问题)
3. [后端中等问题](#3-后端中等问题)
4. [前端严重问题](#4-前端严重问题)
5. [前端中等问题](#5-前端中等问题)
6. [前后端对接问题](#6-前后端对接问题)
7. [安全性问题](#7-安全性问题)
8. [总结与优先级建议](#8-总结与优先级建议)

---

## 1. 架构层面问题

### 1.1 moba-battle 双网络通道冲突 🔴

**问题描述**：  
[BattleApplication.java](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/BattleApplication.java) 启动了 Spring Boot HTTP 服务器（端口 8083），同时 [NettyServer.java](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/network/NettyServer.java) 启动了 Netty TCP/WebSocket 服务器（端口 8888/9999）。两套传输层并存，架构混乱：

- Netty :8888 被客户端直连，绕过 Gateway 的认证/限流/路由
- Spring Boot :8083 上没有任何 @RestController，形同虚设
- 客户端硬编码连接地址，破坏服务发现机制

**修复方案**：
```
方案A（推荐）：将 moba-battle 改造为纯 Netty 服务
  - 移除 Spring Boot 依赖（保留必要的 nacos-client、rocketmq-client）
  - Netty :9999 端口增加 HTTP Handler 用于服务间管理 API
  - 通过 Nacos SDK 直接注册服务

方案B（保守）：保持 Spring Boot 但明确职责
  - Spring Boot :8083 只做管理 API（创建战斗、查询结果）
  - Netty :8888 只做实时战斗通信
  - 客户端登录/匹配走 Gateway HTTP → moba-battle :8083
```

### 1.2 BattleApplication 从未启动 NettyServer 🔴

**问题描述**：  
[BattleApplication.java:12-14](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/BattleApplication.java#L12-L14) 的 main() 方法只启动了 Spring Boot 应用，没有启动 NettyServer。没有 @PostConstruct 或 CommandLineRunner 来调用 `NettyServer.start()`。这意味着 **Netty TCP/WebSocket 服务器在当前代码中根本不会运行**。

**修复方案**：
在启动类或配置类中添加：
```java
@Bean
CommandLineRunner startNettyServer(NettyServer nettyServer) {
    return args -> {
        nettyServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(nettyServer::stop));
    };
}
```

### 1.3 Manager 类双重单例问题 🟡

**问题描述**：  
多个核心 Manager 类同时使用了两种获取实例的方式，存在实例不一致风险：

| 类 | 方式1 | 方式2 | 实际使用 |
|----|-------|-------|---------|
| [MessageDispatchService](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/service/MessageDispatchService.java) | `@Component` Spring Bean | `SpringContextHolder.getBean()` | 两者混用 |
| [PlayerManager](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/PlayerManager.java) | `@Component` Spring Bean | `SpringContextHolder.getBean()` | 两者混用 |
| [MatchManager](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/MatchManager.java) | `@Component` Spring Bean | `SpringContextHolder.getBean()` | 两者混用 |
| [BattleManager](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/BattleManager.java) | `@Component` Spring Bean | `SpringContextHolder.getBean()` | 两者混用 |

同时 [BattleBeanConfig](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/config/BattleBeanConfig.java) 注册了 AntiCheatValidator、ServerMonitor 等的 @Bean，但代码中用 `getInstance()` 获取，@Bean 注册形同虚设。

**修复方案**：
统一使用 Spring 依赖注入模式，移除所有手动单例的 `getInstance()` 方法：
```java
// 不要再这样
MatchManager mgr = MatchManager.getInstance();
// 改为
@Autowired
private MatchManager matchManager;
```

---

## 2. 后端严重问题

### 2.1 MatchController 硬编码匹配服务地址 🔴

**问题描述**：  
[MatchController.java:22](file:///d:/workspace/moba_game/backend/microservices/moba-business/src/main/java/com/moba/business/controller/MatchController.java#L22) 硬编码了匹配服务地址：
```java
private static final String MATCH_SERVICE_URL = "http://localhost:8082/internal/match";
```
这导致部署到多台机器时无法正确路由；云环境/容器环境下 localhost 指向自身。

**修复方案**：
```java
// 方案1：使用 Nacos 服务发现
@Value("${match.service.url}")  // 或通过 Nacos 服务发现
private String matchServiceUrl;

// 方案2：使用 Dubbo RPC 调用（已有 @DubboReference 可用）
@DubboReference
private MatchService matchService;
```

### 2.2 MatchmakingService.startBattle() 未发布匹配成功事件 🔴

**问题描述**：  
[MatchmakingService.java:119-146](file:///d:/workspace/moba_game/backend/microservices/moba-match/service/MatchmakingService.java#L119-L146) 的 `startBattle()` 方法直接通过 Dubbo 调用创建战斗，但从未调用 `MatchEventProducer.publishMatchSuccess()` 发布 RocketMQ 事件。然而 moba-battle 中存在 [MatchSuccessConsumer](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/event/MatchSuccessConsumer.java) 等待消费该事件 — 这条消息路径是死代码。

**修复方案**：
在 `startBattle()` 中匹配成功后，如果不是直接调用 Dubbo，则发布 MQ 事件让 battle 服务消费；如果是直接调用 Dubbo，则删除无用的 MatchSuccessConsumer。

### 2.3 LockstepEngine 输入处理存在数据竞态 🔴

**问题描述**：  
[LockstepEngine.java](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/battle/LockstepEngine.java) 中 `pendingInputs` 是 `ConcurrentLinkedQueue`，但 `frameInputs` 是 `ConcurrentHashMap`。这两个集合分别被 Netty IO 线程和 RoomManager 的 tick 线程访问，存在竞态条件：

- `submitInput()` (L183) 由 Netty IO 线程调用
- `collectInputs()` (L150) + `pollInputForPlayer()` (L171) 由 tick 线程调用
- tick 线程遍历 `session.getBattlePlayers()` 同时 Netty 线程可能修改 session 数据

**修复方案**：
使用读写锁保护 session 的玩家数据，或将所有状态变更提交到 tick 线程执行：
```java
// 使用队列将外部变更提交到 tick 线程
BlockingQueue<Runnable> commandQueue = new LinkedBlockingQueue<>();
// tick 循环开头执行所有排队的命令
```

### 2.4 RoomManager tick 循环并发修改房间集合 🔴

**问题描述**：  
[RoomManager.java:42-61](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/RoomManager.java#L42-L61) 的 tick 循环中使用 `new ArrayList<>(rooms.values())` 复制快照然后遍历，这避免了 ConcurrentModificationException。但是被复制的 ConcurrentHashMap 在遍历期间可能被其他线程（如 removeRoom、createRoom）并发修改，导致某些房间的 tick 被跳过或重复。

**修复方案**：
```java
// 使用 CopyOnWriteArrayList 或加锁复制
private final ReadWriteLock roomsLock = new ReentrantReadWriteLock();
roomsLock.readLock().lock();
try {
    new ArrayList<>(rooms.values());
} finally {
    roomsLock.readLock().unlock();
}
```

### 2.5 BattleManager.handlePlayerAction() 解析格式与前端不匹配 🔴

**问题描述**：  
[BattleManager.java:148-195](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/BattleManager.java#L148-L195) 的 `handlePlayerAction()` 期望 body 格式为 `playerId|actionType|...`，但前端 [codec.ts](file:///d:/workspace/moba_game/frontend/moba-client/src/network/codec.ts#L37) 的 `PLAYER_ACTION_REQUEST` 序列化格式为：
```
heroId|type|targetX|targetY|targetId|skillId|itemId|frame
```
- 服务器 parser：`parts[0]` = playerId，`parts[1]` = actionType
- 前端发：`parts[0]` = heroId（字符串），`parts[1]` = type（"move"）

heroId 是字符串，但服务器尝试 `Long.parseLong(parts[0])` 会抛出 NumberFormatException。

**修复方案**：
统一协议格式，确保前后端一致。要么前端发 playerId 而非 heroId，要么服务器适配前端的格式。

### 2.6 BattleManager.handleSkillCast() 参数解析与前端不匹配 🔴

**问题描述**：  
[BattleManager.java:197-222](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/BattleManager.java#L197-L222) 解析 `parts[0]=playerId, parts[1]=skillId, parts[2]=targetId`，但前端 [codec.ts](file:///d:/workspace/moba_game/frontend/moba-client/src/network/codec.ts#L40) 发送的格式为：
```
heroId|skillId|targetX|targetY|frame
```
- `parts[0]` = heroId（字符串），`parts[2]` = targetX（浮点数）
- 服务器尝试 `Long.parseLong(parts[2])` 解析浮点数会抛异常

**修复方案**：
```java
// 服务器端适配
long playerId = Long.parseLong(parts[0]);
int skillId = Integer.parseInt(parts[1]);
float targetX = Float.parseFloat(parts[2]);
float targetY = Float.parseFloat(parts[3]);
int frame = Integer.parseInt(parts[4]);
```

---

## 3. 后端中等问题

### 3.1 NettyServer - HeartbeatHandler 存在内存泄漏风险 🟡

**问题描述**：  
[HeartbeatHandler.java:38-47](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/network/handler/HeartbeatHandler.java#L38-L47) 的 `scheduleHeartbeat()` 方法递归调度自身（每30秒），形成无限的定时任务链。如果 Channel 突然关闭但 `ctx.channel().isActive()` 返回 true（少数竞争情况），任务会继续调度但 writeAndFlush 失败，日志堆积。

**修复方案**：
```java
// 使用 ScheduledFuture 来管理任务，channelInactive 时 cancel
private final Map<ChannelId, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();

@Override
public void channelInactive(ChannelHandlerContext ctx) {
    ScheduledFuture<?> future = heartbeats.remove(ctx.channel().id());
    if (future != null) future.cancel(false);
    ctx.fireChannelInactive();
}
```

### 3.2 @EnableScheduling 未使用 🟡

**问题描述**：  
[BattleApplication.java:10](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/BattleApplication.java#L10) 标注了 `@EnableScheduling`，但整个项目中没有任何 `@Scheduled` 方法。所有定时任务通过 `ScheduledExecutorService` 手动实现。这是无意义的开销。

**修复方案**：
移除 `@EnableScheduling` 注解，或将 `ScheduledExecutorService` 替换为 `@Scheduled`。

### 2.3 ServerConfig 配置未绑定到 application.yml 🟡

**问题描述**：  
[ServerConfig.java](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/config/ServerConfig.java) 标注了 `@ConfigurationProperties(prefix = "battle")`，但 [application.yml](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/resources/application.yml) 中没有对应的 battle 配置项（之前的 `battle.tick-interval-ms` 等已被移除）。同时 `HeartbeatHandler` 中直接调用 `ServerConfig.defaultConfig()` 硬编码默认值，绕过了配置绑定。

**修复方案**：
在 application.yml 中加入配置项并用 `@Value` 或 `@ConfigurationProperties` 读取。

### 2.4 MatchManager.currentBattleId 类型不一致 🟡

**问题描述**：  
[Player.java:20](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/model/Player.java#L20) 中 `currentBattleId` 是 `long` 类型，但在 [MatchManager.java:139](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/MatchManager.java#L139) 和 [BattleManager.java:81](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/manager/BattleManager.java#L81) 中：
```java
p.setCurrentBattleId(Long.parseLong(battleId.replace("BATTLE_", "")));
```
当 battleId = "BATTLE_1745xxxxxx" 时，`replace("BATTLE_", "")` 得到时间戳毫秒值（如 "1745337600000"），可以解析为 long。但如果后续 battleId 格式变更（如 "BATTLE_a1b2c3d4"），会抛出 NumberFormatException。

**修复方案**：
将 `currentBattleId` 改为 `String` 类型，存储完整的 battleId。

### 2.5 DistributedLock - 注册为 Bean 但无人使用 🟡

**问题描述**：  
[DistributedLock.java](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/util/DistributedLock.java) 用 `@Component` 注册为 Spring Bean，但在整个代码库中没有任何地方注入或使用它。

**修复方案**：
要么在需要分布式锁的场景中使用（如创建战斗房间防止重复），要么移除这个类。

### 2.6 匹配队列 Memory Leak 风险 🟡

**问题描述**：  
[MatchmakingService.java](file:///d:/workspace/moba_game/backend/microservices/moba-match/src/main/java/com/moba/match/service/MatchmakingService.java) 的 `joinQueue()` 为每个玩家创建一个新的 `MatchInfo` 并加入队列。如果玩家匹配成功后 `startBattle()` 调用 battle 服务的 Dubbo RPC 失败（超时/异常），匹配被从队列移除但玩家状态未恢复，`playerPools` 中残留。

**修复方案**：
```java
private void startBattle(MatchInfo match) {
    try {
        CreateBattleResponse response = battleService.createBattle(request);
        if (!response.isSuccess()) {
            // 失败后恢复玩家状态
            for (Long playerId : match.getPlayerIds()) {
                playerPools.remove(playerId);
                // 通知玩家匹配失败
            }
        }
    } catch (Exception e) {
        // 同样恢复状态
        log.error("Battle creation failed", e);
    }
}
```

---

## 4. 前端严重问题

### 4.1 GameCanvas - 离线模式启用，未对接在线战斗 🔴

**问题描述**：  
[GameCanvas.vue:78](file:///d:/workspace/moba_game/frontend/moba-client/src/components/GameCanvas.vue#L78) 中 `lockstepManager.start(true)` 以离线模式启动，所有战斗逻辑在前端本地模拟。进入在线战斗无任何切换逻辑。

同时 [MatchScreen.vue](file:///d:/workspace/moba_game/frontend/moba-client/src/components/MatchScreen.vue) 的匹配逻辑完全是前端模拟（随机数填槽位、固定时间到），未调用后端匹配 API。

**修复方案**：
```typescript
// 改为在线模式，从后端获取状态
const isOnline = isConnected.value
lockstepManager.init(gameStore)
lockstepManager.start(!isOnline)  // 在线时传 false
```

### 4.2 LoginScreen 登录后未能连接战斗 WebSocket 🔴

**问题描述**：  
[LoginScreen.vue](file:///d:/workspace/moba_game/frontend/moba-client/src/components/LoginScreen.vue) 登录成功后只设置了 `gameStore.setPlayerInfo()` 和 `gameStore.setGameState('lobby')`，但未调用 `connectToBattleServer()` 建立 WebSocket 连接。战斗连接必须在匹配前建立。

**修复方案**：
```typescript
// 登录成功后
if (result.success) {
    gameStore.setPlayerInfo(result.playerInfo)
    await connectToBattleServer()  // 建立战斗连接
    gameStore.setGameState('lobby')
}
```

### 4.3 connectToBattleServer 的 Object.assign 破坏 GameWebSocket 实例 🔴

**问题描述**：  
[index.ts:170-180](file:///d:/workspace/moba_game/frontend/moba-client/src/network/index.ts#L170-L180) 中：
```typescript
const socket = new GameWebSocket(url)
Object.assign(ws, socket)  // 复制属性到已导出的 ws 实例
await ws.connect()
```
`Object.assign` 会覆盖 `ws` 原有的引用属性（`messageCallbacks` Map, `messageQueue` 数组等），可能导致尚未触发的回调丢失。同时这造成了两个 `GameWebSocket` 实例同时存在，一个被 `ws` 引用，另一个 `socket` 变量被 GC。

**修复方案**：
```typescript
// 不要用 Object.assign，直接替换模块级变量
// 在 network/index.ts 中将 ws 改为 let 并重新赋值
export let ws = new GameWebSocket(BATTLE_WS_URL)

export async function connectToBattleServer(): Promise<void> {
    ws = new GameWebSocket(`${BATTLE_WS_URL}?token=${authToken}`)
    await ws.connect()
    isConnected.value = true
    setupMessageHandlers()
}
```

### 4.4 LockstepEngine.ts 与服务器 LockstepEngine.java 行为不一致 🔴

**问题描述**：  
前端 [LockstepEngine.ts](file:///d:/workspace/moba_game/frontend/moba-client/src/engine/LockstepEngine.ts) 是简化版，与服务器 [LockstepEngine.java](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/battle/LockstepEngine.java) 在以下方面不同：

- 前端没有 INPUT_DELAY_FRAMES 概念
- 前端没有 frameHashes 哈希校验逻辑
- 前端没有 collisionSystem（技能碰撞检测）
- 前端没有 randomSeed 确定性随机

这意味着前后端即使同时运行同一场战斗，帧状态**必然不一致**。

**修复方案**：
短期内：将所有战斗权威逻辑放在服务器，前端只做渲染和输入发送。
长期：实现前后端引擎逻辑完全一致的确定性模拟。

---

## 5. 前端中等问题

### 5.1 PlayerInfo 类型定义不一致 🟡

**问题描述**：  
[index.ts:13-25](file:///d:/workspace/moba_game/frontend/moba-client/src/network/index.ts#L13-L25) 的 `PlayerInfo` 包含 `nickname, level, avatar, gold, diamond, isSignedIn, signInDays`，但 [game.ts](file:///d:/workspace/moba_game/frontend/moba-client/src/stores/game.ts) 中 `setPlayerInfo` 函数参数类型来自 stores/game.ts 内部的 `PlayerInfo` 接口（只有 `playerId, playerName, rank, rankScore`）。

两个 `PlayerInfo` 类型各自定义且不兼容。

**修复方案**：
统一到一个共享类型文件中：
```typescript
// types/player.ts
export interface PlayerInfo {
    playerId: number
    playerName: string
    nickname: string
    rank: number
    rankScore: number
    level: number
    avatar: string
    gold: number
    diamond: number
    isSignedIn: boolean
    signInDays: number
}
```

### 5.2 LockstepManager - move 类型输入未处理 🟡

**问题描述**：  
[LockstepManager.ts:91-112](file:///d:/workspace/moba_game/frontend/moba-client/src/engine/LockstepManager.ts#L91-L112) 的 `processPendingActions()` 中 switch 只处理了 `skill`、`attack`、`item`，但没有处理 `move` 类型。然而 `queueInput()` 的离线模式会把 `move` 入队到 `pendingInputs`。

**修复方案**：
在 switch 中添加 move 分支，或直接在 queueInput 时不将 move 入队（因为 movement 由 GameCanvas 的 updateMovement 处理）。

### 5.3 pendingInputs - frame 为 key 可能错过输入 🟡

**问题描述**：  
[LockstepManager.ts:78-80](file:///d:/workspace/moba_game/frontend/moba-client/src/engine/LockstepManager.ts#L78-L80) 以 `this.currentFrame` 为 key 存输入，但 `queueInput()` 可能在 tick 之间被多次调用（例如快速按两个技能），两次调用可能在不同的 frame，第二个输入会被存入下一 frame 但处理时已经错过。

**修复方案**：
```typescript
// 使用队列而非 Map<frame, inputs>
private pendingInputQueue: Array<{ heroId: string; type: string; ... }> = []

// 每帧处理所有排队的输入
while (this.pendingInputQueue.length > 0) {
    const input = this.pendingInputQueue.shift()!
    // process input
}
```

### 5.4 HeroSprite - 图片加载硬编码路径 🟡

**问题描述**：  
[GameCanvas.vue:103-109](file:///d:/workspace/moba_game/frontend/moba-client/src/components/GameCanvas.vue#L103-L109) 中英雄贴图路径硬编码：
```typescript
[HeroRole.WARRIOR]: '/assets/heroes/warrior.png',
[HeroRole.MAGE]: '/assets/heroes/mage.png',
...
```
如果构建后资源路径变化（vite 打包后 assets hash 化），这些路径将失效。

**修复方案**：
```typescript
// 使用动态 import
const texture = Texture.from(new URL(`/assets/heroes/${roleName}.png`, import.meta.url).href)
```

### 5.5 GameCanvas - gameLoop 使用 requestAnimationFrame 而非锁定帧率 🟡

**问题描述**：  
[GameCanvas.vue:379-397](file:///d:/workspace/moba_game/frontend/moba-client/src/components/GameCanvas.vue#L379-L397) 使用 `requestAnimationFrame` 驱动渲染，但服务器帧率是 15Hz（66ms/帧）。前端在 60Hz 屏幕上 tick 可能跑 4 倍于服务器的速度，导致状态不同步。

**修复方案**：
```typescript
// 使用 setInterval 或累积时间方式保证固定 tick 频率
// tickAccumulator 已存在但未被用于限制 tick 频率
```

### 5.6 多个组件 getCurrentPlayer() 未处理 null 🟡

**问题描述**：  
[GameLobby.vue](file:///d:/workspace/moba_game/frontend/moba-client/src/components/GameLobby.vue) 中 `playerInfo?.gold` 使用可选链，但有地方直接引用 `gameStore.gameModes` 等可能为 undefined 的值，TypeScript strict 模式下会报错。

**修复方案**：
启用 TypeScript strict mode 并修复所有潜在的 undefined 引用。

---

## 6. 前后端对接问题

### 6.1 连接地址三重不一致 🟡

当前存在三个不同的连接配置：

| 位置 | 地址 | 用途 |
|------|------|------|
| [index.ts:5](file:///d:/workspace/moba_game/frontend/moba-client/src/network/index.ts#L5) API_BASE | `http://localhost:8080/api` | HTTP API（经Gateway） |
| [index.ts:6](file:///d:/workspace/moba_game/frontend/moba-client/src/network/index.ts#L6) BATTLE_WS_URL | `ws://localhost:8888/ws/battle` | WebSocket 直连 Netty |
| [MatchController.java:22](file:///d:/workspace/moba_game/backend/microservices/moba-business/src/main/java/com/moba/business/controller/MatchController.java#L22) | `http://localhost:8082/internal/match` | moba-business → moba-match |
| [MatchController.java:92](file:///d:/workspace/moba_game/backend/microservices/moba-business/src/main/java/com/moba/business/controller/MatchController.java#L92) | `ws://localhost:8888/ws/battle` | 硬编码返回给前端 |

四个硬编码地址分散在不同位置，运维时需要同时修改多处。

**修复方案**：
使用环境变量 + Nacos 配置中心统一管理：
```typescript
// 前端 .env
VITE_API_BASE=http://gateway-host:8080/api
VITE_BATTLE_WS_URL=ws://battle-host:8888/ws/battle
```

### 6.2 匹配完成后缺少战斗服务器推送机制 🟡

**问题描述**：  
当前流程：
1. 前端 POST `/api/match/join` → moba-business → moba-match → 匹配中
2. moba-match 匹配成功后调用 Dubbo → moba-battle 创建战斗
3. 前端轮询 `GET /api/match/status`，返回 `battleServerUrl: "ws://localhost:8888/ws/battle"`

问题：battleServerUrl 硬编码在 [MatchController.java:92](file:///d:/workspace/moba_game/backend/microservices/moba-business/src/main/java/com/moba/business/controller/MatchController.java#L92)，无法支持多台 battle 服务器动态负载均衡。

**修复方案**：
匹配成功后通过 Nacos 服务发现获取实际的 battle 服务器地址：
```java
// 查询 Nacos 中注册的 battle 服务器
List<Instance> instances = namingService.selectInstances("moba-battle-netty", true);
Instance best = selectByLowestLoad(instances);
return Map.of("battleServerUrl", "ws://" + best.getIp() + ":" + best.getPort() + "/ws/battle");
```

---

## 7. 安全性问题

### 7.1 JWT Secret 硬编码 🔴

**问题描述**：  
[AuthController.java:23](file:///d:/workspace/moba_game/backend/microservices/moba-business/src/main/java/com/moba/business/controller/AuthController.java#L23) 和 [JwtService.java](file:///d:/workspace/moba_game/backend/microservices/moba-gateway/src/main/java/com/moba/gateway/service/JwtService.java) 中 JWT Secret 使用默认值 `moba-game-jwt-secret-key-2024-must-be-at-least-256-bits`，且存在于代码中。如果未通过外部配置覆盖，攻击者可以伪造 Token。

**修复方案**：
```yaml
# 从环境变量或密钥管理服务读取
jwt:
  secret: ${JWT_SECRET:}  # 不提供默认值，强制部署时配置
```

### 7.2 WebSocket 连接 token 验证不完整 🟡

**问题描述**：  
[BattleWebSocketHandler.java](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/network/websocket/BattleWebSocketHandler.java) 通过 URL query 参数 `?playerId=xxx` 识别玩家，没有任何 token 验证。前端 [index.ts:170-180](file:///d:/workspace/moba_game/frontend/moba-client/src/network/index.ts#L170-L180) 的 `connectToBattleServer()` 在 URL 中带了 token，但服务器端 [ClientRequestHandler](file:///d:/workspace/moba_game/backend/microservices/moba-battle/src/main/java/com/moba/battle/network/handler/ClientRequestHandler.java) 引用了 `JwtAuthHandler.ATTR_PLAYER_ID` 表明应该已经有 JWT 验证机制。两者不一致。

**修复方案**：
确保 Netty Pipeline 中的 `JwtAuthHandler` 正常工作，验证 token 的有效性，验证失败拒绝连接。

### 7.3 密码编码器每次创建新实例 🟡

**问题描述**：  
[AuthController.java:29](file:///d:/workspace/moba_game/backend/microservices/moba-business/src/main/java/com/moba/business/controller/AuthController.java#L29) 中：
```java
private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
```
每次创建 AuthController 实例时创建一个新的 BCryptPasswordEncoder。BCryptPasswordEncoder 创建开销较大（生成随机 salt），应该作为共享 Bean。

**修复方案**：
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

### 7.4 Gateway 白名单前缀匹配过于宽泛 🟡

**问题描述**：  
[AuthFilter.java:37-39](file:///d:/workspace/moba_game/backend/microservices/moba-gateway/src/main/java/com/moba/gateway/filter/AuthFilter.java#L37-L39) 前缀白名单：
```java
private static final List<String> PREFIX_WHITE_LIST = List.of("/ws/battle");
```
任何以 `/ws/battle` 开头的路径都不需要认证，包括潜在恶意的 `/ws/battle/../../../admin`（虽然 Gateway 通常会对 URL 做规范化，但依赖于具体实现）。

**修复方案**：
限制前缀白名单只匹配明确的路径格式：
```java
private boolean isWhiteListed(String path) {
    if (EXACT_WHITE_LIST.contains(path)) return true;
    // 只允许 /ws/battle 及其下一级路径
    return path.equals("/ws/battle") || path.startsWith("/ws/battle/");
}
```

---

## 8. 总结与优先级建议

### 问题统计

| 分类 | 🔴 严重 | 🟡 中等 | 🟠 轻微 |
|------|---------|---------|---------|
| 架构 | 2 | 1 | 0 |
| 后端 | 6 | 7 | 0 |
| 前端 | 4 | 6 | 0 |
| 前后端对接 | 0 | 2 | 0 |
| 安全 | 1 | 3 | 0 |
| **合计** | **13** | **19** | **0** |

### 修复优先级建议

**P0 - 立即修复（阻塞系统运行）：**
1. BattleApplication 未启动 NettyServer → 游戏根本不可用
2. BattleManager 输入解析格式与前端不匹配 → 战斗操作完全无法处理
3. MatchController 硬编码 URL → 多机部署不可用
4. LoginScreen 登录后未连接 WebSocket → 前端状态机断裂

**P1 - 近期修复（影响核心功能）：**
5. LockstepEngine 前后端行为不一致 → 在线战斗不同步
6. 双网络通道架构清理 → 架构债务
7. matchmaking startBattle 未发布事件 → 事件驱动链路断裂
8. Object.assign 破坏 GameWebSocket → 潜在连接问题

**P2 - 计划修复（改善健壮性）：**
9. 连接地址多处硬编码
10. JWT Secret 硬编码
11. Manager 类双重单例
12. 线程安全问题

**P3 - 技术债务清理：**
13. @EnableScheduling 未使用
14. DistributedLock 死代码
15. 类型定义不一致

---

> **文档结束** — 共发现 32 个问题，其中 13 个严重级别，19 个中等级别。
