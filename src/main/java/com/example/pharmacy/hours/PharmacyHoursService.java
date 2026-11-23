package com.example.pharmacy.hours;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// 영업시간 데이터 조회.
//  1) 어떤 좌표 근처에서 "그 요일, 그 시각에 연" 약국 찾기 (AI 도구 search_open_pharmacies)
//  2) 카카오 검색 결과 약국 한 곳의 영업시간 찾기 (이름/전화번호/좌표로 맞춘다)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PharmacyHoursService {

    static final double MATCH_METERS = 120;  // 카카오와 공공데이터 좌표가 이 정도는 어긋날 수 있다

    private final PharmacyHoursRepository repository;
    private final Clock clock;

    public boolean isAvailable() {
        return repository.count() > 0;
    }

    public record Nearby(PharmacyHours pharmacy, double distanceMeters) {
    }

    public List<Nearby> findOpenNear(double lat, double lng, int radiusMeters, int day, int hhmm, int count) {
        return within(lat, lng, radiusMeters).stream()
                .filter(n -> n.pharmacy().hours().isOpen(day, hhmm))
                .limit(count)
                .toList();
    }

    // 이름 + 전화번호 + 좌표로 같은 약국을 찾는다. 확실하지 않으면 비워 둔다 (틀린 영업시간을 보여 주는 것보다 낫다)
    public Optional<PharmacyHours> match(String name, String tel, double lat, double lng) {
        String telDigits = digits(tel);
        String normName = normalize(name);
        return within(lat, lng, (int) MATCH_METERS).stream()
                .map(Nearby::pharmacy)
                .filter(p -> (!telDigits.isEmpty() && telDigits.equals(digits(p.getTel())))
                        || (!normName.isEmpty() && normName.equals(normalize(p.getName()))))
                .findFirst();
    }

    // 지금 한국 시각 기준 요일 번호(1=월 ... 7=일)와 HHMM
    public Now now() {
        LocalDateTime t = LocalDateTime.now(clock);
        return new Now(t.getDayOfWeek().getValue(), t.getHour() * 100 + t.getMinute(), t);
    }

    public record Now(int day, int hhmm, LocalDateTime dateTime) {
    }

    // 반경 안의 약국을 가까운 순서로. DB 에서는 사각형으로 좁히고, 정확한 거리는 여기서 계산한다
    private List<Nearby> within(double lat, double lng, int radiusMeters) {
        double dLat = radiusMeters / 111_320.0;
        double dLng = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(lat)));
        return repository.findByLatitudeBetweenAndLongitudeBetween(lat - dLat, lat + dLat, lng - dLng, lng + dLng)
                .stream()
                .map(p -> new Nearby(p, distanceMeters(lat, lng, p.getLatitude(), p.getLongitude())))
                .filter(n -> n.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(Nearby::distanceMeters))
                .toList();
    }

    // 하버사인 공식: 지구를 구로 보고 두 좌표 사이 거리(m)
    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    static String digits(String tel) {
        return tel == null ? "" : tel.replaceAll("\\D", "");
    }

    // "온누리 강남약국" 과 "온누리강남약국" 을 같게 본다
    static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s", "").replace("(주)", "");
    }
}
