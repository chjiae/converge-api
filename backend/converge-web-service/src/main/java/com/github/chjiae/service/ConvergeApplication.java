package com.github.chjiae.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.github.chjiae")
@MapperScan("com.github.chjiae.service.mapper")
public class ConvergeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConvergeApplication.class, args);
    }
}
