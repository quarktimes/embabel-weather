# 我写了一个"假的"AI Agent

> **项目地址**: [github.com/quarktimes/embabel-weather](https://github.com/quarktimes/embabel-weather)

事情要从一个 bug 说起。

我用 Java + Embabel + DeepSeek 做了一个天气预报功能。用户输入"明天上海会下雨吗？"，系统提取城市名、查天气、生成分析、返回结果。

功能跑通了。但我盯着代码，总觉得哪里不对。

Agent 类的核心方法长这样：

```java
public WeatherContext execute(String input) {
    // 第一步：提取城市
    ParsedQuery p = parseQuery(input);
    // 第二步：查坐标
    GeoLocation g = geocode(p.cityName());
    // 第三步：拿预报
    List<DayForecast> f = forecast(g.lat(), g.lon());
    // 第四步：生成分析
    String a = analyze(p, f);
    // 组装返回
    return assemble(p, g, f, a);
}
```

这不是 Agent。这是 Workflow。

**我把 4 个步骤用代码硬生生串在一起，然后骗自己说这是"AI 智能体"。**

后来我读到了一个真正 Agent 框架的教程（Embabel），发现同样是 4 个步骤，写法完全不同。

---

## 真正的 Agent 怎么写

**Workflow 写法（错的，我一开始写的）：**

```java
// 程序员手动编排每一步的顺序
步骤A();
步骤B();
步骤C();
步骤D();
```

**Agent 写法（对的，后来改的）：**

```java
@Action  // 我会提取城市名
public ParsedQuery extractCity(UserQuery input) { ... }

@Action  // 我会查坐标
public GeoLocation geocode(ParsedQuery city) { ... }

@Action  // 我会拿预报
public List<DayForecast> forecast(GeoLocation geo) { ... }

@Action  // 我会生成回答（这是最终目标）
@AchievesGoal
public WeatherQueryResult reply(ParsedQuery city, List<DayForecast> data) { ... }
```

看出区别了吗？

Workflow 是 **我给电脑下指令**——先做A，再做B，再做C。

Agent 是 **我给电脑描述能力**——我会做A，会做B，会做C。目标是完成这个任务，你看着办。

"你看着办"这三个字，就是 Agent 和 Workflow 的分界线。

---

## 引擎是怎么"看着办"的

当用户输入"明天上海会下雨吗？"进入系统时，Agent 引擎的世界状态是这样的：

```
当前世界：只有 UserQuery("明天上海会下雨吗？")
目标世界：需要 WeatherQueryResult（回答）
```

引擎开始倒推。从目标出发，找谁能产出它；然后看那个人需要什么，再找谁能产出那个……直到所有条件都满足。

```
目标：WeatherQueryResult
   ↑ 谁能产出它？
   reply(ParsedQuery, List<DayForecast>)
      ↑ 需要 ParsedQuery 和 List<DayForecast
      ├─ ParsedQuery ← extractCity(UserQuery)
      │     ↑ 需要 UserQuery → 已在世界状态 ✅
      └─ List<DayForecast> ← forecast(GeoLocation)
              ↑ 需要 GeoLocation
              geocode(ParsedQuery)
                ↑ 需要 ParsedQuery ← 已满足 ✅
```

路径出来了：

```
UserQuery
    → extractCity（提取城市名）
    → geocode（查坐标）
    → forecast（拿预报）
    → reply（生成回答）
```

**引擎不是一次性规划完就完事了。** 每执行完一个 Action，它会重新审视世界状态，再规划下一步：

```
第1轮：世界有 UserQuery → 规划 extractCity → geocode → forecast → reply
第2轮：extractCity 完成，世界有了 ParsedQuery → 规划 geocode → forecast → reply
第3轮：geocode 完成，世界有了 GeoLocation → 规划 forecast → reply
第4轮：forecast 完成，世界有了 3天数据 → 规划 reply
第5轮：reply 完成 → 目标达成 ✅
```

这就是 GOAP（Goal-Oriented Action Planning）。

---

## 类型匹配才是核心

理解 GOAP 的关键是：**引擎连接的依据是 Java 类型**，不是方法名。

每个 Action 的**参数类型**和**返回类型**就是它的"接口说明"：

| Action | 输入类型 | 输出类型 |
|--------|---------|---------|
| extractCity | `UserQuery` | `ParsedQuery` |
| geocode | `ParsedQuery` | `GeoLocation` |
| forecast | `GeoLocation` | `List<DayForecast>` |
| reply | `ParsedQuery` + `List<DayForecast>` | `WeatherQueryResult` |

引擎的匹配逻辑很简单：

> 谁需要这个类型？谁产出这个类型？
> 
> `ParsedQuery` 是 extractCity 产出的，也是 geocode 需要的 → 连起来。
> 
> `GeoLocation` 是 geocode 产出的，也是 forecast 需要的 → 连起来。

就像管道工接水管——看接口尺寸对得上就接上，不需要画图纸告诉它怎么接。

---

## 为什么我一开始写成了 Workflow

因为**惯性思维**。

写了 10 年代码，习惯了"这个方法做1234"，自然就在 Agent 里写了一个 `execute()` 方法，把 4 个步骤串起来。编译器不报错，测试通过，功能正常——但它不是 Agent。

问题是：**谁来保证这个顺序永远是对的？**

如果明天要加一个"空气质量查询"的步骤，我得打开 `execute()` 方法，找到合适的位置插进去。如果后天要改成"先查空气质量再决定要不要查天气"，我得重写编排逻辑。

而 Agent 不需要——我只需要新增一个 `@Action` 声明"我会查空气质量"，引擎自动决定什么时候用它。

---

## 技术栈

| 组件 | 说明 |
|------|------|
| Java 21 | Record + Pattern Matching |
| Spring Boot 3.5.9 | Web 框架 |
| Embabel 0.3.5 | GOAP Agent 框架 |
| DeepSeek V4 | 城市名提取 + 天气分析 |
| OpenWeather API | 天气数据源 |
| Langfuse | OpenTelemetry 可观测性 |

---

## 踩坑记录

**坑 1：Embabel 启动报错 Default LLM not found**

通用启动器不注册具体 LLM。需要用 `embabel-agent-starter-deepseek`。

**坑 2：Spring AI 版本冲突**

Embabel 内嵌了 Spring AI 1.1.4，手动加其他版本会冲突。用专用启动器就没这个问题。

**坑 3：写了 Workflow 以为是 Agent**

这个坑最隐蔽，也最值得写出来。两者的分界线不在于技术，而在于**思维模式**——你是在"指挥"还是在"描述能力"。

---

## 项目地址

**[github.com/quarktimes/embabel-weather](https://github.com/quarktimes/embabel-weather)**

```bash
export DEEPSEEK_API_KEY=sk-xxx
export OPENWEATHER_API_KEY=xxx
mvn spring-boot:run
```

打开 http://localhost:8080，输入"明天上海会下雨吗？"试试。

---

*本文由 Claude Code 辅助撰写。*
