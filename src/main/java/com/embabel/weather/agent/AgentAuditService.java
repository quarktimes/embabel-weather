package com.embabel.weather.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 审计服务 — 在内存中保留最近 N 条执行记录
 * <p>
 * 用于页面展示 Agent 执行链路（用户透明）+ 后端排查。
 */
@Slf4j
@Service
public class AgentAuditService {

    private static final int MAX_RECORDS = 100;

    private final CopyOnWriteArrayList<AgentAuditRecord> records = new CopyOnWriteArrayList<>();

    /** 生成新的 traceId */
    public String startTrace(String userInput) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Trace:{}] 开始处理: {}", traceId, userInput);
        return traceId;
    }

    /** 记录一个执行步骤 */
    public void record(String traceId, int stepOrder, String stepName,
                       String input, String output, long durationMs,
                       boolean success, String errorMessage) {
        var record = new AgentAuditRecord(traceId, stepOrder, stepName,
                input, output, durationMs, success, errorMessage,
                new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()));
        records.add(record);
        if (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
        if (success) {
            log.info("[Trace:{}] ✅ {} ({}ms) → {}", traceId, stepName, durationMs, output);
        } else {
            log.warn("[Trace:{}] ❌ {} → {}", traceId, stepName, errorMessage);
        }
    }

    /** 获取某次 trace 的所有步骤 */
    public List<AgentAuditRecord> getTrace(String traceId) {
        return records.stream()
                .filter(r -> r.traceId().equals(traceId))
                .toList();
    }

    /** 获取最近所有审计记录 */
    public List<AgentAuditRecord> getRecentRecords() {
        return List.copyOf(records);
    }
}
