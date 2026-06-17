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
public class WaveformData {
    private String trackId;
    private List<Double> amplitudes;
    private double currentVolume;
    private long timestamp;
}
