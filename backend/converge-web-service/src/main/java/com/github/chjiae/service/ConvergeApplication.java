package com.github.chjiae.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.github.chjiae")
@MapperScan("com.github.chjiae.service.mapper")
@EnableScheduling
public class ConvergeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConvergeApplication.class, args);
    }
}
