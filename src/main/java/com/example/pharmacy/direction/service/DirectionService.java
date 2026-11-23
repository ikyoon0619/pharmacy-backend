package com.example.pharmacy.direction.service;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.repository.DirectionRepository;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto.DocumentDto;
import com.example.pharmacy.kakao.service.KakaoSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectionService {

    static final int MAX_SEARCH_COUNT = 3;     // 추천 약국 최대 개수
    static final int RADIUS_METERS = 10_000;   // 반경 10km

    private static final String KAKAO_MAP_DIRECTION_URL = "https://map.kakao.com/link/to/";
    private static final String KAKAO_MAP_ROADVIEW_URL = "https://map.kakao.com/link/roadview/";

    private final KakaoSearchService kakaoSearchService;
    private final DirectionRepository directionRepository;
    private final Base62Service base62Service;

    // 주소 -> 좌표 -> 주변 약국 3곳 -> DB 저장
    @Transactional
    public List<Direction> recommend(String address) {
        KakaoApiResponseDto addressResult = kakaoSearchService.searchAddress(address);
        if (addressResult == null || addressResult.isEmpty()) {
            log.info("주소 검색 결과 없음. address={}", address);
            return Collections.emptyList();
        }
        DocumentDto input = addressResult.documents().get(0);

        return recommendNear(input.addressName(), input.latitude(), input.longitude(),
                RADIUS_METERS, MAX_SEARCH_COUNT);
    }

    // 좌표 -> 주변 약국 count 곳 -> DB 저장.
    // 주소 폼과 AI 자연어 검색이 함께 쓴다. (AI 는 장소 이름으로 좌표를 먼저 구한다)
    @Transactional
    public List<Direction> recommendNear(String inputLabel, double latitude, double longitude,
                                         int radiusMeters, int count) {
        KakaoApiResponseDto pharmacyResult =
                kakaoSearchService.searchPharmacies(latitude, longitude, radiusMeters);
        if (pharmacyResult == null || pharmacyResult.isEmpty()) {
            return Collections.emptyList();
        }

        return saveDirections(inputLabel, latitude, longitude, pharmacyResult.documents(), count);
    }

    // 약국 목록 -> Direction 저장 (짧은 길안내 URL 을 만들려면 id 가 필요하다).
    // 카카오 검색 결과와 영업시간 데이터(공공데이터) 검색 결과가 함께 쓴다.
    @Transactional
    public List<Direction> saveDirections(String inputLabel, double latitude, double longitude,
                                          List<DocumentDto> pharmacies, int count) {
        List<Direction> directions = pharmacies.stream()
                .limit(count)
                .map(pharmacy -> Direction.builder()
                        .inputAddress(inputLabel)
                        .inputLatitude(latitude)
                        .inputLongitude(longitude)
                        .targetPharmacyName(pharmacy.placeName())
                        .targetAddress(pharmacy.addressName())
                        .targetLatitude(pharmacy.latitude())
                        .targetLongitude(pharmacy.longitude())
                        .distanceKm(toKm(pharmacy.distance()))
                        .targetPhone(pharmacy.phone())
                        .targetPlaceUrl(pharmacy.placeUrl())
                        .build())
                .toList();

        return directionRepository.saveAll(directions);
    }

    // 짧은 코드 -> 카카오맵 길안내 URL
    @Transactional(readOnly = true)
    public String findDirectionUrl(String encodedId) {
        long id = base62Service.decode(encodedId);
        Direction direction = directionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("길안내 정보를 찾을 수 없습니다: " + encodedId));

        String params = String.join(",",
                direction.getTargetPharmacyName(),
                String.valueOf(direction.getTargetLatitude()),
                String.valueOf(direction.getTargetLongitude()));
        return UriComponentsBuilder.fromHttpUrl(KAKAO_MAP_DIRECTION_URL + params)
                .encode().build().toUriString();
    }

    // 결과 화면 한 줄 (주소 검색과 AI 검색이 같이 쓴다)
    public OutputDto toOutput(Direction d) {
        return new OutputDto(
                d.getTargetPharmacyName(),
                d.getTargetAddress(),
                formatDistance(d.getDistanceKm()),
                shortUrlPath(d),
                roadViewUrl(d),
                d.getTargetPhone() == null || d.getTargetPhone().isBlank() ? null : d.getTargetPhone(),
                d.getTargetPlaceUrl());
    }

    // 1km 미만은 m 로 보여 준다. 예: 0.18 -> "180m", 1.234 -> "1.2km"
    static String formatDistance(double km) {
        if (km < 1) {
            return Math.round(km * 1000) + "m";
        }
        return String.format("%.1fkm", km);
    }

    public String shortUrlPath(Direction direction) {
        return "/dir/" + base62Service.encode(direction.getId());
    }

    public String roadViewUrl(Direction direction) {
        return KAKAO_MAP_ROADVIEW_URL + direction.getTargetLatitude() + "," + direction.getTargetLongitude();
    }

    // 카카오는 거리를 "미터 문자열"로 준다. 예: "532" -> 0.53
    private double toKm(String meters) {
        if (meters == null || meters.isBlank()) {
            return 0;
        }
        return Math.round(Double.parseDouble(meters) / 10.0) / 100.0;
    }
}