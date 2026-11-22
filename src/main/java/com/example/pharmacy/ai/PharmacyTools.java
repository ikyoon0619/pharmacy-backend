package com.example.pharmacy.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.service.DirectionService;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto.DocumentDto;
import com.example.pharmacy.kakao.service.KakaoSearchService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Claude 에게 주는 도구 2개와, 도구를 실제로 실행하는 코드.
// 도구는 둘 다 "읽기"만 한다 (카카오 검색 + 추천 결과 저장). 그래서 AI 가 잘못 불러도 위험한 일은 없다.
@Component
@RequiredArgsConstructor
public class PharmacyTools {

    static final String FIND_LOCATION = "find_location";
    static final String SEARCH_PHARMACIES = "search_pharmacies";

    static final int DEFAULT_RADIUS = 3_000;
    static final int MIN_RADIUS = 300;
    static final int MAX_RADIUS = 20_000;   // 카카오 카테고리 검색 최대 반경
    static final int DEFAULT_COUNT = 3;
    static final int MAX_COUNT = 5;

    private final KakaoSearchService kakaoSearchService;
    private final DirectionService directionService;
    private final ObjectMapper objectMapper;

    // 한 번의 검색(질문 1개) 동안 도구 결과를 모아 두는 곳
    public static class Session {
        private String lastLocationName;
        private List<Direction> directions = List.of();

        public List<Direction> directions() {
            return directions;
        }
    }

    // ---------- 도구 정의 (Claude 에게 보내는 JSON 스키마) ----------

    public List<Tool> definitions() {
        Tool findLocation = Tool.builder()
                .name(FIND_LOCATION)
                .description("장소 이름이나 주소를 위도/경도로 바꾼다. "
                        + "역, 건물, 동네, 도로명 주소 모두 된다. 예: '강남역', '서울시청', '성북구 종암로 10길'. "
                        + "'근처', '약국' 같은 말은 빼고 장소만 넣는다.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("query", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "찾을 장소나 주소")))
                                .build())
                        .required(List.of("query"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .strict(true)
                .build();

        Tool searchPharmacies = Tool.builder()
                .name(SEARCH_PHARMACIES)
                .description("좌표 주변 약국을 가까운 순서로 찾는다. find_location 결과의 좌표를 넣는다. "
                        + "radius_meters 는 300~20000 (특별한 말이 없으면 3000), count 는 1~5 (기본 3). "
                        + "영업시간 정보는 돌려주지 않는다.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("latitude", JsonValue.from(Map.of("type", "number")))
                                .putAdditionalProperty("longitude", JsonValue.from(Map.of("type", "number")))
                                .putAdditionalProperty("radius_meters", JsonValue.from(Map.of("type", "integer")))
                                .putAdditionalProperty("count", JsonValue.from(Map.of("type", "integer")))
                                .build())
                        .required(List.of("latitude", "longitude", "radius_meters", "count"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .strict(true)
                .build();

        return List.of(findLocation, searchPharmacies);
    }

    // ---------- 도구 실행 ----------

    record FindLocationInput(String query) {
    }

    record SearchPharmaciesInput(
            double latitude,
            double longitude,
            @JsonProperty("radius_meters") Integer radiusMeters,
            Integer count) {
    }

    // Claude 가 고른 도구를 실행하고, 결과를 JSON 문자열로 돌려준다 (tool_result 로 다시 Claude 에게 간다)
    public String execute(String toolName, JsonValue input, Session session) {
        return switch (toolName) {
            case FIND_LOCATION -> findLocation(parse(input, FindLocationInput.class), session);
            case SEARCH_PHARMACIES -> searchPharmacies(parse(input, SearchPharmaciesInput.class), session);
            default -> throw new IllegalArgumentException("모르는 도구: " + toolName);
        };
    }

    // SDK 의 JsonValue -> Map -> 우리 record. 문자열을 직접 자르지 않고 JSON 으로 파싱한다.
    private <T> T parse(JsonValue input, Class<T> type) {
        return objectMapper.convertValue(input.convert(Map.class), type);
    }

    // 주소 검색을 먼저 해 보고, 없으면 장소(키워드) 검색. "강남역"은 주소가 아니라서 키워드 검색으로 찾힌다.
    private String findLocation(FindLocationInput in, Session session) {
        DocumentDto doc = first(kakaoSearchService.searchAddress(in.query()));
        if (doc == null) {
            doc = first(kakaoSearchService.searchKeyword(in.query()));
        }
        if (doc == null) {
            return toJson(Map.of("found", false, "message", "'" + in.query() + "' 위치를 찾지 못했습니다."));
        }

        String name = doc.placeName() != null ? doc.placeName() : doc.addressName();
        session.lastLocationName = name;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("name", name);
        result.put("address", doc.addressName());
        result.put("latitude", doc.latitude());
        result.put("longitude", doc.longitude());
        return toJson(result);
    }

    private String searchPharmacies(SearchPharmaciesInput in, Session session) {
        // AI 가 범위를 벗어난 값을 보내도 여기서 안전한 범위로 맞춘다
        int radius = clamp(in.radiusMeters(), DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS);
        int count = clamp(in.count(), DEFAULT_COUNT, 1, MAX_COUNT);
        String label = session.lastLocationName != null ? session.lastLocationName : "AI 검색 위치";

        List<Direction> directions = directionService.recommendNear(
                label, in.latitude(), in.longitude(), radius, count);
        session.directions = directions;

        List<Map<String, Object>> pharmacies = new ArrayList<>();
        for (Direction d : directions) {
            pharmacies.add(Map.of(
                    "name", d.getTargetPharmacyName(),
                    "address", d.getTargetAddress(),
                    "distance_km", d.getDistanceKm()));
        }
        return toJson(Map.of(
                "radius_meters", radius,
                "count", pharmacies.size(),
                "pharmacies", pharmacies));
    }

    private static DocumentDto first(KakaoApiResponseDto response) {
        return (response == null || response.isEmpty()) ? null : response.documents().get(0);
    }

    static int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
