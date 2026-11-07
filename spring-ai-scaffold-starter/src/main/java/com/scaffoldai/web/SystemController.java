package com.scaffoldai.web;

import com.scaffoldai.audit.AuditService;
import com.scaffoldai.autoconfigure.AiScaffoldProperties;
import com.scaffoldai.memory.ChatMemoryService;
import com.scaffoldai.model.ModelRegistry;
import com.scaffoldai.rag.KnowledgeStore;
import com.scaffoldai.security.TenantContext;
import com.scaffoldai.tool.ToolDefinition;
import com.scaffoldai.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时自检接口：模型清单、工具清单、存储实现、关键开关。
 * 排查环境问题、给客户演示时先调这里。
 */
@RestController
@RequestMapping("${ai.scaffold.web.base-path:/api/ai}")
public class SystemController {

    private final ModelRegistry modelRegistry;
    private final ToolRegistry toolRegistry;
    private final ChatMemoryService chatMemoryService;
    private final KnowledgeStore knowledgeStore;
    private final AuditService auditService;
    private final AiScaffoldProperties properties;

    public SystemController(ModelRegistry modelRegistry, ToolRegistry toolRegistry,
                            ChatMemoryService chatMemoryService, KnowledgeStore knowledgeStore,
                            AuditService auditService, AiScaffoldProperties properties) {
        this.modelRegistry = modelRegistry;
        this.toolRegistry = toolRegistry;
        this.chatMemoryService = chatMemoryService;
        this.knowledgeStore = knowledgeStore;
        this.auditService = auditService;
        this.properties = properties;
    }

    @GetMapping("/models")
    public ApiResponse<Map<String, Object>> models() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chat", modelRegistry.describe());
        result.put("embedding", modelRegistry.describeEmbedding());
        return ApiResponse.ok(result);
    }

    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> tools() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, ToolDefinition> entry : toolRegistry.definitions().entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("description", entry.getValue().description());
            item.put("parameters", entry.getValue().schema());
            list.add(item);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("tenant", TenantContext.current() == null ? null : TenantContext.current().tenantId());
        result.put("chatModels", modelRegistry.describe().size());
        result.put("embeddingModels", modelRegistry.describeEmbedding().size());
        result.put("tools", toolRegistry.size());
        result.put("memoryStore", chatMemoryService.store().type());
        result.put("knowledgeStore", knowledgeStore.type());
        result.put("auditStore", auditService.storeType());
        result.put("securityMode", properties.getSecurity().getMode());
        result.put("rateLimitEnabled", properties.getRateLimit().isEnabled());
        result.put("moderationEnabled", properties.getModeration().isEnabled());
        return ApiResponse.ok(result);
    }
}
