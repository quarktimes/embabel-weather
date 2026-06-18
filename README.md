# 🌤 Embabel Weather

AI 智能天气预报 Web 应用。输入自然语言查天气，自动识别城市、分析趋势，下雨提醒带伞。

## 技术栈

| 组件 | 用途 |
|------|------|
| **Java 21 + Spring Boot 3.5.9** | Web 框架 |
| **Embabel 0.3.5** | AI Agent 编排 |
| **DeepSeek V4** | 自然语言分析与实体提取 |
| **OpenWeather API** | 天气数据源 |
| **Thymeleaf** | 服务端 HTML 渲染 |
| **Caffeine** | 本地缓存 |

## 快速开始

### 1. 获取 API Key

- [OpenWeather](https://openweathermap.org/api) — 免费注册获取 API Key
- [DeepSeek](https://platform.deepseek.com) — 注册获取 API Key

### 2. 启动

```bash
export DEEPSEEK_API_KEY=sk-xxx
export OPENWEATHER_API_KEY=xxx
mvn spring-boot:run
```

打开浏览器访问 [http://localhost:8080](http://localhost:8080)

## 功能

- **自然语言输入** — 输入 "明天上海会下雨吗？"，Agent 自动提取城市名
- **3 天天气预报** — 最高/低温、天气状况、图标
- **AI 天气分析** — DeepSeek 生成个性化出行建议
- **下雨提醒** 🌂 — 规则检测 + AI 增强双重保障
- **异常降级** — LLM 异常时静默降级，数据卡片照常展示

## 项目结构

```
src/main/java/com/embabel/weather/
├── config/          # 配置类（Caffeine 缓存、OpenWeather 配置）
├── model/           # 领域 Record
├── client/          # OpenWeather API 客户端
│   └── dto/         # 外部 API 请求/响应 DTO
├── agent/           # WeatherAgent + 执行逻辑
├── service/         # 业务编排 + 缓存 + 降级
└── controller/      # Web 路由
```
