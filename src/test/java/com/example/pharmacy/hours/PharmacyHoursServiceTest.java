package com.example.pharmacy.hours;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 H2 DB 에 넣고 위치 + 영업시간 검색을 확인한다
@DataJpaTest
class PharmacyHoursServiceTest {

    @Autowired
    private PharmacyHoursRepository repository;

    private PharmacyHoursService service;

    // 기준점: 강남역 근처
    static final double LAT = 37.4979, LNG = 127.0276;

    private static PharmacyHours pharmacy(String id, String name, String tel, double dLat, WeeklyHours hours) {
        return PharmacyHours.builder().hpid(id).name(name).address(name + " 주소").tel(tel)
                .latitude(LAT + dLat).longitude(LNG).weeklyHours(hours.serialize()).build();
    }

    @BeforeEach
    void setUp() {
        // 2026-11-23 월요일 10:22 (한국)
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T01:22:00Z"), ZoneId.of("Asia/Seoul"));
        service = new PharmacyHoursService(repository, clock);
        repository.saveAll(List.of(
                pharmacy("A", "낮약국", "02-111-1111", 0.001, WeeklyHours.empty().set(7, "0900", "1800")),      // 약 110m
                pharmacy("B", "심야약국", "02-222-2222", 0.005, WeeklyHours.empty().set(7, "1900", "0200")),    // 약 560m
                pharmacy("C", "먼심야약국", "02-333-3333", 0.05, WeeklyHours.empty().set(7, "1900", "0200")))); // 약 5.6km
    }

    @Test
    void 일요일_밤_11시에_반경_안에서_연_약국만_가까운_순서로() {
        List<PharmacyHoursService.Nearby> result = service.findOpenNear(LAT, LNG, 3000, 7, 2300, 5);

        assertThat(result).extracting(n -> n.pharmacy().getName()).containsExactly("심야약국");
        assertThat(result.get(0).distanceMeters()).isBetween(500.0, 620.0);
    }

    @Test
    void 반경을_넓히면_먼_약국도_나온다() {
        assertThat(service.findOpenNear(LAT, LNG, 10_000, 7, 2300, 5)).hasSize(2);
    }

    @Test
    void 카카오_약국과_공공데이터_약국을_전화번호나_이름으로_맞춘다() {
        // 전화번호 형식이 달라도 숫자만 비교
        assertThat(service.match("다른이름", "021111111", LAT + 0.001, LNG)).map(PharmacyHours::getHpid).contains("A");
        // 띄어쓰기가 달라도 같은 이름
        assertThat(service.match("심야 약국", null, LAT + 0.0052, LNG)).map(PharmacyHours::getHpid).contains("B");
        // 이름이 같아도 너무 멀면 다른 약국
        assertThat(service.match("심야약국", null, LAT + 0.02, LNG)).isEmpty();
    }

    @Test
    void 지금은_한국_시각_기준() {
        PharmacyHoursService.Now now = service.now();
        assertThat(now.day()).isEqualTo(1);
        assertThat(now.hhmm()).isEqualTo(1022);
        assertThat(service.isAvailable()).isTrue();
    }
}
