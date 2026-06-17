package com.murder.mystery.audio.controller;

import com.murder.mystery.audio.model.PlaybackState;
import com.murder.mystery.audio.model.Script;
import com.murder.mystery.audio.service.AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/audio")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AudioController {

    private final AudioService audioService;

    @GetMapping("/scripts")
    public Flux<Script> getAllScripts() {
        return audioService.getAllScripts();
    }

    @GetMapping("/scripts/{id}")
    public Mono<Script> getScriptById(@PathVariable String id) {
        return audioService.getScriptById(id);
    }

    @PostMapping("/play/{scriptId}")
    public Mono<PlaybackState> playScript(@PathVariable String scriptId) {
        return audioService.playScript(scriptId);
    }

    @PostMapping("/pause")
    public Mono<PlaybackState> pauseScript() {
        return audioService.pauseScript();
    }

    @PostMapping("/resume")
    public Mono<PlaybackState> resumeScript() {
        return audioService.resumeScript();
    }

    @PostMapping("/stop")
    public Mono<PlaybackState> stopScript() {
        return audioService.stopScript();
    }

    @GetMapping("/state")
    public Mono<PlaybackState> getPlaybackState() {
        return audioService.getPlaybackState();
    }

    @PostMapping("/tracks/{trackId}/volume")
    public Mono<PlaybackState> setTrackVolume(
            @PathVariable String trackId,
            @RequestBody Map<String, Double> body) {
        double volume = body.getOrDefault("volume", 0.5);
        return audioService.setTrackVolume(trackId, volume);
    }

    @PostMapping("/tracks/{trackId}/toggle")
    public Mono<PlaybackState> toggleTrack(
            @PathVariable String trackId,
            @RequestBody Map<String, Boolean> body) {
        boolean play = body.getOrDefault("play", true);
        return audioService.toggleTrack(trackId, play);
    }
}
