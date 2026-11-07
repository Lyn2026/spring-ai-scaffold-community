package com.scaffoldai.web;

import com.scaffoldai.rag.Citation;
import com.scaffoldai.rag.DocInfo;
import com.scaffoldai.rag.KbInfo;
import com.scaffoldai.rag.KnowledgeBaseService;
import com.scaffoldai.rag.RagContext;
import com.scaffoldai.rag.Retriever;
import com.scaffoldai.security.TenantContext;
import com.scaffoldai.security.TenantPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库接口：建库、上传文档、检索、删除。
 */
@RestController
@RequestMapping("${ai.scaffold.web.base-path:/api/ai}/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final Retriever retriever;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, Retriever retriever) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.retriever = retriever;
    }

    @PostMapping
    public ApiResponse<KbInfo> create(@RequestBody CreateKbRequest body) {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(knowledgeBaseService.createKb(principal.tenantId(),
                body.name(), body.description(), body.embeddingModel()));
    }

    @GetMapping
    public ApiResponse<List<KbInfo>> list() {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(knowledgeBaseService.listKbs(principal.tenantId()));
    }

    @GetMapping("/{kbId}")
    public ApiResponse<KbInfo> get(@PathVariable String kbId) {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(knowledgeBaseService.requireKb(principal.tenantId(), kbId));
    }

    @DeleteMapping("/{kbId}")
    public ApiResponse<Boolean> delete(@PathVariable String kbId) {
        TenantPrincipal principal = TenantContext.require();
        knowledgeBaseService.deleteKb(principal.tenantId(), kbId);
        return ApiResponse.ok(Boolean.TRUE);
    }

    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<DocInfo>> documents(@PathVariable String kbId) {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(knowledgeBaseService.listDocuments(principal.tenantId(), kbId));
    }

    @PostMapping(value = "/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocInfo> upload(@PathVariable String kbId,
                                       @RequestPart("file") MultipartFile file) throws IOException {
        TenantPrincipal principal = TenantContext.require();
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(com.scaffoldai.common.ErrorCode.INVALID_ARGUMENT, "文件为空");
        }
        DocInfo info = knowledgeBaseService.ingest(principal.tenantId(), kbId,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return ApiResponse.ok(info);
    }

    @DeleteMapping("/{kbId}/documents/{docId}")
    public ApiResponse<Boolean> deleteDocument(@PathVariable String kbId, @PathVariable String docId) {
        TenantPrincipal principal = TenantContext.require();
        knowledgeBaseService.deleteDocument(principal.tenantId(), kbId, docId);
        return ApiResponse.ok(Boolean.TRUE);
    }

    @GetMapping("/{kbId}/documents/{docId}")
    public ApiResponse<DocInfo> document(@PathVariable String kbId, @PathVariable String docId) {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(knowledgeBaseService.getDocument(principal.tenantId(), kbId, docId));
    }

    @PostMapping("/{kbId}/search")
    public ApiResponse<Map<String, Object>> search(@PathVariable String kbId, @RequestBody SearchRequest body) {
        TenantPrincipal principal = TenantContext.require();
        KbInfo kb = knowledgeBaseService.requireKb(principal.tenantId(), kbId);
        RagContext context = retriever.retrieve(principal.tenantId(), List.of(kbId), body.query(),
                kb.embeddingModel());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kbId", kbId);
        result.put("query", body.query());
        result.put("citations", context.citations());
        result.put("context", context.contextText());
        return ApiResponse.ok(result);
    }

    @GetMapping("/{kbId}/chunks/count")
    public ApiResponse<Long> count(@PathVariable String kbId) {
        TenantPrincipal principal = TenantContext.require();
        return ApiResponse.ok(knowledgeBaseService.countChunks(principal.tenantId(), kbId));
    }
}
