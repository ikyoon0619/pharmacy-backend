package com.example.pharmacy.hours;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

// 전국 약국 영업시간을 공공데이터에서 받아 DB 에 넣는다.
//  - 서버가 뜬 직후 한 번 (백그라운드라 첫 화면은 바로 뜬다)
//  - 매일 새벽 4시(한국) 다시
// 약 2만 5천 곳 / 1000건씩 = 25번 정도 호출. 개발계정 하루 한도 1,000번 안에서 넉넉하다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PharmacyHoursLoader {

    private final NmcPharmacyClient client;
    private final PharmacyHoursRepository repository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${opendata.page-size:1000}")
    private int pageSize = 1000;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        load();
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void reloadDaily() {
        load();
    }

    // 반환값: 저장한 약국 수 (키가 없거나 이미 도는 중이면 -1)
    public int load() {
        if (!client.hasKey()) {
            log.info("DATA_GO_KR_SERVICE_KEY 가 없어 영업시간 데이터를 불러오지 않습니다.");
            return -1;
        }
        if (!running.compareAndSet(false, true)) {
            return -1; // 동시에 두 번 돌지 않게
        }
        long started = System.currentTimeMillis();
        try {
            int page = 1;
            int saved = 0;
            NmcPharmacyClient.Page result;
            do {
                result = client.fetchPage(page, pageSize);
                repository.saveAll(result.items()); // 같은 hpid 면 덮어쓴다
                saved += result.items().size();
                page++;
            } while (!result.items().isEmpty() && (long) (page - 1) * pageSize < result.totalCount() && page <= 100);
            log.info("약국 영업시간 {}곳 저장 ({}ms)", saved, System.currentTimeMillis() - started);
            return saved;
        } catch (RuntimeException e) {
            // 실패해도 서비스는 계속 돈다. 영업시간 기능만 "데이터 없음" 상태가 된다
            log.error("약국 영업시간 불러오기 실패", e);
            return -1;
        } finally {
            running.set(false);
        }
    }
}
