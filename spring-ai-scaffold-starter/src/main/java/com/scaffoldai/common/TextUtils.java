package com.scaffoldai.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文本与向量工具：分词（中英混合）、相似度、token 估算、向量序列化。
 */
public final class TextUtils {

    private TextUtils() {
    }

    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    public static String defaultIfBlank(String text, String defaultValue) {
        return hasText(text) ? text : defaultValue;
    }

    /**
     * 粗粒度 token 估算：CJK 字符按 1 token，其它字符按 4 字符 1 token。
     * 仅在模型没有返回 usage 时兜底，用于统计与限流，不用于精确计费。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        int fromOther = (int) Math.ceil(other / 4.0);
        return Math.max(1, cjk + fromOther);
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF) || (c >= 0xF900 && c <= 0xFAFF);
    }

    /**
     * 中英混合分词：英文按单词，中文按二元组，用于关键词召回打分。
     */
    public static List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        if (!hasText(text)) {
            return result;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        StringBuilder latin = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (isCjk(c)) {
                if (latin.length() > 0) {
                    addIfMeaningful(result, latin.toString());
                    latin.setLength(0);
                }
                if (i + 1 < normalized.length() && isCjk(normalized.charAt(i + 1))) {
                    result.add(normalized.substring(i, i + 2));
                } else {
                    result.add(String.valueOf(c));
                }
            } else if (Character.isLetterOrDigit(c)) {
                latin.append(c);
            } else {
                if (latin.length() > 0) {
                    addIfMeaningful(result, latin.toString());
                    latin.setLength(0);
                }
            }
        }
        if (latin.length() > 0) {
            addIfMeaningful(result, latin.toString());
        }
        return result;
    }

    private static void addIfMeaningful(List<String> sink, String token) {
        if (token.length() >= 2) {
            sink.add(token);
        }
    }

    /**
     * 关键词覆盖率：查询词在文档中命中的比例，0~1。
     */
    public static double keywordCoverage(String query, String document) {
        List<String> queryTerms = terms(query);
        if (queryTerms.isEmpty() || !hasText(document)) {
            return 0.0;
        }
        String lowerDoc = document.toLowerCase(Locale.ROOT);
        int hit = 0;
        for (String term : queryTerms) {
            if (lowerDoc.contains(term)) {
                hit++;
            }
        }
        return (double) hit / queryTerms.size();
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 把向量序列化成 pgvector 可接受的字面量，如 [0.1,0.2,0.3]。
     */
    public static String toPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
