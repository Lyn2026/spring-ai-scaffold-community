package com.scaffoldai.rag;

import java.io.InputStream;
import java.util.Set;

/**
 * 文档解析 SPI。新增格式（Word/Excel/PPT）实现本接口并注册为 Bean 即可。
 */
public interface DocumentParser {

    String name();

    Set<String> extensions();

    String parse(InputStream inputStream);
}
