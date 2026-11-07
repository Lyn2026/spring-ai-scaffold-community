package com.scaffoldai.rag;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 解析器路由：按扩展名选择解析器，未支持的类型给出明确错误。
 */
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = new ArrayList<>(parsers);
    }

    public DocumentParser resolve(String filename) {
        String ext = extension(filename);
        for (DocumentParser parser : parsers) {
            if (parser.extensions().contains(ext)) {
                return parser;
            }
        }
        throw new AiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "暂不支持的文件类型: ." + ext + "，当前支持: " + supportedExtensions());
    }

    public List<String> supportedExtensions() {
        return parsers.stream().flatMap(p -> p.extensions().stream()).sorted().toList();
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
