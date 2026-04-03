package com.example.portfolioai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioChatService {

    private final OpenAiChatModel chatModel;
    private final VectorStore vectorStore;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 류경록의 포트폴리오 AI 어시스턴트입니다.
            방문자(주로 채용 담당자나 면접관)의 질문에 아래 포트폴리오 정보를 바탕으로 답변합니다.

            [현재 날짜]
            오늘은 %s입니다. 날짜를 기준으로 과거/현재/미래를 정확히 구분하여 답변하세요.

            [출력 형식 - 반드시 지킬 것]
            **, *, #, -, `, > 등 마크다운 기호를 절대 사용하지 마세요.
            나열이 필요할 경우 "Java, Spring Boot, Docker" 처럼 쉼표로 연결하거나 자연스러운 문장으로 작성하세요.

            [답변 규칙]
            아래 포트폴리오 정보에 있는 내용만 답변하세요.
            정보에 없는 내용은 "해당 내용은 포트폴리오에 포함되어 있지 않습니다."라고 말하고 답변을 마치세요.
            추측하거나 없는 내용을 만들어 내지 마세요.
            자연스러운 한국어로 3문장 이내로 간결하게 답변하세요.

            [포트폴리오 정보]
            %s
            """;

    public Flux<ServerSentEvent<String>> streamChat(String userMessage) {
        // VectorStore.similaritySearch()는 내부적으로 blocking HTTP 호출을 사용하므로
        // Netty 이벤트 루프 스레드에서 직접 호출 불가 → boundedElastic 스레드로 오프로드
        return Mono.fromCallable(() ->
                        vectorStore.similaritySearch(
                                SearchRequest.builder().query(userMessage).topK(3).build()
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(relevantDocs -> buildChatStream(userMessage, relevantDocs))
                .doOnError(e -> log.error("스트리밍 오류: {}", e.getMessage()));
    }

    private Flux<ServerSentEvent<String>> buildChatStream(String userMessage, List<Document> relevantDocs) {
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("검색된 청크 수: {}", relevantDocs.size());

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(today, context);

        Prompt prompt = new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)),
                ChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.7)
                        .build()
        );

        return chatModel.stream(prompt)
                .mapNotNull(ChatResponse::getResult)
                .mapNotNull(result -> result.getOutput().getText())
                .filter(text -> !text.isBlank())
                .map(token -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(token)
                        .build());
    }
}
