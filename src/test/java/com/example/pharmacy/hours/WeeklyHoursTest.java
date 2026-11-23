package com.example.pharmacy.hours;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyHoursTest {

    @Test
    void 여는_시간_안이면_영업_중() {
        WeeklyHours h = WeeklyHours.empty().set(1, "0900", "1800");

        assertThat(h.isOpen(1, 900)).isTrue();
        assertThat(h.isOpen(1, 1759)).isTrue();
        assertThat(h.isOpen(1, 1800)).isFalse();   // 닫는 시각은 포함하지 않는다
        assertThat(h.isOpen(2, 1000)).isFalse();   // 화요일 정보 없음
    }

    @Test
    void 자정을_넘기는_영업은_다음날_새벽까지_본다() {
        WeeklyHours a = WeeklyHours.empty().set(7, "1900", "0200");   // 일요일 19:00 ~ 월요일 02:00
        WeeklyHours b = WeeklyHours.empty().set(7, "1900", "2600");   // 같은 뜻을 2600 으로 쓰는 경우

        for (WeeklyHours h : new WeeklyHours[]{a, b}) {
            assertThat(h.isOpen(7, 2330)).isTrue();
            assertThat(h.isOpen(1, 130)).isTrue();    // 월요일 01:30 은 일요일 영업의 연장
            assertThat(h.isOpen(1, 230)).isFalse();
            assertThat(h.label(7)).isEqualTo("19:00~다음날 02:00");
        }
    }

    @Test
    void 일요일_밤이_월요일_새벽으로_이어진다_요일_경계() {
        WeeklyHours h = WeeklyHours.empty().set(7, "2200", "0100");
        assertThat(h.isOpen(1, 30)).isTrue();
    }

    @Test
    void 저장용_문자열로_바꿨다가_다시_읽어도_같다() {
        WeeklyHours h = WeeklyHours.empty().set(1, "0900", "1800").set(8, "1000", "1500");

        WeeklyHours back = WeeklyHours.deserialize(h.serialize());

        assertThat(back.serialize()).isEqualTo(h.serialize());
        assertThat(back.label(8)).isEqualTo("10:00~15:00");
        assertThat(back.label(3)).isEqualTo("휴무 또는 정보 없음");
    }

    @Test
    void 시각_문자열을_읽는다() {
        assertThat(WeeklyHours.parseTime("2300")).isEqualTo(2300);
        assertThat(WeeklyHours.parseTime("23:00")).isEqualTo(2300);
        assertThat(WeeklyHours.parseTime("0130")).isEqualTo(130);
        assertThat(WeeklyHours.parseTime("2575")).isNull();
        assertThat(WeeklyHours.parseTime("밤")).isNull();
    }
}
