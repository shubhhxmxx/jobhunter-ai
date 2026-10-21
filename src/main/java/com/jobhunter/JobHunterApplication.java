package com.jobhunter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JobHunterApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobHunterApplication.class, args);
    }
}
