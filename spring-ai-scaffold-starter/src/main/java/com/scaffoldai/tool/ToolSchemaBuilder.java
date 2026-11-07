package com.scaffoldai.tool;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据方法签名生成 JSON Schema（OpenAI function calling 格式）。
 * 仅支持基础类型与 List/Map，复杂对象请用字符串传入并在工具内部解析。
 */
public final class ToolSchemaBuilder {

    private ToolSchemaBuilder() {
    }

    public static Map<String, Object> buildSchema(java.lang.reflect.Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String name = parameter.getName();
            if (name == null || name.isBlank() || name.startsWith("arg")) {
                name = "arg" + i;
            }
            ToolParam annotation = parameter.getAnnotation(ToolParam.class);
            Map<String, Object> property = new LinkedHashMap<>();
            Class<?> type = parameter.getType();
            if (List.class.equals(type)) {
                property.put("type", "array");
                property.put("items", Map.of("type", "string"));
            } else if (Map.class.equals(type)) {
                property.put("type", "object");
            } else {
                property.put("type", jsonType(type));
            }
            if (annotation != null && !annotation.value().isBlank()) {
                property.put("description", annotation.value());
            }
            properties.put(name, property);
            if (annotation == null || annotation.required()) {
                required.add(name);
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static String jsonType(Class<?> type) {
        if (String.class.equals(type)) {
            return "string";
        }
        if (int.class.equals(type) || Integer.class.equals(type) || long.class.equals(type) || Long.class.equals(type)) {
            return "integer";
        }
        if (double.class.equals(type) || Double.class.equals(type) || float.class.equals(type) || Float.class.equals(type)) {
            return "number";
        }
        if (boolean.class.equals(type) || Boolean.class.equals(type)) {
            return "boolean";
        }
        return "string";
    }
}
