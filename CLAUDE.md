# Embabel Weather — CLAUDE.md

## 项目概述
Java 21 + Spring Boot 3.5 + Embabel 0.3.5 的 AI 天气预报 Web 应用。

## 技术栈
- Java 21 (Record、Pattern Matching)
- Maven 3.9+ (单模块构建)
- Spring Boot 3.5.9 (Web + Thymeleaf + Cache)
- Embabel 0.3.5 (@Agent / @Action / GOAP)
- DeepSeek V4 (通过 OpenAI 兼容接口)
- OpenWeather Free API (天气数据源)
- Caffeine Cache (本地缓存)

## MVP 范围
- 用户输入自然语言问天气（如"明天上海会下雨吗？"）
- Agent 自动提取城市名 → 天气查询 → 3天预报展示
- 下雨自动提醒带伞 ☂️（规则检测 + AI 增强双重保障）
- 单页面应用（Thymeleaf 服务端渲染）

## 项目结构
单模块 Maven 项目，包路径：com.embabel.weather
- config/ — 配置类
- model/ — 领域模型 Record
- client/ — 外部 API 调用
  - dto/ — 外部 API 请求/响应 DTO
- agent/ — GOAP Agent（4 个独立 @Action，通过参数/返回类型自动串联）
- service/ — 通过 AgentPlatform 调用 Agent + 降级兜底
- controller/ — Web 控制器

## DTO 规范
- 调用外部 API 的请求/响应 DTO 放在 `client/dto/` 包下
- 外部 DTO 使用 `XxxDto` 或外层类 + 内部 Record 组织
- 领域 DTO（Service ↔ Controller 之间）放在 `model/` 包下
- 所有 DTO 使用 Java 21 Record 类型

## 数据模型规范
- 不可变 DTO 使用 Java 21 Record 类型
- 需要可变 getter/setter 的配置类使用 Lombok `@Data`
- Lombok 只在配置/工具类中使用，不用于业务模型

## 命名规范
- Agent Action 方法: 动词开头 (extractCity, geocode, forecast, reply)
- Controller 方法: 按功能命名 (queryWeather, showHome)
- Service 方法: queryWeather, fallback

## 错误码设计

```java
public enum ErrorCode {
    SUCCESS(0, "成功"),
    EMPTY_QUERY(1001, "请输入城市或天气问题"),
    CITY_NOT_FOUND(1002, "未找到该城市，请检查输入"),
    WEATHER_API_ERROR(2001, "天气服务暂时不可用"),
    LLM_API_ERROR(2002, "AI 分析服务暂时不可用"),
    AGENT_ERROR(3001, "智能助手处理异常"),
    UNKNOWN_ERROR(9999, "系统异常，请稍后重试");
}
```

## 降级策略
- LLM_API_ERROR / AGENT_ERROR → **静默降级**：跳过 AI 分析，只展示数据卡片
- 其他错误 → 向用户展示错误信息

## 关键架构决策

### 为什么用 embabel-agent-starter-deepseek 而不是 embabel-agent-starter
- `embabel-agent-starter` 是通用启动器，不注册具体 LLM 模型 → AgentPlatform 初始化报 `Default LLM not found`
- `embabel-agent-starter-deepseek` 自动注册 `deepseek-chat` 和 `deepseek-reasoner` 到 ModelProvider
- 避免手动配置 Spring AI 版本（Embabel 0.3.5 内嵌 Spring AI 1.1.4，外部添加其他版本会冲突）

### 参数校验策略
- 参数格式校验（空值、长度、格式）→ Controller 层
- 业务约束校验（状态、余额、时间逻辑）→ Service 层

### GOAP 自主规划（不是 Workflow）
- **旧（Workflow）**: Agent 内有 `execute()` 方法硬编码步骤顺序
- **新（GOAP）**: 4 个独立 `@Action`，无编排方法
- 引擎根据 Action 的**参数类型**和**返回类型**自动串联：
  ```
  UserQuery → extractCity → ParsedQuery → geocode → GeoLocation
    → forecast → List<DayForecast> → reply → WeatherQueryResult
  ```
- Service 通过 `AgentPlatform` 执行 agent，不直接调用 agent 方法
- 每步执行完引擎会重新规划下一步（不一次定型）
- 参考教程: https://github.com/zhangjessey/embabel-java-agent-tutorial

## 构建与运行
```bash
# 开发运行
export OPENWEATHER_API_KEY=xxx
export DEEPSEEK_API_KEY=xxx
mvn spring-boot:run

# 打包
mvn clean package -DskipTests
```

## 环境变量
- OPENWEATHER_API_KEY — OpenWeather API 密钥
- DEEPSEEK_API_KEY — DeepSeek API 密钥
