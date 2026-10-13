package com.aives;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AivesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AivesApplication.class, args);
    }
}
