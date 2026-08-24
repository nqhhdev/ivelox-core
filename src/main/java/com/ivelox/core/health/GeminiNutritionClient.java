package com.ivelox.core.health;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivelox.core.config.IveloxProperties;

@Component
public class GeminiNutritionClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiNutritionClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final IveloxProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GeminiNutritionClient(IveloxProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        String key = props.geminiApiKey();
        return key != null && !key.isBlank();
    }

    public HealthModels.ResolveResult resolveText(String text, Double quantity, String unit) {
        List<Map<String, Object>> parts = List.of(Map.of("text", NutritionJsonParser.buildTextPrompt(text, quantity, unit)));
        return generate(parts);
    }

    public HealthModels.ResolveResult resolveImage(byte[] imageBytes, String mime, String hint, Double quantity, String unit) {
        String mimeType = (mime == null || mime.isBlank()) ? "image/jpeg" : mime;
        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of(
                        "mime_type", mimeType,
                        "data", Base64.getEncoder().encodeToString(imageBytes)
                )
        );
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(imagePart);
        parts.add(Map.of("text", NutritionJsonParser.buildImagePrompt(hint, quantity, unit)));
        return generate(parts);
    }

    private HealthModels.ResolveResult generate(List<Map<String, Object>> parts) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "nutrition resolver unavailable");
        }
        String raw = generateRaw(parts);
        try {
            return NutritionJsonParser.parse(mapper, raw);
        } catch (Exception first) {
            String repair = "The previous response was not valid JSON matching the nutrition schema. "
                    + "Fix JSON only. Return only the corrected JSON object, no markdown.\n\nPrevious response:\n"
                    + raw;
            String repaired = generateRaw(List.of(Map.of("text", repair)));
            try {
                return NutritionJsonParser.parse(mapper, repaired);
            } catch (Exception e) {
                log.warn("gemini nutrition json parse failed: {}", e.toString());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "nutrition resolver unavailable");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String generateRaw(List<Map<String, Object>> parts) {
        try {
            String model = props.geminiModel() == null || props.geminiModel().isBlank()
                    ? "gemini-2.5-flash"
                    : props.geminiModel();
            String key = URLEncoder.encode(props.geminiApiKey(), StandardCharsets.UTF_8);
            URI uri = URI.create(
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                            + model
                            + ":generateContent?key="
                            + key
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(Map.of("parts", parts)));
            body.put("generationConfig", Map.of(
                    "temperature", 0.1,
                    "responseMimeType", "application/json"
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                log.warn("gemini generate failed model={} status={} body={}", model, res.statusCode(), truncate(res.body()));
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "nutrition resolver unavailable");
            }
            Map<String, Object> parsed = mapper.readValue(res.body(), Map.class);
            String text = NutritionJsonParser.extractCandidateText(parsed);
            if (text == null || text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "nutrition resolver unavailable");
            }
            return text;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("gemini generate error", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "nutrition resolver unavailable");
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
