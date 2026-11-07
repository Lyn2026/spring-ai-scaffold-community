package com.scaffoldai.tool;

import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.model.ToolSpec;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心：启动时扫描所有 Bean 上的 {@link AiTool} 方法，生成 function calling 所需的 Schema；
 * 也可通过 {@link #register} 动态注册（例如 MCP 远程工具）。
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ObjectMapper mapper = JsonUtils.mapper();
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    public ToolRegistry(ApplicationContext context) {
        scan(context);
    }

    public void scan(ApplicationContext context) {
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = context.getBean(beanName);
            } catch (RuntimeException e) {
                continue;
            }
            if (bean == null) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getDeclaredMethods()) {
                AiTool annotation = method.getAnnotation(AiTool.class);
                if (annotation == null) {
                    continue;
                }
                String toolName = annotation.name().isBlank() ? method.getName() : annotation.name();
                if (tools.containsKey(toolName)) {
                    log.warn("工具名重复，忽略 {}#{}", targetClass.getSimpleName(), method.getName());
                    continue;
                }
                String description = annotation.description().isBlank()
                        ? "调用 " + method.getName()
                        : annotation.description();
                ToolDefinition definition = new ToolDefinition(toolName, description,
                        ToolSchemaBuilder.buildSchema(method), reflectionInvoker(bean, method));
                tools.put(toolName, definition);
            }
        }
        if (!tools.isEmpty()) {
            log.info("已注册 AI 工具 {} 个: {}", tools.size(), tools.keySet());
        }
    }

    public void register(ToolDefinition definition) {
        tools.putIfAbsent(definition.name(), definition);
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    public int size() {
        return tools.size();
    }

    public List<String> names() {
        return new ArrayList<>(tools.keySet());
    }

    public Map<String, ToolDefinition> definitions() {
        return Map.copyOf(tools);
    }

    public List<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (ToolDefinition definition : tools.values()) {
            specs.add(definition.toSpec());
        }
        return specs;
    }

    /**
     * 执行工具。异常统一转成字符串结果，避免模型因为工具报错中断整轮对话。
     */
    public String execute(String toolName, String argumentsJson) {
        ToolDefinition definition = tools.get(toolName);
        if (definition == null) {
            return "工具不存在: " + toolName;
        }
        Map<String, Object> arguments;
        try {
            arguments = JsonUtils.fromMap(argumentsJson);
        } catch (RuntimeException e) {
            return "工具参数不是合法 JSON: " + e.getMessage();
        }
        try {
            String result = definition.invoker().invoke(arguments);
            return result == null ? "" : result;
        } catch (RuntimeException e) {
            log.warn("工具 {} 执行失败: {}", toolName, e.getMessage());
            return "工具执行失败: " + e.getMessage();
        }
    }

    private ToolInvoker reflectionInvoker(Object bean, Method method) {
        return arguments -> {
            Parameter[] parameters = method.getParameters();
            Object[] methodArgs = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                String name = parameters[i].getName();
                if (name == null || name.isBlank() || name.startsWith("arg")) {
                    name = "arg" + i;
                }
                Object raw = arguments == null ? null : arguments.get(name);
                Class<?> type = parameters[i].getType();
                if (raw == null) {
                    methodArgs[i] = defaultValue(type);
                } else {
                    JavaType javaType = mapper.getTypeFactory().constructType(parameters[i].getParameterizedType());
                    methodArgs[i] = mapper.convertValue(raw, javaType);
                }
            }
            try {
                Method invocable = AopUtils.selectInvocableMethod(method, bean.getClass());
                Object result = invocable.invoke(bean, methodArgs);
                if (result == null) {
                    return "";
                }
                return result instanceof String text ? text : JsonUtils.toJson(result);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                throw new IllegalStateException(target == null ? e.getMessage() : target.getMessage(), target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("工具方法调用失败: " + e.getMessage(), e);
            }
        };
    }

    private Object defaultValue(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == boolean.class) {
            return false;
        }
        return null;
    }
}
