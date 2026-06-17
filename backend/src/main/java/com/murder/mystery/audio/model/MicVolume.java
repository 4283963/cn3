package com.murder.mystery.audio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicVolume {
    private String roomId;
    private double volume;
    private double peakVolume;
    private long timestamp;
}
