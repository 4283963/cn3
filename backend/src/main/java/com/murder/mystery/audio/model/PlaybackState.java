package com.murder.mystery.audio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackState {
    private String scriptId;
    private boolean playing;
    private List<TrackState> tracks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackState {
        private String trackId;
        private String trackName;
        private boolean playing;
        private double volume;
        private double currentTime;
        private double duration;
    }
}
