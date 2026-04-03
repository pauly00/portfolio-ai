package com.example.portfolioai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioDataLoader {

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    private static final String CONTENT_JS_URL =
            "https://api.github.com/repos/pauly00/portfolio/contents/src/data/content.js";

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("Accept", "application/vnd.github.v3+json")
            .defaultHeader("User-Agent", "portfolio-ai")
            .build();

    @PostConstruct
    public void load() {
        try {
            List<Document> existing = vectorStore.similaritySearch(
                    SearchRequest.builder().query("포트폴리오").topK(1).build()
            );
            if (!existing.isEmpty()) {
                log.info("포트폴리오 데이터 이미 인덱싱됨, 건너뜀");
                return;
            }

            List<Document> documents = new ArrayList<>();
            documents.addAll(loadPortfolioMd());
            documents.addAll(fetchContentJs());

            vectorStore.add(documents);
            log.info("포트폴리오 데이터 인덱싱 완료: {}개 청크", documents.size());

        } catch (Exception e) {
            log.error("포트폴리오 데이터 로드 실패", e);
        }
    }

    // portfolio.md를 ### 항목 단위로 분할 (## 섹션 헤더를 각 청크에 포함)
    private List<Document> loadPortfolioMd() {
        try {
            ClassPathResource resource = new ClassPathResource("data/portfolio.md");
            String content = resource.getContentAsString(StandardCharsets.UTF_8);

            List<Document> documents = new ArrayList<>();
            String[] lines = content.split("\n");

            StringBuilder currentChunk = new StringBuilder();
            String currentH2 = "";

            for (String line : lines) {
                if (line.startsWith("## ")) {
                    // 이전 청크 저장
                    if (!currentChunk.isEmpty()) {
                        documents.add(new Document(currentChunk.toString().trim()));
                    }
                    currentH2 = line;
                    currentChunk = new StringBuilder(line).append("\n");
                } else if (line.startsWith("### ")) {
                    // 이전 청크 저장 (## 헤더만 있던 경우 제외)
                    String prev = currentChunk.toString().trim();
                    if (!prev.isEmpty() && !prev.equals(currentH2.trim())) {
                        documents.add(new Document(prev));
                    }
                    // 새 청크: 부모 ## 헤더 + ### 항목
                    currentChunk = new StringBuilder(currentH2).append("\n").append(line).append("\n");
                } else {
                    currentChunk.append(line).append("\n");
                }
            }
            // 마지막 청크 저장
            if (!currentChunk.isEmpty()) {
                documents.add(new Document(currentChunk.toString().trim()));
            }

            log.info("portfolio.md 로드 완료: {}개 청크", documents.size());
            return documents;
        } catch (Exception e) {
            log.warn("portfolio.md 로드 실패: {}", e.getMessage());
            return List.of();
        }
    }

    // GitHub 레포의 content.js (포트폴리오 사이트 실제 데이터 소스)
    private List<Document> fetchContentJs() {
        try {
            String json = restClient.get()
                    .uri(CONTENT_JS_URL)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(json);
            String encoded = node.path("content").asText("");
            String content = new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);

            log.info("content.js 조회 완료: {}글자", content.length());
            return List.of(new Document("포트폴리오 사이트 데이터 (content.js)\n" + content));
        } catch (Exception e) {
            log.warn("content.js 조회 실패 (portfolio.md 단독 사용): {}", e.getMessage());
            return List.of();
        }
    }
}
