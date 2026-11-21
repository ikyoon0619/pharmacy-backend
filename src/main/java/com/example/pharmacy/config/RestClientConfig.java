package com.example.pharmacy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

// 카카오 API 호출용 RestClient (Spring 6.1+ 의 새 HTTP 클라이언트)
@Configuration
@EnableRetry
public class RestClientConfig {

    @Bean
    public RestClient kakaoRestClient(
            RestClient.Builder builder,
            @Value("${kakao.base-url}") String baseUrl,
            @Value("${kakao.rest-api-key}") String restApiKey) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .build();
    }
}