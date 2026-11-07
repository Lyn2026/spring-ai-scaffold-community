package com.scaffoldai.rag;

/**
 * 引用来源：前端可直接渲染为「[1] 员工手册.pdf 第 3 段」。
 */
public record Citation(int index, String kbId, String docId, String docName, int chunkIndex,
                       String snippet, double score) {
}
