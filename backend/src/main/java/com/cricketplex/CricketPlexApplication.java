package com.cricketplex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CricketPlexApplication {

    public static void main(String[] args) {
        SpringApplication.run(CricketPlexApplication.class, args);
    }
}
