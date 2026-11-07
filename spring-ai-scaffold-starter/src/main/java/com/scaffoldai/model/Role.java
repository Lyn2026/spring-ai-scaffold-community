package com.scaffoldai.model;

/**
 * 消息角色。与 OpenAI / Ollama 的 role 一一对应。
 */
public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    public String wireName() {
        return name().toLowerCase();
    }

    public static Role fromWire(String role) {
        if (role == null) {
            return USER;
        }
        switch (role.toLowerCase()) {
            case "system":
                return SYSTEM;
            case "assistant":
                return ASSISTANT;
            case "tool":
            case "function":
                return TOOL;
            default:
                return USER;
        }
    }
}
