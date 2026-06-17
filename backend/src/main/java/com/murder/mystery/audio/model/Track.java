package com.murder.mystery.audio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Track {
    private String id;
    private String name;
    private String type;
    private String audioUrl;
    private double volume;
    private boolean loop;
    private boolean playing;
}
