package com.example.pharmacy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @CreatedDate, @LastModifiedDate 가 동작하도록 켜 준다
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}