package com.murder.mystery.audio.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.murder.mystery.audio.model.WaveformData;
import com.murder.mystery.audio.service.AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaveformWebSocketHandler implements WebSocketHandler {

    private final AudioService audioService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket 连接建立: {}", sessionId);

        Flux<WebSocketMessage> heartbeat = Flux.interval(Duration.ofSeconds(10))
                .map(tick -> session.textMessage("{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}"));

        Flux<WebSocketMessage> waveformStream = audioService.getWaveformStream()
                .buffer(Duration.ofMillis(50))
                .filter(list -> !list.isEmpty())
                .map(waveforms -> {
                    try {
                        String json = objectMapper.writeValueAsString(Map.of(
                                "type", "waveform",
                                "data", waveforms
                        ));
                        return session.textMessage(json);
                    } catch (Exception e) {
                        log.error("序列化波形数据失败", e);
                        return session.textMessage("{\"type\":\"error\"}");
                    }
                });

        Mono<Void> output = session.send(Flux.merge(waveformStream, heartbeat))
                .doOnError(error -> log.error("WebSocket 发送错误: {}", error.getMessage()))
                .doFinally(signal -> {
                    sessions.remove(sessionId);
                    log.info("WebSocket 连接关闭: {}", sessionId);
                });

        Mono<Void> input = session.receive()
                .doOnNext(message -> {
                    log.debug("收到客户端消息: {}", message.getPayloadAsText());
                })
                .then();

        return Mono.zip(input, output).then();
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
