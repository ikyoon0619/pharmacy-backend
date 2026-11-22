package com.example.pharmacy.ai;

import com.example.pharmacy.direction.entity.Direction;

import java.util.List;

// AI 검색 결과.
// answer: Claude 가 쓴 짧은 안내 문장
// directions: 실제 도구(카카오 검색)가 돌려준 약국. 표는 이 값으로만 그린다 (AI 가 지어낸 약국이 표에 들어갈 수 없다)
public record AiSearchResult(String answer, List<Direction> directions) {

    public static AiSearchResult message(String answer) {
        return new AiSearchResult(answer, List.of());
    }
}
