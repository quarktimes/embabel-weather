# Embabel Weather — 智能天气预报系统

## 产品设计与架构设计文档

> **版本**: v1.0 | **日期**: 2026-06-17
> **技术栈**: Java 21 + Maven 3.9 + Spring Boot 3.5 + Embabel 0.3.5 + DeepSeek + OpenWeather API

---

## 目录

1. [产品概述](#1-产品概述)
2. [产品设计](#2-产品设计)
3. [系统架构](#3-系统架构)
4. [技术栈详解](#4-技术栈详解)
5. [模块设计](#5-模块设计)
6. [Embabel Agent 设计](#6-embabel-agent-设计)
7. [API 设计](#7-api-设计)
8. [数据模型](#8-数据模型)
9. [部署架构](#9-部署架构)
10. [开发计划](#10-开发计划)

---

## 1. 产品概述

### 1.1 产品愿景

**Embabel Weather** 是一款基于 AI Agent 的智能天气预报 Web 应用。用户可以用自然语言查询天气，系统通过 Embabel Agent 框架理解用户意图，调用 OpenWeather API 获取数据，并经 DeepSeek LLM 生成人性化的天气分析与建议。

### 1.2 核心价值

| 维度 | 描述 |
|------|------|
| 🎯 **自然语言交互** | 用户无需学习特定格式，直接说"北京明天会下雨吗？" |
| 🤖 **AI 驱动** | DeepSeek 大模型理解上下文、生成个性化天气分析 |
| 🧩 **Agent 编排** | Embabel 框架自动规划行动路径（查询→分析→呈现） |
| 🔌 **API 融合** | 基于 OpenWeather 免费 API，无需额外数据成本 |
| 📊 **结构化+自然语言** | 同时提供数据卡片和自然语言描述两种消费方式 |

### 1.3 目标用户

- **普通用户**：日常查询天气、出行决策参考
- **差旅人员**：查询目的地未来几天天气
- **开发者**：参考本项目的 Agent 架构搭建自己的 AI 应用

---

## 2. 产品设计

### 2.1 功能矩阵

| 功能 | 优先级 | 描述 | MVP |
|------|--------|------|-----|
| F1 自然语言天气查询 | P0 | 输入"上海天气怎么样？"获取实时天气 | ✅ |
| F2 当前天气展示 | P0 | 展示温度、湿度、风速、天气状况等 | ✅ |
| F3 5 天预报查询 | P0 | "北京这周末冷吗？"获取多日预报 | ✅ |
| F4 AI 天气分析 | P0 | DeepSeek 生成穿衣建议、出行提醒 | ✅ |
| F5 城市搜索/联想 | P1 | 输入城市名自动补全 | |
| F6 历史查询记录 | P1 | 查看过往查询历史 | |
| F7 收藏城市 | P2 | 将常用城市加入收藏夹方便快速查看 | |
| F8 天气预警 | P2 | 恶劣天气主动提醒 | |
| F9 多语言支持 | P3 | 中英文切换 | |
| F10 天气对比 | P3 | 同时查看多个城市天气 | |

### 2.2 用户旅程

```
用户输入查询
  │
  ▼
┌─────────────────────────────────────────────────────┐
│  ① 自然语言查询                                    │
│  "北京明天天气适合出门吗？"                          │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  ② Embabel Agent 理解意图                          │
│  ├─ 地点: 北京                                      │
│  ├─ 时间: 明天                                      │
│  └─ 需求: 出门适宜性评估                            │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  ③ 工具调用                                        │
│  ├─ geocoding(北京) →  lat=39.9, lon=116.4         │
│  └─ forecast(lat, lon) →  5天预报数据               │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  ④ DeepSeek 生成回答                               │
│  "明天北京晴转多云，15-25°C，适合出门。建议带件外套" │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  ⑤ 结果展示                                        │
│  ├─ 天气卡片 (温度/湿度/风速/天气图标)               │
│  ├─ AI 分析文本                                     │
│  └─ 5天预报列表                                     │
└─────────────────────────────────────────────────────┘
```

### 2.3 UI 页面设计 (MVP)

```
┌──────────────────────────────────────────────┐
│  🌤 Embabel Weather                          │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │ 🔍 输入城市或问天气...                │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  北京 · 2026-06-17 星期二                    │
│  ┌────────────────────┐                     │
│  │ ☀️  晴            │                     │
│  │  28°C / 22°C     │                     │
│  │  体感 26°C        │                     │
│  │  湿度 45% · 风 3级│                     │
│  └────────────────────┘                     │
│                                              │
│  🤖 AI 天气分析                              │
│  ┌────────────────────────────────────────┐  │
│  │ 北京今天天气晴好，适合外出活动。       │  │
│  │ 紫外线较强，建议涂抹防晒霜。          │  │
│  │ 明天预计有阵雨，出门记得带伞。        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  📅 5天预报                                  │
│  ┌────┬────┬────┬────┬────┐                 │
│  │ 三 │ 四 │ 五 │ 六 │ 日 │                 │
│  │ ☀️ │ 🌤 │ 🌧 │ ☁️ │ ☀️ │                 │
│  │30° │28° │25° │26° │29° │                 │
│  └────┴────┴────┴────┴────┘                 │
└──────────────────────────────────────────────┘
```

### 2.4 自然语言查询示例

| 用户输入 | Agent 规划路径 | 输出结果 |
|----------|---------------|----------|
| "北京天气" | geocoding("北京") → current("北京坐标") | 实时天气卡片 + AI 摘要 |
| "上海明天会下雨吗？" | geocoding("上海") → forecast → LLM 分析 | 降水概率分析 + 出行建议 |
| "深圳这周末温度多少？" | geocoding → forecast → 提取周末数据 | 周六日温度区间 + 趋势图 |
| "成都空气质量如何？" | geocoding → air_pollution → LLM 总结 | AQI 指数 + 健康建议 |

---

## 3. 系统架构

### 3.1 分层架构总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                            │
│   ┌────────────┐  ┌────────────┐  ┌──────────────────────┐         │
│   │ Thymeleaf  │  │  REST API  │  │  WebSocket (future)  │         │
│   │  (HTML)    │  │  (JSON)    │  │  (实时推送)          │         │
│   └─────┬──────┘  └─────┬──────┘  └──────────┬───────────┘         │
└─────────┼───────────────┼────────────────────┼──────────────────────┘
          │               │                    │
┌─────────┼───────────────┼────────────────────┼──────────────────────┐
│         ▼               ▼                    ▼                       │
│                        APPLICATION LAYER                            │
│   ┌─────────────────────────────────────────────────────────┐       │
│   │              WeatherController (@Controller)             │       │
│   │        /weather/query  /weather/current  /weather/forecast     │
│   └────────────────────────┬────────────────────────────────┘       │
│                            │                                        │
│   ┌────────────────────────▼────────────────────────────────┐       │
│   │              WeatherService (@Service)                   │       │
│   │        查询路由、数据聚合、缓存管理                         │       │
│   └──────┬────────────────────────────────┬─────────────────┘       │
│          │                                │                          │
│   ┌──────▼──────────────┐     ┌───────────▼───────────────┐         │
│   │  Traditional Query  │     │    Embabel Agent Flow     │         │
│   │  (直接调用OpenWeather) │     │   (自然语言 → 行动规划)   │         │
│   └─────────────────────┘     └───────────────────────────┘         │
└──────────────────────────────────────────────────────────────────────┘
                                      │
┌─────────────────────────────────────┼────────────────────────────────┐
│         AGENT & TOOL LAYER         │                                │
│                                     ▼                                │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │                  WeatherAgent (@Agent)                       │  │
│   │                                                              │  │
│   │  ┌─────────────────┐  ┌────────────────┐  ┌──────────────┐  │  │
│   │  │ @Action          │  │ @Action         │  │ @Action      │  │  │
│   │  │ getCurrentByCity │  │ getForecast     │  │ analyzeWeather│  │  │
│   │  └────────┬────────┘  └───────┬─────────┘  └──────┬───────┘  │  │
│   └───────────┼───────────────────┼───────────────────┼───────────┘  │
│               │                   │                   │              │
└───────────────┼───────────────────┼───────────────────┼──────────────┘
                │                   │                   │
┌───────────────┼───────────────────┼───────────────────┼──────────────┐
│   INTEGRATION │ LAYER            │                   │              │
│   ┌───────────▼─────┐   ┌───────▼────────┐   ┌──────▼──────────┐   │
│   │ OpenWeatherClient│   │  GeocodingClient│   │ DeepSeek LLM    │   │
│   │ (REST Client)   │   │  (REST Client)  │   │ (Spring AI)     │   │
│   └────────┬────────┘   └───────┬─────────┘   └────────┬────────┘   │
└────────────┼────────────────────┼──────────────────────┼─────────────┘
             │                    │                      │
    ┌────────▼────────────────────▼──────────────────────▼────────┐
    │                     EXTERNAL SERVICES                        │
    │   ┌────────────┐  ┌────────────┐  ┌──────────────────┐     │
    │   │ OpenWeather │  │ OpenWeather│  │ DeepSeek API     │     │
    │   │ /weather   │  │ /forecast  │  │ /chat/completions│     │
    │   │ /geo       │  │            │  │ (OpenAI兼容)     │     │
    │   └────────────┘  └────────────┘  └──────────────────┘     │
    └──────────────────────────────────────────────────────────────┘
```

### 3.2 请求处理流程（详细）

```
┌─────────────────────────────────────────────────────────────────────────┐
│  REQUEST FLOW — 自然语言天气查询                                         │
│                                                                         │
│  User ──→ /weather/query?q="北京明天天气"                                │
│            │                                                            │
│  [1]       ▼                                                            │
│       WeatherController                                                 │
│       调用 WeatherService.queryWeather("北京明天天气")                    │
│            │                                                            │
│  [2]       ▼                                                            │
│       WeatherService                                                    │
│       识别到自然语言输入 → 调用 Embabel Agent                            │
│            │                                                            │
│  [3]       ▼                                                            │
│       Embabel Agent Platform                                            │
│       ├── 创建 Goal: "回答用户关于北京明天天气的问题"                      │
│       ├── Agent 解析意图                                                │
│       │   ├── locationDetermined = "北京"                               │
│       │   └── timeDetermined = "明天"                                   │
│       ├── 规划 Actions:                                                 │
│       │   1. geocoding("北京") → 坐标                                   │
│       │   2. forecast(lat,lon) → 预报数据                               │
│       │   3. analyze(数据) → 自然语言回答                                │
│       └── GOAP 执行引擎按序执行 Actions                                  │
│            │                                                            │
│  [4]       ▼                                                            │
│       Action: geocoding("北京")                                         │
│       ├── @Action getCoordinates(cityName)                              │
│       ├── 调用 OpenWeatherClient.geocoding()                            │
│       ├── 返回 GeoLocation(lat=39.9, lon=116.4)                        │
│       └── 结果存入 Context                                              │
│            │                                                            │
│  [5]       ▼                                                            │
│       Action: getForecast(lat, lon)                                     │
│       ├── @Action getWeatherForecast(geo, days)                         │
│       ├── 调用 OpenWeatherClient.forecast()                             │
│       ├── 返回 ForecastResponse(5天逐3小时数据)                          │
│       └── 结果存入 Context                                              │
│            │                                                            │
│  [6]       ▼                                                            │
│       Action: analyzeWeather(context)                                   │
│       ├── @AchievesGoal @Action generateWeatherReport                   │
│       ├── 调用 DeepSeek LLM                                             │
│       │   prompt = "基于以下天气数据，回答用户关于北京明天天气的问题..."     │
│       ├── LLM 返回自然语言回答                                            │
│       └── 最终结果存入 Context                                           │
│            │                                                            │
│  [7]       ▼                                                            │
│       WeatherService 组装 Response                                       │
│       ├── structured: 天气数据 (温度/湿度/风速/天气状况/预报列表)         │
│       └── aiAnalysis: LLM 生成的自然语言分析                             │
│            │                                                            │
│  [8]       ▼                                                            │
│       WeatherController → Thymeleaf 渲染 → HTML 返回用户                 │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 技术栈详解

### 4.1 技术选型

| 层次 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **语言** | Java | 21 | LTS，虚拟线程、Pattern Matching、Record |
| **构建** | Maven | 3.9+ | 依赖管理、多模块构建 |
| **Web 框架** | Spring Boot | 3.5.x | 自动配置、Starter 生态 |
| **AI Agent** | Embabel | 0.3.5 | GOAP 规划引擎、@Agent/@Action |
| **AI 模型** | DeepSeek | V4 | 通过 Spring AI + OpenAI 兼容接口集成 |
| **天气数据** | OpenWeather | Free Tier | 实时天气 + 5天预报 + 地理编码 |
| **模板引擎** | Thymeleaf | 6.x | 服务器端 HTML 渲染 |
| **HTTP 客户端** | RestClient / WebClient | - | Spring 内置声明式 HTTP |
| **缓存** | Caffeine | 3.x | 本地缓存，减少 API 调用 |
| **测试** | JUnit 5 + Mockito | - | 单元测试 + 集成测试 |
| **API 文档** | SpringDoc OpenAPI | 2.x | Swagger UI 接口文档 |

### 4.2 Maven POM 核心依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.9</version>
        <relativePath/>
    </parent>

    <groupId>com.embabel.weather</groupId>
    <artifactId>embabel-weather</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Embabel Weather</name>
    <description>AI-powered weather forecast with Embabel Agent framework</description>

    <properties>
        <java.version>21</java.version>
        <embabel.version>0.3.5</embabel.version>
        <spring-ai.version>1.0.0-M6</spring-ai.version>
    </properties>

    <repositories>
        <!-- Embabel 官方仓库 -->
        <repository>
            <id>embabel-releases</id>
            <url>https://repo.embabel.com/artifactory/libs-release</url>
        </repository>
        <!-- Spring AI 里程碑仓库 -->
        <repository>
            <id>spring-milestones</id>
            <url>https://repo.spring.io/milestone</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Thymeleaf 模板引擎 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>

        <!-- Embabel Agent 核心 -->
        <dependency>
            <groupId>com.embabel.agent</groupId>
            <artifactId>embabel-agent-starter</artifactId>
            <version>${embabel.version}</version>
        </dependency>

        <!-- Embabel OpenAI 集成（用于 DeepSeek） -->
        <dependency>
            <groupId>com.embabel.agent</groupId>
            <artifactId>embabel-agent-openai</artifactId>
            <version>${embabel.version}</version>
        </dependency>

        <!-- Spring AI OpenAI（DeepSeek 兼容） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
            <version>${spring-ai.version}</version>
        </dependency>

        <!-- 缓存 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>

        <!-- API 文档 -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.6</version>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.embabel.agent</groupId>
            <artifactId>embabel-agent-starter-test</artifactId>
            <version>${embabel.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 4.3 application.yml 配置

```yaml
server:
  port: 8080

spring:
  application:
    name: embabel-weather
  thymeleaf:
    cache: false
    prefix: classpath:/templates/
    suffix: .html
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500, expireAfterWrite=10m

# DeepSeek via Spring AI OpenAI-compatible API
spring:
  ai:
    openai:
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-v4-flash
          temperature: 0.7
          max-tokens: 2000

# Embabel Agent 配置
embabel:
  agent:
    platform:
      mode: web                          # web / console / microservice
      auto-start: true
    openai:
      enabled: true                      # 启用 OpenAI 兼容客户端
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      default-model: deepseek-v4-flash
    # 日志和追踪
    observability:
      enabled: true

# OpenWeather API
weather:
  openweather:
    api-key: ${OPENWEATHER_API_KEY}
    base-url: https://api.openweathermap.org/data/2.5
    geo-url: https://api.openweathermap.org/geo/1.0
    units: metric                        # metric / imperial
    lang: zh_cn                          # 中文返回
```

---

## 5. 模块设计

### 5.1 项目目录结构

```
embabel-weather/
├── pom.xml                                    # 父 POM
├── README.md
├── DESIGN.md                                  # 本文档
│
├── embabel-weather-core/                      # 核心业务逻辑模块
│   └── src/main/java/com/embabel/weather/core/
│       ├── config/
│       │   ├── WeatherProperties.java         # OpenWeather 配置属性
│       │   └── CacheConfig.java               # 缓存配置
│       ├── client/
│       │   ├── OpenWeatherClient.java         # OpenWeather REST 客户端
│       │   └── GeocodingClient.java           # 地理编码客户端
│       ├── model/
│       │   ├── WeatherData.java               # 当前天气数据
│       │   ├── ForecastData.java              # 预报数据
│       │   ├── GeoLocation.java               # 地理位置
│       │   └── WeatherQueryResult.java        # 查询结果聚合
│       └── service/
│           └── WeatherService.java            # 天气查询服务
│
├── embabel-weather-agent/                     # Embabel Agent 模块
│   └── src/main/java/com/embabel/weather/agent/
│       ├── WeatherAgent.java                  # @Agent 主类
│       ├── action/
│       │   ├── GetCoordinatesAction.java      # @Action 获取坐标
│       │   ├── GetCurrentWeatherAction.java   # @Action 获取实时天气
│       │   ├── GetForecastAction.java         # @Action 获取预报
│       │   └── AnalyzeWeatherAction.java      # @Action 分析天气
│       ├── tool/
│       │   ├── WeatherTools.java              # 工具注册
│       │   └── WeatherDataFormatter.java      # 数据格式化
│       └── context/
│           └── WeatherContext.java            # Agent 上下文数据
│
├── embabel-weather-web/                       # Web 展示模块
│   └── src/main/java/com/embabel/weather/web/
│       ├── EmbabelWeatherApplication.java     # 启动类
│       ├── controller/
│       │   ├── HomeController.java            # 首页路由
│       │   ├── WeatherController.java         # 天气查询 API
│       │   └── AgentController.java           # Agent 状态管理
│       ├── dto/
│       │   ├── QueryRequest.java
│       │   └── WeatherResponse.java
│       └── exception/
│           └── GlobalExceptionHandler.java
│
│   └── src/main/resources/
│       ├── application.yml                    # 主配置文件
│       ├── templates/
│       │   ├── index.html                     # 首页
│       │   ├── fragments/
│       │   │   ├── weather-card.html          # 天气卡片片段
│       │   │   ├── forecast-card.html         # 预报卡片片段
│       │   │   └── ai-analysis.html           # AI 分析片段
│       │   └── error.html                     # 错误页面
│       └── static/
│           ├── css/
│           │   └── style.css
│           └── js/
│               └── app.js
│
└── embabel-weather-api/                       # REST API 模块
    └── src/main/java/com/embabel/weather/api/
        ├── controller/
        │   ├── WeatherApiController.java       # 公开 REST API
        │   └── AgentApiController.java         # Agent 管理 API
        └── dto/
            ├── ApiResponse.java
            └── ErrorResponse.java
```

### 5.2 模块依赖关系

```
embabel-weather (父 POM)
    │
    ├── embabel-weather-core    ← 无框架依赖，纯业务逻辑
    │       ↑
    ├── embabel-weather-agent   ← 依赖 core，引入 Embabel + Spring AI
    │       ↑
    ├── embabel-weather-web     ← 依赖 agent，引入 Thymeleaf
    │       ↑
    └── embabel-weather-api     ← 依赖 agent，对外提供 REST
```

---

## 6. Embabel Agent 设计

### 6.1 Agent 架构

```
┌─────────────────────────────────────────────────────────────┐
│                   WeatherAgent (@Agent)                     │
│                                                             │
│  name: "weatherAgent"                                       │
│  description: "智能天气预报助手，用自然语言回答天气问题"      │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Goal: 为用户提供准确的天气信息和出行建议              │  │
│  │                                                       │  │
│  │  Plans:                                               │  │
│  │  ┌─────────────────────────────────────────────┐      │  │
│  │  │ Plan 1: 实时天气查询                          │      │  │
│  │  │ ① getCoordinates(city)                       │      │  │
│  │  │ ② getCurrentWeather(coords)                  │      │  │
│  │  │ ③ generateReport(data)                       │      │  │
│  │  └─────────────────────────────────────────────┘      │  │
│  │  ┌─────────────────────────────────────────────┐      │  │
│  │  │ Plan 2: 预报查询                             │      │  │
│  │  │ ① getCoordinates(city)                       │      │  │
│  │  │ ② getForecast(coords, days)                  │      │  │
│  │  │ ③ analyzeWeather(data, question)             │      │  │
│  │  └─────────────────────────────────────────────┘      │  │
│  │  ┌─────────────────────────────────────────────┐      │  │
│  │  │ Plan 3: 空气质量查询                         │      │  │
│  │  │ ① getCoordinates(city)                       │      │  │
│  │  │ ② getAirPollution(coords)                    │      │  │
│  │  │ ③ generateHealthAdvice(data)                 │      │  │
│  │  └─────────────────────────────────────────────┘      │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  Tools:   GetCoordinates   GetWeather   GetForecast         │
│           AnalyzeWeather   FormatReport                     │
│                                                             │
│  LLM:     DeepSeek V4 Flash (默认) / DeepSeek V4 Pro       │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 Agent 核心代码设计

```java
// WeatherAgent.java — 主 Agent 类
package com.embabel.weather.agent;

import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.spi.Ai;
import com.embabel.agent.api.spi.OperationContext;
import com.embabel.weather.agent.action.*;
import com.embabel.weather.agent.context.WeatherContext;
import org.springframework.beans.factory.annotation.Autowired;

@Agent(
    name = "weatherAgent",
    description = "智能天气预报助手，使用自然语言回答天气相关问题"
)
public class WeatherAgent {

    @Autowired
    private GetCoordinatesAction getCoordinatesAction;

    @Autowired
    private GetCurrentWeatherAction getCurrentWeatherAction;

    @Autowired
    private GetForecastAction getForecastAction;

    @Autowired
    private AnalyzeWeatherAction analyzeWeatherAction;

    @Action
    @AchievesGoal("回答天气查询问题")
    public WeatherContext answerWeatherQuery(OperationContext ctx, String userQuery) {
        // 1. Agent 自动规划 → GOAP 引擎根据 Goal 选择最优路径
        // 2. 按序执行 Actions
        // 3. 返回结构化结果
        return ctx.getResult(WeatherContext.class);
    }

    @Action(
        name = "getCoordinates",
        description = "将城市名称转换为经纬度坐标"
    )
    public GeoLocation getCoordinates(OperationContext ctx, String cityName) {
        return getCoordinatesAction.execute(cityName);
    }

    @Action(
        name = "getCurrentWeather",
        description = "根据经纬度获取当前实时天气",
        dependsOn = {"getCoordinates"}
    )
    public WeatherData getCurrentWeather(OperationContext ctx, GeoLocation geo) {
        return getCurrentWeatherAction.execute(geo);
    }

    @Action(
        name = "getForecast",
        description = "根据经纬度获取未来天气预报（支持1-5天）",
        dependsOn = {"getCoordinates"}
    )
    public ForecastData getForecast(OperationContext ctx, GeoLocation geo, int days) {
        return getForecastAction.execute(geo, days);
    }

    @Action(
        name = "generateWeatherReport",
        description = "基于天气数据生成自然语言分析报告",
        dependsOn = {"getCurrentWeather", "getForecast"}
    )
    public String generateWeatherReport(OperationContext ctx, UserQuery query,
                                         WeatherData current, ForecastData forecast) {
        return analyzeWeatherAction.execute(query, current, forecast);
    }
}
```

### 6.3 Action 实现示例

```java
// GetCoordinatesAction.java — 地理编码 Action
package com.embabel.weather.agent.action;

import com.embabel.agent.api.annotation.Action;
import com.embabel.weather.core.client.GeocodingClient;
import com.embabel.weather.core.model.GeoLocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetCoordinatesAction {

    @Autowired
    private GeocodingClient geocodingClient;

    public GeoLocation execute(String cityName) {
        // 调用 OpenWeather Geocoding API
        return geocodingClient.geocode(cityName);
    }
}
```

```java
// AnalyzeWeatherAction.java — AI 分析 Action
package com.embabel.weather.agent.action;

import com.embabel.agent.api.spi.Ai;
import com.embabel.agent.api.spi.PromptRunner;
import com.embabel.weather.core.model.ForecastData;
import com.embabel.weather.core.model.UserQuery;
import com.embabel.weather.core.model.WeatherData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AnalyzeWeatherAction {

    @Autowired
    private PromptRunner promptRunner;

    public String execute(UserQuery query, WeatherData current, ForecastData forecast) {

        String prompt = """
            你是一个专业的天气助手。基于以下天气数据，用中文回答用户的问题。
            回答应包含：当前天气概况、温度体感、出行建议。

            用户问题：{{question}}

            当前天气：
            - 城市：{{city}}
            - 天气：{{weather}}
            - 温度：{{temp}}°C（体感 {{feelsLike}}°C）
            - 湿度：{{humidity}}%
            - 风速：{{windSpeed}} m/s

            未来天气预报：
            {{forecastSummary}}

            请给出简洁、有用的回答。
            """;

        return promptRunner.usingLlm()
            .withPromptTemplate(prompt)
            .withVariable("question", query.text())
            .withVariable("city", current.cityName())
            .withVariable("weather", current.weatherDescription())
            .withVariable("temp", current.temperature())
            .withVariable("feelsLike", current.feelsLike())
            .withVariable("humidity", current.humidity())
            .withVariable("windSpeed", current.windSpeed())
            .withVariable("forecastSummary", forecast.summary())
            .generateText();
    }
}
```

### 6.4 GOAP 执行流程

```
用户输入: "明天深圳适合去海边吗？"

  Goal: 评估明天深圳海边的适宜性
        │
        ▼
  ┌─────────────────────────────────────────────┐
  │  GOAP Planner                               │
  │  ├── World State: {                         │
  │  │    city: unknown,                        │
  │  │    coordinates: unknown,                 │
  │  │    weatherData: unknown,                 │
  │  │    forecastData: unknown,                │
  │  │    analysisGenerated: false              │
  │  │  }                                       │
  │  ├── Goal State: {                          │
  │  │    analysisGenerated: true               │
  │  │  }                                       │
  │  └── 最优计划:                               │
  │  Step 1: getCoordinates("深圳")              │
  │    ├─ 前提: city known                      │
  │    └─ 效果: coordinates known               │
  │  Step 2: getForecast(coords, 1)             │
  │    ├─ 前提: coordinates known               │
  │    └─ 效果: forecastData known              │
  │  Step 3: getCurrentWeather(coords)          │
  │    ├─ 前提: coordinates known               │
  │    └─ 效果: weatherData known               │
  │  Step 4: generateBeachReport(...)           │
  │    ├─ 前提: weatherData + forecastData      │
  │    └─ 效果: analysisGenerated = true        │
  └─────────────────────────────────────────────┘
        │
        ▼
    执行引擎依次调用 Actions（如果需要外部API则等待）
        │
        ▼
  结果: "明天深圳晴天，26-30°C，东南风3级，非常适合去海边。
        紫外线较强，建议涂抹防晒霜、避开中午时段。"
```

### 6.5 Agent 事件与条件（0.3.5 新特性）

利用 Embabel 0.3.5 的事件触发器和条件评估：

```java
// 使用条件评估 — 在特定天气条件下触发不同行为
@Action(description = "检查是否有恶劣天气预警")
public WeatherAlert checkSevereWeather(ForecastData forecast) {
    if (forecast.hasExtremeCondition()) {
        return WeatherAlert.severeWarning(forecast.extremeType());
    }
    return WeatherAlert.none();
}

// 使用 ToolLoop 回调 — 缓存已查询的地点
@Action(description = "带缓存的坐标查询")
public GeoLocation getCoordinatesWithCache(OperationContext ctx, String cityName) {
    // 0.3.5 ToolLoop 回调支持
    return ctx.cacheable("geo:" + cityName, () ->
        geocodingClient.geocode(cityName));
}
```

---

## 7. API 设计

### 7.1 Web 页面路由

| 路径 | 方法 | 描述 | 视图 |
|------|------|------|------|
| `/` | GET | 首页/查询页面 | `index.html` |
| `/weather/query` | GET | 天气查询（自然语言） | `index.html` + fragment |

### 7.2 REST API

| 路径 | 方法 | 参数 | 描述 |
|------|------|------|------|
| `/api/v1/weather/current` | GET | `city` | 获取当前天气 |
| `/api/v1/weather/forecast` | GET | `city`, `days` | 获取天气预报 |
| `/api/v1/weather/query` | POST | `{query: string}` | 自然语言查询（通过 Agent） |
| `/api/v1/weather/query-stream` | SSE | `query` | 流式自然语言查询 |
| `/api/v1/geo/search` | GET | `q`, `limit` | 城市搜索 |
| `/api/v1/agent/status` | GET | - | Agent 运行状态 |

### 7.3 API 响应规范

```json
// POST /api/v1/weather/query
// Request: { "query": "北京明天天气适合出门吗？" }

// Response:
{
  "success": true,
  "timestamp": "2026-06-17T14:30:00+08:00",
  "data": {
    "city": {
      "name": "北京",
      "country": "CN",
      "lat": 39.9042,
      "lon": 116.4074
    },
    "current": {
      "temperature": 28.5,
      "feelsLike": 26.3,
      "humidity": 45,
      "windSpeed": 3.2,
      "weather": "晴",
      "weatherCode": 800,
      "icon": "01d",
      "visibility": 10000
    },
    "forecast": [
      {
        "date": "2026-06-18",
        "tempMax": 30.2,
        "tempMin": 22.1,
        "weather": "晴转多云",
        "weatherCode": 802,
        "humidity": 50,
        "windSpeed": 4.1,
        "precipitation": 0
      }
      // ... 更多天数
    ],
    "aiAnalysis": "北京明天（6月18日）晴转多云，气温22-30°C，适合出门。\n\n🌤 天气总体不错，早晚温差较大，建议携带外套。紫外线强度中等，外出可适当防晒。\n\n出行建议：\n• 适合户外活动\n• 建议携带防晒用品\n• 早晚温差大，注意增减衣物"
  }
}
```

### 7.4 OpenWeather API 映射

| 业务操作 | OpenWeather 端点 | 请求示例 |
|----------|-----------------|----------|
| 地理编码 | `/geo/1.0/direct` | `?q=Beijing&limit=1&appid=KEY` |
| 当前天气 | `/data/2.5/weather` | `?lat=39.9&lon=116.4&units=metric&appid=KEY` |
| 5天预报 | `/data/2.5/forecast` | `?lat=39.9&lon=116.4&units=metric&cnt=40&appid=KEY` |

---

## 8. 数据模型

### 8.1 核心模型（Record — Java 21）

```java
// 地理编码响应
public record GeoLocation(
    String name,
    String localName,
    double lat,
    double lon,
    String country,
    String state
) {}

// 当前天气数据
public record WeatherData(
    String cityName,
    String country,
    double temperature,
    double feelsLike,
    int humidity,
    double windSpeed,
    String weatherDescription,
    int weatherCode,
    String icon,
    int visibility,
    double pressure,
    long timestamp
) {}

// 单日预报
public record DailyForecast(
    String date,
    double tempMax,
    double tempMin,
    String weather,
    int weatherCode,
    int humidity,
    double windSpeed,
    double precipitation
) {}

// 预报数据集合
public record ForecastData(
    String cityName,
    String country,
    List<DailyForecast> dailyForecasts,
    String summary  // LLM 生成的摘要
) {}

// 用户查询
public record UserQuery(
    String text,
    String city,
    String intent  // current / forecast / comparison / airquality
) {}

// 聚合查询结果
public record WeatherQueryResult(
    GeoLocation location,
    WeatherData current,
    ForecastData forecast,
    String aiAnalysis,
    long processingTimeMs
) {}

// Agent 上下文（GOAP 工作内存）
public class WeatherContext {
    private String originalQuery;
    private String cityName;
    private GeoLocation coordinates;
    private WeatherData currentWeather;
    private ForecastData forecastData;
    private String aiReport;
    // getters & setters
}
```

### 8.2 OpenWeather API 响应映射

OpenWeather 返回的 JSON 格式如下，需要映射到上述 Record：

```json
// GET /data/2.5/weather?q=Beijing&units=metric
{
  "coord": { "lon": 116.3972, "lat": 39.9075 },
  "weather": [{ "id": 800, "main": "Clear", "description": "晴", "icon": "01d" }],
  "main": {
    "temp": 28.5, "feels_like": 26.3,
    "temp_min": 22.1, "temp_max": 30.2,
    "pressure": 1013, "humidity": 45
  },
  "visibility": 10000,
  "wind": { "speed": 3.2, "deg": 180 },
  "name": "Beijing"
}
```

---

## 9. 部署架构

### 9.1 开发环境

```
┌─────────────────────────────┐
│  Developer Machine          │
│                             │
│  IntelliJ IDEA / VS Code    │
│  ├─ Java 21 (JDK)          │
│  ├─ Maven 3.9+             │
│  └─ Git                    │
│                             │
│  mvn spring-boot:run        │
│       │                    │
│       ▼                    │
│  http://localhost:8080      │
└─────────────────────────────┘
```

### 9.2 生产环境推荐架构

```
┌──────────────────────────────────────────────────┐
│                   用户浏览器                        │
└─────────────────────┬────────────────────────────┘
                      │ HTTPS
                      ▼
┌──────────────────────────────────────────────────┐
│           反向代理 (Nginx/Cloudflare)              │
│  ├─ SSL 终止                                       │
│  ├─ 静态资源缓存                                    │
│  └─ 请求转发 → upstream embabel-weather:8080       │
└─────────────────────┬────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────┐
│              Spring Boot Application               │
│  ┌─────────┐ ┌──────────┐ ┌──────────────────┐   │
│  │ Web     │ │ Agent    │ │ Service          │   │
│  │ Layer  │ │ Layer    │ │ Layer            │   │
│  └─────────┘ └──────────┘ └──────────────────┘   │
│                                                   │
│  ┌─────────────────────────────────────────────┐  │
│  │ Caffeine Cache (内存缓存 OpenWeather 响应)   │  │
│  └─────────────────────────────────────────────┘  │
└────────┬──────────────────────────┬───────────────┘
         │                          │
         ▼                          ▼
┌──────────────────┐   ┌──────────────────────┐
│  OpenWeather API  │   │  DeepSeek API        │
│  api.openweather │   │  api.deepseek.com     │
│  .org            │   │  (或第三方代理)        │
└──────────────────┘   └──────────────────────┘
```

### 9.3 Docker 部署

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
RUN mvn dependency:go-offline

COPY src src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/embabel-weather-web/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 10. 开发计划

### 10.1 阶段划分

| 阶段 | 名称 | 周期 | 产出 |
|------|------|------|------|
| **P1** | 基础设施搭建 | 0.5 天 | Maven 多模块项目、配置、空启动 |
| **P2** | Core 模块 + OpenWeather 集成 | 1 天 | 天气数据客户端、Model、Service |
| **P3** | Embabel Agent 实现 | 1.5 天 | Agent/Action/Tool 定义、GOAP 流程 |
| **P4** | Web 页面 | 1 天 | Thymeleaf 模板、样式、JavaScript |
| **P5** | DeepSeek 集成 | 0.5 天 | LLM prompt 优化、分析生成 |
| **P6** | 测试 & 优化 | 1 天 | 单元测试、缓存、异常处理 |
| **P7** | REST API + 文档 | 0.5 天 | OpenAPI 文档、API 端点 |
| **合计** | | **6 天** | 完整 MVP |

### 10.2 关键里程碑

```
Day 1  ─── P1+P2: 项目初始化、OpenWeather 数据打通
               ┌── 能通过 API 获取北京天气数据
               └── 测试通过

Day 2-3 ── P3: Embabel Agent 核心能力
               ├── Agent 识别自然语言意图
               ├── GOAP 自动规划执行路径
               └── Action 链正确执行

Day 4   ── P4: Web 界面可用
               ├── 首页搜索框
               ├── 天气卡片展示
               └── AI 分析展示区域

Day 5   ── P5+P6: DeepSeek 集成 + 测试
               ├── AI 分析结果准确、自然
               ├── 缓存生效、响应<500ms
               └── 单元测试覆盖率 > 80%

Day 6   ── P7: 收尾
               ├── REST API 完成
               ├── Swagger 文档可用
               └── 部署文档
```

### 10.3 质量目标

| 指标 | 目标 |
|------|------|
| 页面加载时间 | < 2s |
| 天气查询响应 (缓存命中) | < 200ms |
| 天气查询响应 (缓存未命中) | < 1.5s |
| Agent 查询响应 (含 LLM) | < 5s |
| 单元测试覆盖 | > 80% |
| API 可用性 | 99.9% |

---

## 附录

### A. 参考资料

| 资源 | 链接 |
|------|------|
| Embabel 官方文档 | https://docs.embabel.com/embabel-agent/guide/0.3.5-SNAPSHOT/ |
| Embabel GitHub | https://github.com/embabel/embabel-agent |
| Embabel Java 模板 | https://github.com/embabel/java-agent-template |
| Embabel 示例项目 | https://github.com/embabel/embabel-agent-examples |
| Spring Boot 3.5 文档 | https://docs.spring.io/spring-boot/ |
| Spring AI 文档 | https://docs.spring.io/spring-ai/ |
| DeepSeek API | https://api-docs.deepseek.com/ |
| OpenWeather API | https://openweathermap.org/api |
| Baeldung Embabel 教程 | https://www.baeldung.com/java-embabel-agent-framework |

### B. 环境变量

| 变量名 | 说明 | 获取方式 |
|--------|------|---------|
| `OPENWEATHER_API_KEY` | OpenWeather API 密钥 | https://openweathermap.org/api (免费注册) |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | https://platform.deepseek.com (注册获取) |

### C. 启动命令

```bash
# 开发模式
export OPENWEATHER_API_KEY=your_key
export DEEPSEEK_API_KEY=your_key
mvn spring-boot:run -pl embabel-weather-web

# 构建 JAR
mvn clean package -DskipTests
java -jar embabel-weather-web/target/embabel-weather-web-1.0.0-SNAPSHOT.jar
```
