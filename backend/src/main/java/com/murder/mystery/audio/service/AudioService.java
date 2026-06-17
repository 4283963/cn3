package com.murder.mystery.audio.service;

import com.murder.mystery.audio.model.PlaybackState;
import com.murder.mystery.audio.model.Script;
import com.murder.mystery.audio.model.Track;
import com.murder.mystery.audio.model.WaveformData;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class AudioService {

    private final Map<String, Script> scripts = new ConcurrentHashMap<>();
    private final Map<String, TrackRuntime> trackRuntimes = new ConcurrentHashMap<>();
    private volatile String currentScriptId;
    private volatile boolean isPlaying = false;

    @PostConstruct
    public void init() {
        Script horrorScript = Script.builder()
                .id("horror-001")
                .name("恐怖氛围")
                .description("阴森恐怖的背景音乐，配合风声和开门声效")
                .category("恐怖")
                .tracks(Arrays.asList(
                        Track.builder()
                                .id("track-bgm")
                                .name("背景音乐")
                                .type("bgm")
                                .audioUrl("/audio/horror-bgm.mp3")
                                .volume(0.6)
                                .loop(true)
                                .playing(false)
                                .build(),
                        Track.builder()
                                .id("track-wind")
                                .name("风声")
                                .type("ambient")
                                .audioUrl("/audio/wind.mp3")
                                .volume(0.4)
                                .loop(true)
                                .playing(false)
                                .build(),
                        Track.builder()
                                .id("track-door")
                                .name("开门声")
                                .type("effect")
                                .audioUrl("/audio/door-creak.mp3")
                                .volume(0.8)
                                .loop(false)
                                .playing(false)
                                .build()
                ))
                .build();

        Script suspenseScript = Script.builder()
                .id("suspense-001")
                .name("悬疑推理")
                .description("紧张悬疑的推理氛围音乐")
                .category("悬疑")
                .tracks(Arrays.asList(
                        Track.builder()
                                .id("track-bgm-sus")
                                .name("悬疑BGM")
                                .type("bgm")
                                .audioUrl("/audio/suspense-bgm.mp3")
                                .volume(0.5)
                                .loop(true)
                                .playing(false)
                                .build(),
                        Track.builder()
                                .id("track-clock")
                                .name("时钟滴答")
                                .type("ambient")
                                .audioUrl("/audio/clock-tick.mp3")
                                .volume(0.3)
                                .loop(true)
                                .playing(false)
                                .build(),
                        Track.builder()
                                .id("track-whisper")
                                .name("低语声")
                                .type("effect")
                                .audioUrl("/audio/whisper.mp3")
                                .volume(0.5)
                                .loop(false)
                                .playing(false)
                                .build()
                ))
                .build();

        scripts.put(horrorScript.getId(), horrorScript);
        scripts.put(suspenseScript.getId(), suspenseScript);
    }

    public Flux<Script> getAllScripts() {
        return Flux.fromIterable(scripts.values());
    }

    public Mono<Script> getScriptById(String scriptId) {
        return Mono.justOrEmpty(scripts.get(scriptId));
    }

    public Mono<PlaybackState> playScript(String scriptId) {
        Script script = scripts.get(scriptId);
        if (script == null) {
            return Mono.error(new IllegalArgumentException("剧本不存在: " + scriptId));
        }

        stopAllTracks();
        currentScriptId = scriptId;
        isPlaying = true;

        for (Track track : script.getTracks()) {
            TrackRuntime runtime = TrackRuntime.builder()
                    .track(track)
                    .playing(true)
                    .currentTime(0)
                    .startTime(System.currentTimeMillis())
                    .phase(ThreadLocalRandom.current().nextDouble(0, Math.PI * 2))
                    .frequency(baseFrequency(track.getType()))
                    .build();
            trackRuntimes.put(track.getId(), runtime);
            log.info("开始播放音轨: {} ({})", track.getName(), track.getId());
        }

        return getPlaybackState();
    }

    private double baseFrequency(String type) {
        return switch (type) {
            case "bgm" -> 0.8;
            case "ambient" -> 1.5;
            case "effect" -> 2.0;
            default -> 1.0;
        };
    }

    public Mono<PlaybackState> pauseScript() {
        isPlaying = false;
        trackRuntimes.values().forEach(runtime -> runtime.setPlaying(false));
        log.info("暂停播放");
        return getPlaybackState();
    }

    public Mono<PlaybackState> resumeScript() {
        isPlaying = true;
        trackRuntimes.values().forEach(runtime -> runtime.setPlaying(true));
        log.info("恢复播放");
        return getPlaybackState();
    }

    public Mono<PlaybackState> stopScript() {
        stopAllTracks();
        log.info("停止播放");
        return getPlaybackState();
    }

    private void stopAllTracks() {
        isPlaying = false;
        trackRuntimes.clear();
        currentScriptId = null;
    }

    public Mono<PlaybackState> setTrackVolume(String trackId, double volume) {
        TrackRuntime runtime = trackRuntimes.get(trackId);
        if (runtime != null) {
            runtime.getTrack().setVolume(Math.max(0, Math.min(1, volume)));
        }
        return getPlaybackState();
    }

    public Mono<PlaybackState> toggleTrack(String trackId, boolean play) {
        TrackRuntime runtime = trackRuntimes.get(trackId);
        if (runtime != null) {
            runtime.setPlaying(play);
            runtime.getTrack().setPlaying(play);
        }
        return getPlaybackState();
    }

    public Mono<PlaybackState> getPlaybackState() {
        List<PlaybackState.TrackState> trackStates = new ArrayList<>();
        for (TrackRuntime runtime : trackRuntimes.values()) {
            Track track = runtime.getTrack();
            trackStates.add(PlaybackState.TrackState.builder()
                    .trackId(track.getId())
                    .trackName(track.getName())
                    .playing(runtime.isPlaying())
                    .volume(track.getVolume())
                    .currentTime(runtime.getCurrentTime())
                    .duration(180.0)
                    .build());
        }

        PlaybackState state = PlaybackState.builder()
                .scriptId(currentScriptId)
                .playing(isPlaying)
                .tracks(trackStates)
                .build();

        return Mono.just(state);
    }

    public Flux<WaveformData> getWaveformStream() {
        return Flux.interval(Duration.ofMillis(50))
                .subscribeOn(Schedulers.parallel())
                .filter(tick -> isPlaying)
                .flatMap(tick -> {
                    List<WaveformData> waveforms = new ArrayList<>();
                    long now = System.currentTimeMillis();

                    for (TrackRuntime runtime : trackRuntimes.values()) {
                        if (!runtime.isPlaying()) continue;

                        double elapsed = (now - runtime.getStartTime()) / 1000.0;
                        runtime.setCurrentTime(elapsed);

                        List<Double> amplitudes = generateWaveform(runtime, 64);
                        double rms = calculateRMS(amplitudes);

                        waveforms.add(WaveformData.builder()
                                .trackId(runtime.getTrack().getId())
                                .amplitudes(amplitudes)
                                .currentVolume(rms * runtime.getTrack().getVolume())
                                .timestamp(now)
                                .build());
                    }

                    return Flux.fromIterable(waveforms);
                });
    }

    private List<Double> generateWaveform(TrackRuntime runtime, int size) {
        List<Double> amplitudes = new ArrayList<>(size);
        double baseVolume = runtime.getTrack().getVolume();
        double phase = runtime.getPhase();
        double freq = runtime.getFrequency();
        long time = System.currentTimeMillis();

        for (int i = 0; i < size; i++) {
            double t = (time / 1000.0) + (i * 0.005);
            double value = 0;

            value += Math.sin(2 * Math.PI * freq * t + phase) * 0.3;
            value += Math.sin(2 * Math.PI * freq * 2 * t + phase * 1.5) * 0.2;
            value += Math.sin(2 * Math.PI * freq * 0.5 * t + phase * 0.7) * 0.25;

            String type = runtime.getTrack().getType();
            if ("ambient".equals(type)) {
                value += (Math.random() - 0.5) * 0.3;
                value *= 0.5 + 0.5 * Math.sin(t * 0.3);
            } else if ("effect".equals(type)) {
                double envelope = Math.sin(Math.PI * (time % 3000) / 3000.0);
                value *= envelope;
                value += (Math.random() - 0.5) * 0.2;
            } else {
                value += (Math.random() - 0.5) * 0.1;
            }

            value = Math.max(-1, Math.min(1, value));
            amplitudes.add(value * baseVolume);
        }

        return amplitudes;
    }

    private double calculateRMS(List<Double> samples) {
        if (samples.isEmpty()) return 0;
        double sum = 0;
        for (double s : samples) {
            sum += s * s;
        }
        return Math.sqrt(sum / samples.size());
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class TrackRuntime {
        private Track track;
        private boolean playing;
        private double currentTime;
        private long startTime;
        private double phase;
        private double frequency;
    }
}
