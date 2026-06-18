package com.embabel.weather.agent;

/**
 * Agent 执行步骤审计记录 —— 页面上展示 Agent 执行链路
 *
 * @param traceId      追踪 ID（一次查询唯一）
 * @param stepOrder    步骤序号
 * @param stepName     步骤名 parseQuery/geocode/forecast/analyze
 * @param input        输入摘要
 * @param output       输出摘要
 * @param durationMs   耗时
 * @param success      是否成功
 * @param errorMessage 错误信息
 * @param timestamp    时间戳 HH:mm:ss.SSS
 */
public record AgentAuditRecord(
        String traceId,
        int stepOrder,
        String stepName,
        String input,
        String output,
        long durationMs,
        boolean success,
        String errorMessage,
        String timestamp
) {}
