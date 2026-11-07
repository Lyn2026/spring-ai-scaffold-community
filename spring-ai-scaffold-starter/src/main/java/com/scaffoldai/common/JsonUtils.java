package com.scaffoldai.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具。模型层直接使用自己的 ObjectMapper，避免依赖宿主应用的自定义配置。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AiException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败", e);
        }
    }

    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AiException(ErrorCode.INTERNAL_ERROR, "JSON 解析失败: " + abbreviate(json), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new AiException(ErrorCode.INTERNAL_ERROR, "JSON 反序列化失败: " + abbreviate(json), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new AiException(ErrorCode.INTERNAL_ERROR, "JSON 反序列化失败: " + abbreviate(json), e);
        }
    }

    /**
     * 解析 JSON 对象字符串为 Map，空串返回空 Map。
     */
    public static Map<String, Object> fromMap(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new AiException(ErrorCode.INVALID_ARGUMENT, "JSON 对象解析失败: " + abbreviate(json), e);
        }
    }

    public static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        return MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    public static JsonNode valueToTree(Object value) {
        return MAPPER.valueToTree(value);
    }

    public static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return MAPPER.convertValue(node, new TypeReference<List<String>>() {
        });
    }

    private static String abbreviate(String json) {
        if (json == null) {
            return "";
        }
        return json.length() <= 200 ? json : json.substring(0, 200) + "...";
    }
}
