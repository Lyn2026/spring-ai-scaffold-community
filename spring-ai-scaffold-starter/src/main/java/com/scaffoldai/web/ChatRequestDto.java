package com.scaffoldai.web;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 对话请求体。
 */
public class ChatRequestDto {

    @NotBlank(message = "question 不能为空")
    private String question;
    private String conversationId;
    private String model;
    private List<String> kbIds;
    private Boolean useRag = Boolean.TRUE;
    private Boolean useTools = Boolean.TRUE;
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getKbIds() {
        return kbIds;
    }

    public void setKbIds(List<String> kbIds) {
        this.kbIds = kbIds;
    }

    public Boolean getUseRag() {
        return useRag;
    }

    public void setUseRag(Boolean useRag) {
        this.useRag = useRag;
    }

    public Boolean getUseTools() {
        return useTools;
    }

    public void setUseTools(Boolean useTools) {
        this.useTools = useTools;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
