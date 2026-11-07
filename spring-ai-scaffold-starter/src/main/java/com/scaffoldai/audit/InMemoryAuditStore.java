package com.scaffoldai.audit;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 进程内审计存储：无数据库时的兜底，重启丢失，仅用于开发与小流量验证。
 */
public class InMemoryAuditStore implements AuditStore {

    private final ConcurrentLinkedDeque<AuditRecord> records = new ConcurrentLinkedDeque<>();
    private final int capacity;

    public InMemoryAuditStore(int capacity) {
        this.capacity = Math.max(100, capacity);
    }

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public void save(AuditRecord record) {
        records.addFirst(record);
        while (records.size() > capacity) {
            records.pollLast();
        }
    }

    @Override
    public List<AuditRecord> recent(String tenantId, int limit) {
        List<AuditRecord> result = new ArrayList<>();
        for (AuditRecord record : records) {
            if (record.tenantId().equals(tenantId)) {
                result.add(record);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<UsageStat> stats(String tenantId, int days) {
        LocalDate from = LocalDate.now().minusDays(Math.max(1, days));
        Map<String, long[]> aggregated = new LinkedHashMap<>();
        for (AuditRecord record : records) {
            if (!record.tenantId().equals(tenantId)) {
                continue;
            }
            LocalDate day = record.createdAt().atZone(ZoneId.systemDefault()).toLocalDate();
            if (day.isBefore(from)) {
                continue;
            }
            String key = day + "|" + record.model();
            long[] values = aggregated.computeIfAbsent(key, k -> new long[3]);
            values[0] += 1;
            values[1] += record.promptTokens();
            values[2] += record.completionTokens();
        }
        List<UsageStat> stats = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : aggregated.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            long[] values = entry.getValue();
            stats.add(new UsageStat(parts[0], parts[1], values[0], values[1], values[2]));
        }
        stats.sort(Comparator.comparing(UsageStat::day).reversed());
        return stats;
    }
}
