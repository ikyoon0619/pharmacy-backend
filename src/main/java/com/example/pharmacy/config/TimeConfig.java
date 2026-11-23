package com.example.pharmacy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.ZoneId;

// "지금 몇 시?" 는 서버 시간대와 상관없이 한국 시간으로 본다 (Render 서버는 UTC).
// Clock 을 빈으로 두면 테스트에서 원하는 시각으로 바꿔 끼울 수 있다.
@Configuration
@EnableAsync        // 영업시간 데이터 불러오기를 백그라운드로
@EnableScheduling   // 매일 새벽 다시 불러오기
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    // SSE 로 AI 답변을 흘려보내는 작업용 스레드.
    // 요청 스레드(Tomcat)는 SseEmitter 를 돌려주고 바로 풀려나고, 실제 Claude 호출은 여기서 돈다.
    // 무료 서버(메모리 512MB)라 작게 잡고, 꽉 차면 TaskRejectedException 으로 "잠시 후 다시" 안내한다.
    @Bean
    public ThreadPoolTaskExecutor aiStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-stream-");
        return executor;
    }
}
