package com.scaffoldai.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词注册中心：从 classpath:prompts/*.md 加载内置模板，按 code 取用并渲染。
 *
 * <p>文件名即 code：<code>system-chat.md</code> → <code>system.chat</code>。
 * 模板用 <code>{{变量}}</code> 占位，渲染时未填充的占位会被清空。</p>
 *
 * <p>多版本管理、按版本发布与灰度启用（ai_prompt 表）属于企业版能力；本版只读内置模板，
 * 想改提示词直接改 prompts 目录下的 md 文件。</p>
 */
public class PromptRegistry {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public PromptRegistry() {
        loadDefaults();
    }

    /**
     * 取模板内容，不存在时返回空串。
     */
    public String get(String code) {
        return templates.getOrDefault(code, "");
    }

    public String render(String code, Map<String, String> variables) {
        return renderTemplate(get(code), variables);
    }

    public Map<String, Object> describe() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("codes", new ArrayList<>(templates.keySet()));
        return result;
    }

    /**
     * 内置模板：{{var}} 占位，缺失变量替换为空字符串。
     */
    public static String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result.replaceAll("\\{\\{\\w+}}", "");
    }

    private void loadDefaults() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:prompts/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String code = filename.substring(0, filename.length() - 3).replace('-', '.');
                try (InputStream in = resource.getInputStream()) {
                    templates.put(code, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            log.info("加载内置提示词模板 {} 个: {}", templates.size(), templates.keySet());
        } catch (IOException e) {
            log.warn("加载内置提示词模板失败: {}", e.getMessage());
        }
    }
}
