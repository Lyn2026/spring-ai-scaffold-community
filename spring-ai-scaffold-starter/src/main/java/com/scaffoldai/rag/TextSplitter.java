package com.scaffoldai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 递归字符切分器：先按句子聚合到目标长度，超长句再按窗口硬切，并保留 overlap 上下文。
 * 相比固定长度切分，句子边界更完整，检索命中率更高。
 */
public class TextSplitter {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？；!?;\\n])");

    private final int chunkSize;
    private final int overlap;
    private final int minChunkLength;

    public TextSplitter(int chunkSize, int overlap) {
        this.chunkSize = Math.max(200, chunkSize);
        this.overlap = Math.max(0, Math.min(overlap, this.chunkSize / 2));
        this.minChunkLength = Math.max(10, this.chunkSize / 10);
    }

    public List<String> split(String text) {
        String clean = clean(text);
        if (clean.isEmpty()) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        for (String sentence : SENTENCE_BOUNDARY.split(clean)) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.length() > chunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(hardSplit(sentence));
                continue;
            }
            if (current.length() + sentence.length() + 1 > chunkSize) {
                chunks.add(current.toString().trim());
                String tail = tail(current.toString(), overlap);
                current.setLength(0);
                if (!tail.isEmpty()) {
                    current.append(tail);
                }
            }
            if (current.length() > 0 && !current.toString().endsWith("\n")) {
                current.append('\n');
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return mergeTinyChunks(chunks);
    }

    private List<String> hardSplit(String sentence) {
        List<String> pieces = new ArrayList<>();
        int step = Math.max(1, chunkSize - overlap);
        int start = 0;
        while (start < sentence.length()) {
            int end = Math.min(sentence.length(), start + chunkSize);
            String piece = sentence.substring(start, end).trim();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= sentence.length()) {
                break;
            }
            start += step;
        }
        return pieces;
    }

    private List<String> mergeTinyChunks(List<String> chunks) {
        List<String> merged = new ArrayList<>();
        for (String chunk : chunks) {
            if (merged.isEmpty()) {
                merged.add(chunk);
                continue;
            }
            String last = merged.get(merged.size() - 1);
            if (chunk.length() < minChunkLength && last.length() + chunk.length() <= chunkSize) {
                merged.set(merged.size() - 1, last + "\n" + chunk);
            } else {
                merged.add(chunk);
            }
        }
        return merged;
    }

    private String tail(String text, int length) {
        if (length <= 0 || text.isEmpty()) {
            return "";
        }
        int from = Math.max(0, text.length() - length);
        return text.substring(from).trim();
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\t", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
