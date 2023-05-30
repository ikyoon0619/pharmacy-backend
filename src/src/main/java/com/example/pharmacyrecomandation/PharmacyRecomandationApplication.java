package com.example.pharmacyrecomandation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PharmacyRecomandationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PharmacyRecomandationApplication.class, args);
    }

}
