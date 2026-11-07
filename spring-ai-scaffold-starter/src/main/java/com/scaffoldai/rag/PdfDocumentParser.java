package com.scaffoldai.rag;

import com.scaffoldai.common.AiException;
import com.scaffoldai.common.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * PDF 解析：基于 PDFBox，仅抽取文本层（扫描件需先做 OCR）。
 */
public class PdfDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("pdf");

    @Override
    public String name() {
        return "pdf";
    }

    @Override
    public Set<String> extensions() {
        return EXTENSIONS;
    }

    @Override
    public String parse(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(document);
            }
        } catch (IOException e) {
            throw new AiException(ErrorCode.INVALID_ARGUMENT, "PDF 解析失败（若为扫描件请先 OCR）: " + e.getMessage(), e);
        }
    }
}
