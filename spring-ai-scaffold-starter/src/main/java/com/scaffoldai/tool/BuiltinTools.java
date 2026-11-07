package com.scaffoldai.tool;

import com.scaffoldai.common.TextUtils;
import com.scaffoldai.rag.RagContext;
import com.scaffoldai.rag.Retriever;
import com.scaffoldai.security.TenantContext;
import com.scaffoldai.security.TenantPrincipal;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 内置工具：时间查询、知识库检索。
 * 业务方可在自己的 @Component 中继续添加 {@link AiTool} 方法，会被自动注册。
 */
public class BuiltinTools {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Retriever retriever;

    public BuiltinTools(Retriever retriever) {
        this.retriever = retriever;
    }

    @AiTool(name = "current_time", description = "获取指定时区的当前时间。当问题涉及今天、本周、截止时间等相对时间时调用。")
    public String currentTime(@ToolParam("IANA 时区，例如 Asia/Shanghai，缺省为 Asia/Shanghai") String zone) {
        ZoneId zoneId = ZoneId.of(TextUtils.defaultIfBlank(zone, "Asia/Shanghai"));
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return now.format(FORMATTER) + " (" + zoneId + ")";
    }

    @AiTool(name = "knowledge_search", description = "在当前租户的知识库中检索与问题最相关的资料片段。回答内部制度、产品文档类问题前优先调用。")
    public String knowledgeSearch(@ToolParam("知识库 ID") String kbId,
                                  @ToolParam("检索关键词或完整问题") String query) {
        TenantPrincipal principal = TenantContext.current();
        if (principal == null) {
            return "缺少租户上下文，无法检索知识库";
        }
        if (!TextUtils.hasText(kbId)) {
            return "缺少参数 kbId";
        }
        RagContext context = retriever.retrieve(principal.tenantId(), List.of(kbId), query, null);
        if (context.isEmpty()) {
            return "知识库中未找到相关内容";
        }
        return context.contextText();
    }
}
