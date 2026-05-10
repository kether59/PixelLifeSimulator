package com.kether.pixellife.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PixelLifeApplication {

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of(System.getProperty("user.home"), ".pixellife");
        Files.createDirectories(dataDir);

        SpringApplication.run(PixelLifeApplication.class, args);
    }
}
 