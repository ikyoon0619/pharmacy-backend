package com.example.pharmacy.direction.service;

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

        KakaoApiResponseDto pharmacyResult =
                kakaoSearchService.searchPharmacies(input.latitude(), input.longitude(), RADIUS_METERS);
        if (pharmacyResult == null || pharmacyResult.isEmpty()) {
            return Collections.emptyList();
        }

        List<Direction> directions = pharmacyResult.documents().stream()
                .limit(MAX_SEARCH_COUNT)
                .map(pharmacy -> Direction.builder()
                        .inputAddress(input.addressName())
                        .inputLatitude(input.latitude())
                        .inputLongitude(input.longitude())
                        .targetPharmacyName(pharmacy.placeName())
                        .targetAddress(pharmacy.addressName())
                        .targetLatitude(pharmacy.latitude())
                        .targetLongitude(pharmacy.longitude())
                        .distanceKm(toKm(pharmacy.distance()))
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