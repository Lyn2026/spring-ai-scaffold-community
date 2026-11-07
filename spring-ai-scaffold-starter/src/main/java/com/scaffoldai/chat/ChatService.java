package com.scaffoldai.chat;

import com.scaffoldai.audit.AuditRecord;
import com.scaffoldai.audit.AuditService;
import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;
import com.scaffoldai.common.Ids;
import com.scaffoldai.common.TextUtils;
import com.scaffoldai.memory.ChatMemoryService;
import com.scaffoldai.metrics.AiMetrics;
import com.scaffoldai.model.ChatMessage;
import com.scaffoldai.model.ChatModel;
import com.scaffoldai.model.ChatRequest;
import com.scaffoldai.model.ChatResponse;
import com.scaffoldai.model.ModelRegistry;
import com.scaffoldai.model.TokenUsage;
import com.scaffoldai.model.ToolCall;
import com.scaffoldai.model.ToolSpec;
import com.scaffoldai.moderation.ModerationResult;
import com.scaffoldai.moderation.ModerationService;
import com.scaffoldai.prompt.PromptRegistry;
import com.scaffoldai.rag.KbInfo;
import com.scaffoldai.rag.KnowledgeBaseService;
import com.scaffoldai.rag.RagContext;
import com.scaffoldai.rag.Retriever;
import com.scaffoldai.ratelimit.RateLimitService;
import com.scaffoldai.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话主编排：限流 → 审核 → 记忆 → 检索 → 工具调用 → 生成 → 审计/指标。
 * 任何一环失败都保留可观测记录；检索失败自动降级为纯对话，不阻塞主流程。
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String DEFAULT_CHAT_PROMPT =
            "你是一个专业、严谨的 AI 助手。回答使用简体中文，信息不足时明确说明不要编造。";
    private static final String DEFAULT_RAG_PROMPT =
            "你是企业知识库问答助手。优先依据【资料】回答，并在结论后标注引用编号如 [1]。"
                    + "资料中没有的内容，明确回答「知识库中没有找到相关信息」，禁止编造。\n\n【资料】\n{{context}}";

    private final ModelRegistry models;
    private final ChatMemoryService memory;
    private final Retriever retriever;
    private final KnowledgeBaseService knowledgeBase;
    private final ToolRegistry toolRegistry;
    private final PromptRegistry prompts;
    private final ModerationService moderation;
    private final RateLimitService rateLimit;
    private final AuditService audit;
    private final AiMetrics metrics;
    private final int maxToolRounds;

    public ChatService(ModelRegistry models, ChatMemoryService memory, Retriever retriever,
                       KnowledgeBaseService knowledgeBase, ToolRegistry toolRegistry, PromptRegistry prompts,
                       ModerationService moderation, RateLimitService rateLimit, AuditService audit,
                       AiMetrics metrics, int maxToolRounds) {
        this.models = models;
        this.memory = memory;
        this.retriever = retriever;
        this.knowledgeBase = knowledgeBase;
        this.toolRegistry = toolRegistry;
        this.prompts = prompts;
        this.moderation = moderation;
        this.rateLimit = rateLimit;
        this.audit = audit;
        this.metrics = metrics;
        this.maxToolRounds = Math.max(0, maxToolRounds);
    }

    public ChatAnswer chat(ChatCommand command) {
        ChatPreparation prep = prepare(command);
        long started = System.currentTimeMillis();
        try {
            ChatResponse response = callModel(prep);
            TokenUsage usage = response.usage();
            List<String> usedTools = new ArrayList<>();
            int rounds = 0;
            while (response.hasToolCalls() && rounds < maxToolRounds) {
                rounds++;
                prep.messages().add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
                for (ToolCall toolCall : response.toolCalls()) {
                    String result = toolRegistry.execute(toolCall.name(), toolCall.arguments());
                    usedTools.add(toolCall.name());
                    metrics.toolCall(toolCall.name(), "ok");
                    prep.messages().add(ChatMessage.toolResult(toolCall.id(), toolCall.name(), result));
                }
                ChatResponse next = callModel(prep);
                usage = usage.plus(next.usage());
                response = next;
            }
            String answer = response.content();
            if (!TextUtils.hasText(answer) && !usedTools.isEmpty()) {
                answer = "工具已执行，但模型未返回文本结果，请调整提问或稍后重试。";
            }
            ModerationResult outputCheck = moderation.check(answer);
            if (!outputCheck.allowed()) {
                long latency = System.currentTimeMillis() - started;
                auditFailure(prep, "", TokenUsage.ZERO, latency,
                        AuditRecord.STATUS_BLOCKED, "OUTPUT_MODERATION_BLOCKED: " + outputCheck.reason());
                throw new AiException(ErrorCode.MODERATION_BLOCKED, "输出内容未通过审核");
            }
            long latency = System.currentTimeMillis() - started;
            complete(prep, answer, usage, latency);
            return new ChatAnswer(prep.requestId(), prep.conversationId(), answer, prep.modelName(),
                    usage, prep.ragContext().citations(), usedTools, latency);
        } catch (AiException e) {
            if (!ErrorCode.MODERATION_BLOCKED.equals(e.code())) {
                auditFailure(prep, "", TokenUsage.ZERO, System.currentTimeMillis() - started,
                        AuditRecord.STATUS_FAILED, e.code().name());
            }
            throw e;
        }
    }

    /**
     * 准备阶段：身份/限流/审核/检索/提示词。流式接口复用同一套逻辑。
     */
    public ChatPreparation prepare(ChatCommand command) {
        long started = System.currentTimeMillis();
        String requestId = Ids.requestId();
        String tenantId = TextUtils.defaultIfBlank(command.tenantId(), "public");
        String userId = TextUtils.defaultIfBlank(command.userId(), "anonymous");
        if (!TextUtils.hasText(command.question())) {
            throw AiException.invalid("问题不能为空");
        }
        String conversationId = TextUtils.defaultIfBlank(command.conversationId(), "conv_" + Ids.shortId());
        String modelName = resolveModelName(command.model());
        ChatModel model = models.chatModel(modelName);

        try {
            rateLimit.check(tenantId, userId);
        } catch (AiException e) {
            audit.record(requestId, tenantId, userId, conversationId, modelName, command.question(), "",
                    TokenUsage.ZERO, System.currentTimeMillis() - started,
                    AuditRecord.STATUS_FAILED, e.code().name());
            throw e;
        }

        ModerationResult inputCheck = moderation.check(command.question());
        if (!inputCheck.allowed()) {
            audit.record(requestId, tenantId, userId, conversationId, modelName, command.question(), "",
                    TokenUsage.ZERO, System.currentTimeMillis() - started,
                    AuditRecord.STATUS_BLOCKED, "INPUT_MODERATION_BLOCKED");
            throw new AiException(ErrorCode.MODERATION_BLOCKED, "输入内容未通过审核: " + inputCheck.reason());
        }

        RagContext ragContext = RagContext.empty();
        if (command.useRag() && !command.kbIds().isEmpty()) {
            long ragStart = System.currentTimeMillis();
            try {
                ragContext = retriever.retrieve(tenantId, command.kbIds(), command.question(),
                        embeddingModelName(tenantId, command.kbIds()));
            } catch (RuntimeException e) {
                log.warn("知识库检索失败，降级为普通对话: {}", e.getMessage());
            } finally {
                metrics.ragLatency(System.currentTimeMillis() - ragStart);
            }
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt(command.systemPrompt(), ragContext)));
        messages.addAll(memory.history(tenantId, conversationId));
        messages.add(ChatMessage.user(command.question()));

        List<ToolSpec> tools = command.useTools() && !toolRegistry.isEmpty()
                ? toolRegistry.specs()
                : List.of();

        return new ChatPreparation(requestId, tenantId, userId, conversationId, command.question(),
                modelName, model, messages, ragContext, tools, command.temperature(), command.maxTokens(), started);
    }

    /**
     * 收尾阶段：记忆、审计、指标。流式接口在流结束后调用。
     */
    public void complete(ChatPreparation prep, String answer, TokenUsage usage, long latencyMs) {
        memory.recordExchange(prep.tenantId(), prep.conversationId(), prep.question(), answer);
        audit.record(prep.requestId(), prep.tenantId(), prep.userId(), prep.conversationId(), prep.modelName(),
                prep.question(), answer, usage, latencyMs, AuditRecord.STATUS_SUCCESS, null);
        metrics.chatRequest(prep.modelName(), "success");
        metrics.tokens(prep.modelName(), usage.promptTokens(), usage.completionTokens());
        metrics.chatLatency(prep.modelName(), latencyMs);
    }

    public ChatResponse callModel(ChatPreparation prep) {
        // 请求体里的 model 必须是供应商真实模型名（如 qwen-plus），不是注册表里的逻辑名（如 dashscope）
        long started = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("模型调用 -> 逻辑名={} provider={} 真实模型={} 消息数={} 工具数={} temperature={} maxTokens={} 引用数={}",
                    prep.modelName(), prep.model().provider(), prep.model().modelName(),
                    prep.messages().size(), prep.tools().size(),
                    prep.temperature(), prep.maxTokens(), prep.ragContext().citations().size());
        }
        ChatResponse response = prep.model().chat(new ChatRequest(prep.model().modelName(), prep.messages(),
                prep.temperature(), prep.maxTokens(), prep.tools(), Map.of()));
        if (log.isDebugEnabled()) {
            log.debug("模型返回 <- 逻辑名={} 真实模型={} 耗时={}ms promptTokens={} completionTokens={} 工具调用={}",
                    prep.modelName(), prep.model().modelName(), System.currentTimeMillis() - started,
                    response.usage().promptTokens(), response.usage().completionTokens(),
                    response.hasToolCalls() ? response.toolCalls().size() : 0);
        }
        return response;
    }

    public void auditFailure(ChatPreparation prep, String answer, TokenUsage usage, long latencyMs,
                             String status, String errorCode) {
        memory.appendAssistant(prep.tenantId(), prep.conversationId(),
                "[调用失败] " + TextUtils.truncate(errorCode, 200));
        audit.record(prep.requestId(), prep.tenantId(), prep.userId(), prep.conversationId(), prep.modelName(),
                prep.question(), answer, usage, latencyMs, status, TextUtils.truncate(errorCode, 200));
        metrics.chatRequest(prep.modelName(), "failed");
    }

    private String resolveModelName(String requested) {
        return TextUtils.hasText(requested) ? requested : models.defaultChatModel().name();
    }

    private String embeddingModelName(String tenantId, List<String> kbIds) {
        for (KbInfo info : knowledgeBase.listKbs(tenantId)) {
            if (kbIds.contains(info.id())) {
                return info.embeddingModel();
            }
        }
        return null;
    }

    private String systemPrompt(String override, RagContext ragContext) {
        if (TextUtils.hasText(override)) {
            return override;
        }
        boolean withRag = !ragContext.isEmpty();
        String code = withRag ? "system.rag" : "system.chat";
        String template = prompts.get(code);
        if (!TextUtils.hasText(template)) {
            template = withRag ? DEFAULT_RAG_PROMPT : DEFAULT_CHAT_PROMPT;
        }
        return withRag
                ? PromptRegistry.renderTemplate(template, Map.of("context", ragContext.contextText()))
                : template;
    }
}
