package com.shopai.agent.web;

import com.shopai.agent.rag.DocumentIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class PolicyController {

    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    private final DocumentIndexService indexService;
    private final Path policiesPath;

    public PolicyController(DocumentIndexService indexService) {
        this.indexService = indexService;
        String resourcePath = getClass().getClassLoader().getResource("").getPath();
        this.policiesPath = Paths.get(resourcePath, "policies").normalize();
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".md") && !filename.endsWith(".txt"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .md / .txt 文件"));
            }

            Files.createDirectories(policiesPath);
            Path dest = policiesPath.resolve(filename);
            file.transferTo(dest.toFile());

            log.info("Document uploaded: {}", filename);
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "filename", filename,
                "message", "上传成功，请重建索引以生效"
            ));
        } catch (IOException e) {
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments() {
        List<Map<String, Object>> docs = new ArrayList<>();
        File[] files = policiesPath.toFile().listFiles((dir, name) -> name.endsWith(".md") || name.endsWith(".txt"));
        if (files != null) {
            for (File f : files) {
                docs.add(Map.of(
                    "id", f.getName(),
                    "name", f.getName(),
                    "size", f.length(),
                    "updatedAt", new Date(f.lastModified()).toString()
                ));
            }
        }
        return ResponseEntity.ok(docs);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String id) {
        try {
            Path file = policiesPath.resolve(id).normalize();
            if (!file.startsWith(policiesPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "非法路径"));
            }
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Document deleted: {}", id);
                return ResponseEntity.ok(Map.of("status", "ok", "message", "删除成功，请重建索引以生效"));
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        int count = indexService.buildIndex();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "documentCount", count,
            "message", "索引重建完成，共处理 " + count + " 篇文档"
        ));
    }

    @GetMapping("/chunks/{docId}")
    public ResponseEntity<Map<String, Object>> previewChunks(@PathVariable String docId) {
        // MVP 阶段返回简单的文件信息（Chroma 查询需要额外设计），后续可增强
        Path file = policiesPath.resolve(docId).normalize();
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "docId", docId,
            "message", "Chunk 预览功能将通过 Chroma 查询实现 (待增强)"
        ));
    }
}
