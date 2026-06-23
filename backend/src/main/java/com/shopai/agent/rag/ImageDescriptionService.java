package com.shopai.agent.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts images from PDF pages and generates Chinese text descriptions
 * via the DeepSeek Vision API (multimodal chat). The descriptions are embedded
 * alongside text chunks so that image content becomes searchable.
 *
 * <p>Only processes PDF files; other formats (DOCX etc.) pass through unchanged.
 * Non-blocking: if the Vision API call fails, indexing continues without image
 * descriptions — a warning is logged but no exception is thrown.</p>
 */
@Component
public class ImageDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(ImageDescriptionService.class);

    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxImageSizeKb;
    private final int maxPagesToAnalyze;
    private final RestTemplate restTemplate;

    public ImageDescriptionService(
        @Value("${shopai.rag.vision.enabled:true}") boolean enabled,
        @Value("${shopai.rag.vision.api-key}") String apiKey,
        @Value("${shopai.rag.vision.base-url:https://api.deepseek.com/v1}") String baseUrl,
        @Value("${shopai.rag.vision.model:deepseek-v2.5-vision}") String model,
        @Value("${shopai.rag.vision.max-image-size-kb:1024}") int maxImageSizeKb,
        @Value("${shopai.rag.vision.max-pages-to-analyze:10}") int maxPagesToAnalyze
    ) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxImageSizeKb = maxImageSizeKb;
        this.maxPagesToAnalyze = maxPagesToAnalyze;
        this.restTemplate = new RestTemplate();
        log.info("ImageDescriptionService: enabled={}, model={}, maxPages={}, maxImageSize={}KB",
            enabled, model, maxPagesToAnalyze, maxImageSizeKb);
    }

    /**
     * Describes images in a PDF file. Returns a list of page-numbered descriptions.
     * Non-PDF files return an empty list (not an error).
     */
    public List<ImageDescription> describe(Path filePath) {
        if (!enabled) {
            return Collections.emptyList();
        }
        String filename = filePath.getFileName().toString();
        if (!filename.toLowerCase().endsWith(".pdf")) {
            return Collections.emptyList();
        }

        List<ImageDescription> descriptions = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            int totalPages = doc.getNumberOfPages();
            int pagesToAnalyze = Math.min(totalPages, maxPagesToAnalyze);
            if (pagesToAnalyze < totalPages) {
                log.info("PDF has {} pages, analyzing first {} (limit: max-pages-to-analyze)",
                    totalPages, pagesToAnalyze);
            }

            PDFRenderer renderer = new PDFRenderer(doc);
            for (int page = 0; page < pagesToAnalyze; page++) {
                try {
                    String description = describePage(renderer, page);
                    if (description != null && !description.isBlank()) {
                        descriptions.add(new ImageDescription(page + 1, description));
                        // Small delay between API calls to avoid rate limiting
                        if (page < pagesToAnalyze - 1) {
                            Thread.sleep(300);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to describe page {} of {}: {}", page + 1, filename, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Image extraction failed for {}: {}", filename, e.getMessage());
        }

        if (!descriptions.isEmpty()) {
            log.info("Extracted {} image descriptions from {}", descriptions.size(), filename);
        }
        return descriptions;
    }

    private String describePage(PDFRenderer renderer, int pageIndex) throws Exception {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150);
        byte[] imageBytes = bufferedImageToBytes(image);

        if (imageBytes.length > maxImageSizeKb * 1024L) {
            log.debug("Page {} image too large ({}KB > {}KB limit), skipping",
                pageIndex + 1, imageBytes.length / 1024, maxImageSizeKb);
            return null;
        }

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return callVisionApi(base64);
    }

    private String callVisionApi(String base64Image) {
        try {
            Map<String, Object> textPart = Map.of("type", "text", "text",
                "请用中文详细描述这张图片的内容。如果包含表格，请描述表格的结构和数据；"
                + "如果包含图表，请说明图表的类型、趋势和关键数据点；"
                + "如果包含流程图，请逐步描述流程；"
                + "如果是截图，请描述界面上的关键信息和操作步骤。"
                + "尽量详细，以便后续文本检索能匹配到。");

            Map<String, Object> imagePart = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/png;base64," + base64Image)
            );

            Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(textPart, imagePart)
            );

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model);
            request.put("messages", List.of(message));
            request.put("max_tokens", 500);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/chat/completions",
                new HttpEntity<>(request, headers),
                Map.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    if (msg != null) {
                        String content = (String) msg.get("content");
                        if (content != null && !content.isBlank()) {
                            return content.trim();
                        }
                    }
                }
            }
            log.debug("Vision API returned empty content");
            return null;
        } catch (Exception e) {
            log.warn("Vision API call failed: {}", e.getMessage());
            return null;
        }
    }

    private byte[] bufferedImageToBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    /** A page-numbered image description to be embedded alongside document text. */
    public record ImageDescription(int pageNumber, String description) {
        public String toIndexableText() {
            return String.format("[第%d页图片描述] %s", pageNumber, description);
        }
    }
}
