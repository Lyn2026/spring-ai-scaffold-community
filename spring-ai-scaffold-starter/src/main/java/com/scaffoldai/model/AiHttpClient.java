package com.scaffoldai.model;

import com.scaffoldai.common.JsonUtils;
import com.scaffoldai.common.TextUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 轻量 HTTP 客户端：只依赖 JDK HttpClient，既支持一次性 JSON 调用，
 * 也支持逐行消费 SSE / NDJSON 流，避免为不同供应商引入额外 SDK。
 */
public class AiHttpClient {

    private final HttpClient client;

    public AiHttpClient(Duration connectTimeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String postJson(String url, Map<String, String> headers, Object payload, Duration timeout) {
        HttpRequest request = buildRequest(url, headers, payload, timeout);
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw ModelCallException.httpError(status, url, response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new ModelCallException("模型请求失败 " + url + " : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("模型请求被中断 " + url, e);
        }
    }

    /**
     * 流式调用。onLine 返回 false 时停止读取（例如收到 [DONE]）。
     */
    public void streamJsonLines(String url, Map<String, String> headers, Object payload,
                                Duration timeout, Function<String, Boolean> onLine) {
        HttpRequest request = buildRequest(url, headers, payload, timeout);
        try {
            HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String body = response.body().reduce("", (a, b) -> a + b);
                throw ModelCallException.httpError(status, url, body);
            }
            try (Stream<String> lines = response.body()) {
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (Boolean.FALSE.equals(onLine.apply(line))) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new ModelCallException("模型流式请求失败 " + url + " : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("模型流式请求被中断 " + url, e);
        }
    }

    private HttpRequest buildRequest(String url, Map<String, String> headers, Object payload, Duration timeout) {
        String body = JsonUtils.toJson(payload);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout == null ? Duration.ofSeconds(60) : timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        Map<String, String> safeHeaders = new HashMap<>(headers == null ? Map.of() : headers);
        safeHeaders.forEach(builder::header);
        return builder.build();
    }

    public static String requireUrl(String baseUrl, String path, String provider) {
        if (!TextUtils.hasText(baseUrl)) {
            throw new ModelCallException("模型 [" + provider + "] 未配置 base-url");
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}
