# MOBA Game - Backend Microservices

## 项目结构

```
moba-microservices/
├── pom.xml                  # 父 POM（统一版本管理）
├── moba-common/             # 公共模块（DTO/Model/接口/工具类）
├── moba-gateway/            # API 网关（Spring Cloud Gateway）
├── moba-business/           # 业务服务（用户/玩家/背包/排行）
├── moba-match/              # 匹配服务（3v3v3/5v5 匹配）
├── moba-battle/             # 战斗服务（纯 Netty + Spring DI）
└── moba-data/               # 数据服务（战斗日志/回放/统计）
```

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 21 | LTS |
| Spring Boot | 3.2.0 | gateway/business/match/data 使用 |
| Spring Cloud | 2023.0.0 | 微服务治理 |
| Spring Cloud Alibaba | 2023.0.1.0 | Nacos/Sentinel |
| Dubbo | 3.2.4 | RPC 框架 |
| RocketMQ Client | 5.2.0 | 消息队列 |
| Netty | 4.1.100.Final | battle 服务网络层 |
| Protobuf | 3.25.1 | 协议序列化 |
| JJWT | 0.12.3 | JWT 令牌 |
| MySQL | 8.2.0 | 关系型数据库 |
| Lombok | 1.18.30 | 代码简化 |

## 依赖版本管理规范

### 核心原则

**所有第三方依赖的版本号统一在父 POM（`moba-microservices/pom.xml`）中管理，子模块不得硬编码任何版本号。**

### 管理方式

1. **`<properties>` 定义版本变量**

   父 POM 的 `<properties>` 中集中定义所有版本号：

   ```xml
   <properties>
       <spring-boot.version>3.2.0</spring-boot.version>
       <dubbo.version>3.2.4</dubbo.version>
       <jjwt.version>0.12.3</jjwt.version>
       <!-- ... -->
   </properties>
   ```

2. **`<dependencyManagement>` 锁定版本**

   父 POM 的 `<dependencyManagement>` 中声明所有依赖及其版本：

   ```xml
   <dependencyManagement>
       <dependencies>
           <dependency>
               <groupId>io.jsonwebtoken</groupId>
               <artifactId>jjwt-api</artifactId>
               <version>${jjwt.version}</version>
           </dependency>
           <!-- ... -->
       </dependencies>
   </dependencyManagement>
   ```

3. **`<pluginManagement>` 锁定插件版本**

   父 POM 的 `<pluginManagement>` 中声明所有构建插件版本：

   ```xml
   <pluginManagement>
       <plugins>
           <plugin>
               <groupId>org.apache.maven.plugins</groupId>
               <artifactId>maven-jar-plugin</artifactId>
               <version>${maven-jar-plugin.version}</version>
           </plugin>
           <!-- ... -->
       </plugins>
   </pluginManagement>
   ```

4. **子模块只声明 groupId + artifactId**

   子模块的 `<dependencies>` 中不得出现 `<version>` 标签：

   ```xml
   <!-- 正确 ✅ -->
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-api</artifactId>
   </dependency>

   <!-- 错误 ❌ -->
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-api</artifactId>
       <version>0.12.3</version>
   </dependency>
   ```

### 版本号命名规范

| 类型 | 格式 | 示例 |
|------|------|------|
| Spring 生态 | `<组件名>.version` | `spring-boot.version` |
| 中间件 | `<组件名>.version` | `dubbo.version`, `netty.version` |
| 工具库 | `<组件名>.version` | `jjwt.version`, `protobuf.version` |
| Maven 插件 | `<插件名>.version` | `maven-jar-plugin.version` |

### 新增依赖流程

1. 在父 POM `<properties>` 中添加版本变量
2. 在父 POM `<dependencyManagement>` 中添加依赖声明（引用版本变量）
3. 在子模块中只声明 `groupId` + `artifactId`（不写版本号）

### 版本升级流程

1. 仅修改父 POM `<properties>` 中的版本变量值
2. 所有子模块自动生效，无需逐个修改

## 各服务说明

### moba-common（公共模块）

纯 POJO/DTO/接口/工具类模块，**不依赖任何框架**（无 Spring、无 Dubbo、无 RocketMQ）。

仅依赖：Lombok、JJWT

### moba-battle（战斗服务）

**纯 Netty 服务器**，不使用 Spring Boot。使用 Spring Framework 的 `AnnotationConfigApplicationContext` 进行依赖注入。

- 网络层：Netty（WebSocket + TCP 双端口）
- 依赖注入：Spring Framework（`@Component`、`@Configuration`、`@Bean`）
- RPC：Dubbo（纯 API 方式注册服务）
- 消息队列：RocketMQ Client（纯 API 方式收发消息）
- 配置加载：SnakeYAML 手动解析 application.yml

### 其他服务（gateway/business/match/data）

标准 Spring Boot 微服务，使用 Spring Boot 自动配置。

## 构建与运行

```bash
# 构建全部
cd moba-microservices
mvn clean package -DskipTests

# 构建 battle 服务（纯 Netty）
java -jar moba-battle/target/moba-battle.jar

# 构建其他 Spring Boot 服务
java -jar moba-business/target/moba-business.jar
java -jar moba-match/target/moba-match.jar
java -jar moba-data/target/moba-data.jar
java -jar moba-gateway/target/moba-gateway.jar
```
