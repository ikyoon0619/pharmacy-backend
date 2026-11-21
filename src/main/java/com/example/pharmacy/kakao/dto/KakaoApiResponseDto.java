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
    // 주소 검색에는 place_name, distance 가 없어서 null 이 들어온다.
    public record DocumentDto(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("y") double latitude,
            @JsonProperty("x") double longitude,
            @JsonProperty("distance") String distance) {
    }

    public boolean isEmpty() {
        return documents == null || documents.isEmpty();
    }
}