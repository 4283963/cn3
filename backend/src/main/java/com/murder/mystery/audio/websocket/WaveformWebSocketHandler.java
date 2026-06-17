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
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
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
                .map(tick -> session.textMessage(
                        "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}"))
                .onErrorResume(ex -> {
                    log.warn("心跳流异常, 跳过该次: {}", ex.getMessage());
                    return Flux.empty();
                })
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(500))
                        .doBeforeRetry(s -> log.warn("心跳流重试 #{}: {}",
                                s.totalRetries() + 1, s.failure().getMessage())));

        Flux<WebSocketMessage> waveformStream = audioService.getWaveformStream()
                .buffer(Duration.ofMillis(50))
                .filter(list -> list != null && !list.isEmpty())
                .concatMap(this::safeSerializeWaveforms)
                .onErrorContinue((ex, obj) ->
                        log.warn("波形批次处理异常, 已跳过: {}", ex.getMessage()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(1))
                        .doBeforeRetry(s -> log.warn("波形发送流重试 #{}: {}",
                                s.totalRetries() + 1, s.failure().getMessage())));

        Flux<WebSocketMessage> mergedOutput = Flux.merge(
                        waveformStream.onErrorResume(ex -> {
                            log.error("波形流最终异常, 降级为空流等待恢复: {}", ex.getMessage());
                            return Flux.never();
                        }),
                        heartbeat.onErrorResume(ex -> {
                            log.error("心跳流最终异常: {}", ex.getMessage());
                            return Flux.never();
                        })
                )
                .onBackpressureDrop(msg ->
                        log.trace("WebSocket 发送背压, 丢弃单条消息"));

        Mono<Void> output = session.send(mergedOutput)
                .doOnError(error -> log.error("WebSocket 发送错误: {}", error.getMessage()))
                .doFinally(signal -> {
                    sessions.remove(sessionId);
                    log.info("WebSocket 连接关闭: {}, 信号: {}", sessionId, signal);
                });

        Mono<Void> input = session.receive()
                .doOnNext(message -> log.debug("收到客户端消息: {}", message.getPayloadAsText()))
                .doOnError(ex -> log.warn("WebSocket 接收异常: {}", ex.getMessage()))
                .onErrorResume(ex -> Flux.empty())
                .then();

        return Mono.zipDelayError(input, output).then();
    }

    private Flux<WebSocketMessage> safeSerializeWaveforms(List<WaveformData> waveforms) {
        try {
            if (waveforms == null || waveforms.isEmpty()) return Flux.empty();
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "waveform",
                    "data", waveforms
            ));
            return Flux.just(new WebSocketMessage(WebSocketMessage.Type.TEXT,
                    java.nio.ByteBuffer.wrap(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } catch (Exception e) {
            log.warn("序列化波形数据失败: {}", e.getMessage());
            return Flux.empty();
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
