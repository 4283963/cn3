package com.murder.mystery.audio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
@EnableScheduling
public class AudioControlApplication {

    public static void main(String[] args) {
        SpringApplication.run(AudioControlApplication.class, args);
    }
}
