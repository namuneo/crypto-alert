package com.sungjun.cryptoalert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoAlertApplication.class, args);
    }

}
