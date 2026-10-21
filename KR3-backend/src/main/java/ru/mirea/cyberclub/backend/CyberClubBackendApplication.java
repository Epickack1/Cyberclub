package ru.mirea.cyberclub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Точка входа backend киберспортивного клуба. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CyberClubBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CyberClubBackendApplication.class, args);
    }
}
