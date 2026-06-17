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
public class Script {
    private String id;
    private String name;
    private String description;
    private String category;
    private List<Track> tracks;
}
