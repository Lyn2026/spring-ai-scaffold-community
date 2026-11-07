package com.scaffoldai.rag;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 纯文本类解析：txt / md / csv / json / log。HTML 仅做去标签处理，不追求排版还原。
 */
public class PlainTextDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("txt", "md", "markdown", "csv", "json", "log", "text");

    @Override
    public String name() {
        return "plain-text";
    }

    @Override
    public Set<String> extensions() {
        return EXTENSIONS;
    }

    @Override
    public String parse(InputStream inputStream) {
        try {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return stripHtml(text);
        } catch (IOException e) {
            throw new AiException(ErrorCode.INTERNAL_ERROR, "读取文本文件失败", e);
        }
    }

    private String stripHtml(String text) {
        if (!text.contains("<")) {
            return text;
        }
        return text.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}
