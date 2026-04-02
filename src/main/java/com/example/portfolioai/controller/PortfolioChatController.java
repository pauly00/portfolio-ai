package com.example.portfolioai.controller;

import com.example.portfolioai.dto.ChatRequestDto;
import com.example.portfolioai.service.PortfolioChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Portfolio Chat", description = "포트폴리오 AI 어시스턴트 채팅 API")
public class PortfolioChatController {

    private final PortfolioChatService chatService;

    @Operation(summary = "포트폴리오 AI 채팅 (스트리밍)", description = "SSE로 토큰 단위 응답과 참고 출처를 스트리밍합니다.")
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequestDto request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("message")
                    .data("질문을 입력해주세요.")
                    .build());
        }

        log.info("채팅 요청 - message: {}", request.getMessage());
        return chatService.streamChat(request.getMessage());
    }
}
