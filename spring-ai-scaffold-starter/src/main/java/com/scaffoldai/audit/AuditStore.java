package com.scaffoldai.audit;

import java.util.List;

/**
 * 审计存储 SPI。
 */
public interface AuditStore {

    String type();

    void save(AuditRecord record);

    List<AuditRecord> recent(String tenantId, int limit);

    List<UsageStat> stats(String tenantId, int days);
}
