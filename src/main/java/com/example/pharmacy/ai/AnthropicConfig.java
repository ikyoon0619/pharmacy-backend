package com.example.pharmacy.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Claude API 클라이언트.
// ANTHROPIC_API_KEY 환경변수가 있을 때만 만든다. 없으면 AI 검색만 꺼지고 주소 검색은 그대로 동작한다.
@Configuration
public class AnthropicConfig {

    @Bean
    @ConditionalOnExpression("!'${anthropic.api-key:}'.isBlank()")
    public AnthropicClient anthropicClient(@Value("${anthropic.api-key}") String apiKey) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(30)) // 화면에서 기다리는 요청이라 너무 길게 잡지 않는다
                .maxRetries(1)
                .build();
    }
}
