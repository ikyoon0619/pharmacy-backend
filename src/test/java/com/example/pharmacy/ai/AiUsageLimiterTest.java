package com.example.pharmacy.ai;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageLimiterTest {

    // 테스트에서 시간을 앞으로 돌릴 수 있는 시계
    static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-22T03:00:00Z"); // 한국 시간 낮 12시

        void plusHours(long hours) {
            now = now.plus(hours, ChronoUnit.HOURS);
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void 하루_전체_한도를_넘으면_거절한다() {
        AiUsageLimiter limiter = new AiUsageLimiter(3, 10, Clock.fixed(Instant.now(), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("c")).isTrue();
        assertThat(limiter.tryAcquire("d")).isFalse();
        assertThat(limiter.remainingToday()).isZero();
    }

    @Test
    void 한_사용자는_IP당_한도까지만_쓴다() {
        AiUsageLimiter limiter = new AiUsageLimiter(100, 2, Clock.fixed(Instant.now(), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue(); // 다른 사람은 쓸 수 있다
    }

    @Test
    void 한국_시간_자정이_지나면_다시_쓸_수_있다() {
        MovableClock clock = new MovableClock();
        AiUsageLimiter limiter = new AiUsageLimiter(1, 1, clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.plusHours(11); // 한국 시간 밤 11시 (같은 날)
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.plusHours(2);  // 다음 날 새벽 1시
        assertThat(limiter.tryAcquire("a")).isTrue();
    }
}
