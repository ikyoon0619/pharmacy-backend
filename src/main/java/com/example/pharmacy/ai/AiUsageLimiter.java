package com.example.pharmacy.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

// 공개 데모라서 Claude 호출 비용이 끝없이 늘지 않게 막는 장치.
//  - 하루 전체 한도 (모든 사용자 합계)
//  - 하루 IP 당 한도 (한 사람이 다 쓰지 못하게)
// 날짜가 바뀌면(한국 시간 자정) 0 부터 다시 센다.
// 서버 1대 + 메모리 저장이라 재시작하면 초기화된다. 데모 규모에서는 이걸로 충분하다.
@Component
public class AiUsageLimiter {

    private final int dailyLimit;
    private final int perClientDailyLimit;
    private final Clock clock;

    private LocalDate today;
    private int usedToday;
    private final Map<String, Integer> usedByClient = new HashMap<>();

    @Autowired
    public AiUsageLimiter(@Value("${ai.daily-limit:50}") int dailyLimit,
                          @Value("${ai.per-client-daily-limit:5}") int perClientDailyLimit) {
        this(dailyLimit, perClientDailyLimit, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    // 테스트에서 시계를 바꿔 끼우려고 따로 둔 생성자
    AiUsageLimiter(int dailyLimit, int perClientDailyLimit, Clock clock) {
        this.dailyLimit = dailyLimit;
        this.perClientDailyLimit = perClientDailyLimit;
        this.clock = clock;
        this.today = LocalDate.now(clock);
    }

    // 한 번 쓸 수 있으면 횟수를 올리고 true. 한도를 넘었으면 false.
    // 여러 요청이 동시에 들어와도 숫자가 꼬이지 않게 synchronized.
    public synchronized boolean tryAcquire(String clientKey) {
        resetIfNewDay();
        int clientUsed = usedByClient.getOrDefault(clientKey, 0);
        if (usedToday >= dailyLimit || clientUsed >= perClientDailyLimit) {
            return false;
        }
        usedToday++;
        usedByClient.put(clientKey, clientUsed + 1);
        return true;
    }

    public synchronized int remainingToday() {
        resetIfNewDay();
        return Math.max(0, dailyLimit - usedToday);
    }

    private void resetIfNewDay() {
        LocalDate now = LocalDate.now(clock);
        if (!now.equals(today)) {
            today = now;
            usedToday = 0;
            usedByClient.clear();
        }
    }
}
