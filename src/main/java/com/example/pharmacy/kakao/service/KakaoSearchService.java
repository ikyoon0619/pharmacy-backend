package com.example.pharmacy.kakao.service;

import com.example.pharmacy.kakao.dto.KakaoApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoSearchService {

    private static final String PHARMACY_CATEGORY = "PM9"; // 카카오 카테고리 코드: 약국

    private final RestClient kakaoRestClient;

    // 1) 주소 -> 위도/경도.
    //    네트워크 오류나 카카오 서버 오류(5xx)면 최대 2번 더 시도한다.
    //    키가 틀린 401 같은 4xx 는 다시 해도 같으니 재시도하지 않는다.
    @Retryable(retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 500))
    public KakaoApiResponseDto searchAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        return kakaoRestClient.get()
                .uri(uri -> uri.path("/v2/local/search/address.json")
                        .queryParam("query", address)
                        .build())
                .retrieve()
                .body(KakaoApiResponseDto.class);
    }

    // 2) 좌표 주변 약국을 가까운 순서로 검색. radius 단위는 미터(최대 20000).
    @Retryable(retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 500))
    public KakaoApiResponseDto searchPharmacies(double latitude, double longitude, int radiusMeters) {
        return kakaoRestClient.get()
                .uri(uri -> uri.path("/v2/local/search/category.json")
                        .queryParam("category_group_code", PHARMACY_CATEGORY)
                        .queryParam("x", longitude)
                        .queryParam("y", latitude)
                        .queryParam("radius", radiusMeters)
                        .queryParam("sort", "distance")
                        .build())
                .retrieve()
                .body(KakaoApiResponseDto.class);
    }

    // 3) 장소 이름(예: "강남역", "서울시청") -> 좌표. 주소 검색으로 못 찾는 랜드마크용.
    //    AI 자연어 검색에서 쓴다. 가장 정확한 1건만 받는다.
    @Retryable(retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 500))
    public KakaoApiResponseDto searchKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return kakaoRestClient.get()
                .uri(uri -> uri.path("/v2/local/search/keyword.json")
                        .queryParam("query", keyword)
                        .queryParam("size", 1)
                        .build())
                .retrieve()
                .body(KakaoApiResponseDto.class);
    }

    // 재시도를 다 써도 실패하거나 4xx 오류면 여기로 온다. 화면에는 "결과 없음"으로 보여 준다.
    @Recover
    public KakaoApiResponseDto recoverAddress(RestClientException e, String address) {
        log.error("카카오 주소 검색 실패. address={}", address, e);
        return null;
    }

    @Recover
    public KakaoApiResponseDto recoverKeyword(RestClientException e, String keyword) {
        log.error("카카오 키워드 검색 실패. keyword={}", keyword, e);
        return null;
    }

    @Recover
    public KakaoApiResponseDto recoverPharmacies(RestClientException e, double latitude, double longitude, int radiusMeters) {
        log.error("카카오 약국 검색 실패. lat={}, lng={}", latitude, longitude, e);
        return null;
    }
}