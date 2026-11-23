package com.example.pharmacy.direction.view;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.service.DirectionService;
import com.example.pharmacy.hours.PharmacyHours;
import com.example.pharmacy.hours.PharmacyHoursService;
import com.example.pharmacy.hours.WeeklyHours;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ResultViewAssemblerTest {

    @Mock
    private DirectionService directionService;
    @Mock
    private PharmacyHoursService hoursService;
    @InjectMocks
    private ResultViewAssembler assembler;

    private final Direction d = Direction.builder().id(1L).targetPharmacyName("온누리약국").build();
    private final OutputDto base = new OutputDto("온누리약국", "주소", "180m", "/dir/1", "rv", null, null);

    private void at(int day, int hhmm) {
        given(hoursService.now()).willReturn(new PharmacyHoursService.Now(day, hhmm, LocalDateTime.now()));
        given(directionService.toOutput(d)).willReturn(base);
    }

    private void hours(WeeklyHours h) {
        given(hoursService.match(any(), any(), anyDouble(), anyDouble())).willReturn(Optional.of(
                PharmacyHours.builder().hpid("x").weeklyHours(h.serialize()).build()));
    }

    @Test
    void 영업시간이_맞으면_오늘_시간과_영업_중을_붙인다() {
        at(1, 1000);
        hours(WeeklyHours.empty().set(1, "0900", "2200"));

        OutputDto out = assembler.toOutputs(List.of(d)).get(0);

        assertThat(out.hoursToday()).isEqualTo("오늘(월) 09:00~22:00");
        assertThat(out.openNow()).isTrue();
    }

    @Test
    void 오늘_칸이_비어_있으면_영업_여부는_모름() {
        at(3, 1000);
        hours(WeeklyHours.empty().set(1, "0900", "2200"));

        OutputDto out = assembler.toOutputs(List.of(d)).get(0);

        assertThat(out.hoursToday()).isEqualTo("오늘(수) 휴무 또는 정보 없음");
        assertThat(out.openNow()).isNull();
    }

    @Test
    void 맞는_약국이_없으면_영업시간_없이() {
        at(1, 1000);
        given(hoursService.match(any(), any(), anyDouble(), anyDouble())).willReturn(Optional.empty());

        OutputDto out = assembler.toOutputs(List.of(d)).get(0);

        assertThat(out.hoursToday()).isNull();
        assertThat(out.openNow()).isNull();
    }
}
