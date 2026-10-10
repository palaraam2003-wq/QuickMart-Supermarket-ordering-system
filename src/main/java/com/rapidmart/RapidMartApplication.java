package com.rapidmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RapidMartApplication {

    public static void main(String[] args) {
        SpringApplication.run(RapidMartApplication.class, args);
    }
}
