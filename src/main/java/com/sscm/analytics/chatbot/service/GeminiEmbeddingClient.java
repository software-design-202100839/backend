package com.sscm.analytics.chatbot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Gemini Text Embedding API 직접 호출 클라이언트.
 *
 * Google AI Studio의 OpenAI 호환 API는 chat만 지원하고 embeddings는 미지원.
 * 따라서 Gemini 네이티브 REST API를 직접 호출하여 gemini-embedding-001 모델로 임베딩을 생성한다.
 *
 * API: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent
 * 출력 차원: 768 (gemini-embedding-001 기본값)
 */
@Slf4j
@Component
public class GeminiEmbeddingClient {

    private static final String EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=%s";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public GeminiEmbeddingClient(
            @Value("${GEMINI_API_KEY:}") String apiKey) {
        this.restTemplate = new RestTemplate();
        this.apiKey = apiKey;
    }

    /**
     * 텍스트를 3072차원 벡터로 변환.
     *
     * @param text 임베딩할 텍스트
     * @return 3072차원 float 배열
     */
    public float[] embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY가 설정되지 않아 임베딩을 생성할 수 없습니다.");
            return new float[3072];  // 빈 벡터 반환 (graceful degradation)
        }

        String url = String.format(EMBEDDING_URL, apiKey);

        // 요청 바디: { "model": "models/gemini-embedding-001", "content": { "parts": [{ "text": "..." }] } }
        Map<String, Object> requestBody = Map.of(
                "model", "models/gemini-embedding-001",
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        try {
            EmbeddingResponse response = restTemplate.postForObject(url, requestBody, EmbeddingResponse.class);
            if (response != null && response.embedding() != null && response.embedding().values() != null) {
                List<Double> values = response.embedding().values();
                float[] result = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    result[i] = values.get(i).floatValue();
                }
                return result;
            }
            log.warn("Gemini 임베딩 응답이 비어있습니다.");
            return new float[3072];
        } catch (Exception e) {
            log.error("Gemini 임베딩 API 호출 실패: {}", e.getMessage(), e);
            return new float[3072];
        }
    }

    // ── 응답 DTO ─────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(
            @JsonProperty("embedding") EmbeddingData embedding
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(
            @JsonProperty("values") List<Double> values
    ) {}
}
