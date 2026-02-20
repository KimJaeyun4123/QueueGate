package com.study.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class QueueGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueGateApplication.class, args);
    }

}
