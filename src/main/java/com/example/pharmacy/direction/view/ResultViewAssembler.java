package com.example.pharmacy.direction.view;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.service.DirectionService;
import com.example.pharmacy.hours.PharmacyHoursService;
import com.example.pharmacy.hours.WeeklyHours;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 저장된 추천 결과(Direction) -> 화면 카드(OutputDto).
// 영업시간 데이터가 있으면 "오늘 09:00~22:00 · 영업 중" 을 붙인다.
@Component
@RequiredArgsConstructor
public class ResultViewAssembler {

    private final DirectionService directionService;
    private final PharmacyHoursService hoursService;

    public List<OutputDto> toOutputs(List<Direction> directions) {
        PharmacyHoursService.Now now = hoursService.now();
        return directions.stream().map(d -> toOutput(d, now)).toList();
    }

    private OutputDto toOutput(Direction d, PharmacyHoursService.Now now) {
        OutputDto base = directionService.toOutput(d);
        return hoursService.match(d.getTargetPharmacyName(), d.getTargetPhone(),
                        d.getTargetLatitude(), d.getTargetLongitude())
                .map(p -> p.hours())
                .filter(hours -> !hours.isEmpty())
                .map(hours -> {
                    String label = "오늘(" + WeeklyHours.dayName(now.day()) + ") " + hours.label(now.day());
                    // 오늘 영업시간 칸이 비어 있으면 쉬는 날인지 정보가 없는 건지 알 수 없어서 "모름"(null) 으로 둔다
                    Boolean open = hours.isOpen(now.day(), now.hhmm()) ? Boolean.TRUE
                            : hours.hasDay(now.day()) ? Boolean.FALSE : null;
                    return base.withHours(label, open);
                })
                .orElse(base);
    }
}
