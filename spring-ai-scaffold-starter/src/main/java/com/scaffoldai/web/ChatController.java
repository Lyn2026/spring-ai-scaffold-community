package com.scaffoldai.web;

import com.scaffoldai.autoconfigure.AiScaffoldProperties;
import com.scaffoldai.chat.ChatAnswer;
import com.scaffoldai.chat.ChatCommand;
import com.scaffoldai.chat.ChatService;
import com.scaffoldai.chat.ChatStreamService;
import com.scaffoldai.memory.ChatMemoryService;
import com.scaffoldai.security.TenantContext;
import com.scaffoldai.security.TenantPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话接口：非流式、流式（SSE）、会话清理。
 *
 * <p>SSE 事件：meta → token* → done / error。流式链路不支持工具调用，
 * 需要函数调用时请使用非流式 POST /chat。</p>
 */
@RestController
@RequestMapping("${ai.scaffold.web.base-path:/api/ai}")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;
    private final ChatMemoryService chatMemoryService;
    private final AiScaffoldProperties properties;

    public ChatController(ChatService chatService, ChatStreamService chatStreamService,
                          ChatMemoryService chatMemoryService, AiScaffoldProperties properties) {
        this.chatService = chatService;
        this.chatStreamService = chatStreamService;
        this.chatMemoryService = chatMemoryService;
        this.properties = properties;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponseDto> chat(@Valid @RequestBody ChatRequestDto body) {
        TenantPrincipal principal = TenantContext.require();
        ChatAnswer answer = chatService.chat(toCommand(principal, body));
        return ApiResponse.ok(new ChatResponseDto(answer.requestId(), answer.conversationId(), answer.answer(),
                answer.model(), answer.usage().promptTokens(), answer.usage().completionTokens(),
                answer.usage().totalTokens(), answer.citations(), answer.toolsUsed(), answer.latencyMs(), "success"));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequestDto body) {
        TenantPrincipal principal = TenantContext.require();
        ChatCommand command = toCommand(principal, body);
        SseEmitter emitter = new SseEmitter((long) properties.getChat().getSseTimeoutSeconds() * 1000L);
        chatStreamService.stream(command, emitter);
        return emitter;
    }

    @DeleteMapping("/chat/{conversationId}")
    public ApiResponse<Boolean> clear(@PathVariable String conversationId) {
        TenantPrincipal principal = TenantContext.require();
        chatMemoryService.clear(principal.tenantId(), conversationId);
        return ApiResponse.ok(Boolean.TRUE);
    }

    @GetMapping("/conversations")
    public ApiResponse<List<com.scaffoldai.memory.ConversationSummary>> conversations(
            @RequestParam(defaultValue = "20") int limit) {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(chatMemoryService.conversations(principal.tenantId(), limit));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<com.scaffoldai.model.ChatMessage>> messages(@PathVariable String conversationId,
                                                                        @RequestParam(defaultValue = "20") int limit) {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(chatMemoryService.history(principal.tenantId(), conversationId, limit));
    }

    private ChatCommand toCommand(TenantPrincipal principal, ChatRequestDto body) {
        return ChatCommand.builder()
                .tenantId(principal.tenantId())
                .userId(principal.userId())
                .conversationId(body.getConversationId())
                .question(body.getQuestion())
                .model(body.getModel())
                .kbIds(body.getKbIds() == null ? List.of() : body.getKbIds())
                .useRag(body.getUseRag() == null || body.getUseRag())
                .useTools(body.getUseTools() == null || body.getUseTools())
                .temperature(body.getTemperature())
                .maxTokens(body.getMaxTokens())
                .systemPrompt(body.getSystemPrompt())
                .build();
    }
}
