package com.example.pharmacy.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 카카오 로컬 API 응답: { "meta": {...}, "documents": [...] }
public record KakaoApiResponseDto(
        @JsonProperty("meta") MetaDto meta,
        @JsonProperty("documents") List<DocumentDto> documents) {

    public record MetaDto(@JsonProperty("total_count") Integer totalCount) {
    }

    // 주소 검색과 카테고리 검색이 같은 구조를 쓴다.
    // 주소 검색에는 place_name, distance, phone, place_url 이 없어서 null 이 들어온다.
    public record DocumentDto(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("y") double latitude,
            @JsonProperty("x") double longitude,
            @JsonProperty("distance") String distance,
            @JsonProperty("phone") String phone,          // 약국 전화번호 (없으면 빈 문자열)
            @JsonProperty("place_url") String placeUrl) { // 카카오맵 장소 상세 페이지

        // 주소/좌표만 필요한 곳(테스트 등)에서 쓰는 짧은 생성자
        public DocumentDto(String placeName, String addressName, double latitude, double longitude, String distance) {
            this(placeName, addressName, latitude, longitude, distance, null, null);
        }
    }

    public boolean isEmpty() {
        return documents == null || documents.isEmpty();
    }
}