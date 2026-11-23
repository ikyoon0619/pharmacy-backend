package com.example.pharmacy.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.service.DirectionService;
import com.example.pharmacy.hours.PharmacyHours;
import com.example.pharmacy.hours.PharmacyHoursService;
import com.example.pharmacy.hours.WeeklyHours;
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
    static final String SEARCH_OPEN_PHARMACIES = "search_open_pharmacies";

    static final int DEFAULT_RADIUS = 3_000;
    static final int MIN_RADIUS = 300;
    static final int MAX_RADIUS = 20_000;   // 카카오 카테고리 검색 최대 반경
    static final int DEFAULT_COUNT = 3;
    static final int MAX_COUNT = 5;

    private final KakaoSearchService kakaoSearchService;
    private final DirectionService directionService;
    private final PharmacyHoursService hoursService;
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

    // 영업시간 데이터가 있을 때만 주는 세 번째 도구
    public Tool openHoursDefinition() {
        return Tool.builder()
                .name(SEARCH_OPEN_PHARMACIES)
                .description("좌표 주변에서 특정 요일, 특정 시각에 문을 연 약국을 가까운 순서로 찾는다. "
                        + "국립중앙의료원 공공데이터의 영업시간 기준이다. "
                        + "day 는 1=월 2=화 3=수 4=목 5=금 6=토 7=일 8=공휴일. "
                        + "time 은 24시간제 HHMM 문자열 (예: 오후 11시 -> \"2300\", 새벽 1시 반 -> \"0130\"). "
                        + "radius_meters 는 300~20000 (기본 3000), count 는 1~5 (기본 3).")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("latitude", JsonValue.from(Map.of("type", "number")))
                                .putAdditionalProperty("longitude", JsonValue.from(Map.of("type", "number")))
                                .putAdditionalProperty("radius_meters", JsonValue.from(Map.of("type", "integer")))
                                .putAdditionalProperty("day", JsonValue.from(Map.of("type", "integer")))
                                .putAdditionalProperty("time", JsonValue.from(Map.of("type", "string")))
                                .putAdditionalProperty("count", JsonValue.from(Map.of("type", "integer")))
                                .build())
                        .required(List.of("latitude", "longitude", "radius_meters", "day", "time", "count"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .strict(true)
                .build();
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

    record SearchOpenInput(
            double latitude,
            double longitude,
            @JsonProperty("radius_meters") Integer radiusMeters,
            Integer day,
            String time,
            Integer count) {
    }

    // Claude 가 고른 도구를 실행하고, 결과를 JSON 문자열로 돌려준다 (tool_result 로 다시 Claude 에게 간다)
    public String execute(String toolName, JsonValue input, Session session) {
        return switch (toolName) {
            case FIND_LOCATION -> findLocation(parse(input, FindLocationInput.class), session);
            case SEARCH_PHARMACIES -> searchPharmacies(parse(input, SearchPharmaciesInput.class), session);
            case SEARCH_OPEN_PHARMACIES -> searchOpen(parse(input, SearchOpenInput.class), session);
            default -> throw new IllegalArgumentException("모르는 도구: " + toolName);
        };
    }

    // 화면에 보여 줄 진행 단계 문장. 예: "'강남역' 위치를 찾고 있어요"
    // AI 가 준 값을 그대로 쓰지만, 화면에서는 textContent 로 넣어서 HTML 로 해석되지 않는다.
    static String describe(String toolName, JsonValue input) {
        Map<?, ?> in;
        try {
            in = input.convert(Map.class);
        } catch (RuntimeException e) {
            in = Map.of();
        }
        if (in == null) {
            in = Map.of();
        }
        return switch (toolName) {
            case FIND_LOCATION -> in.get("query") != null
                    ? "'" + in.get("query") + "' 위치를 찾고 있어요"
                    : "위치를 찾고 있어요";
            case SEARCH_PHARMACIES -> "반경 " + radiusLabel(in.get("radius_meters")) + " 안에서 약국을 찾고 있어요";
            case SEARCH_OPEN_PHARMACIES -> {
                Object day = in.get("day");
                Object time = in.get("time");
                String dayLabel = (day instanceof Number n && n.intValue() >= 1 && n.intValue() <= 8)
                        ? WeeklyHours.dayName(n.intValue()) + (n.intValue() == 8 ? "" : "요일")
                        : null;
                if (dayLabel == null && time == null) {
                    yield "지금 여는 약국을 찾고 있어요";
                }
                String when = (dayLabel == null ? "오늘" : dayLabel) + (time == null ? "" : " " + time);
                yield when + "에 여는 약국을 찾고 있어요";
            }
            default -> "정보를 찾고 있어요";
        };
    }

    private static String radiusLabel(Object radius) {
        int meters = clamp(radius instanceof Number n ? n.intValue() : null, DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS);
        return meters >= 1000 ? (meters % 1000 == 0 ? meters / 1000 + "km" : String.format("%.1fkm", meters / 1000.0)) : meters + "m";
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
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", d.getTargetPharmacyName());
            p.put("address", d.getTargetAddress());
            p.put("distance_km", d.getDistanceKm());
            p.put("phone", d.getTargetPhone());
            hoursService.match(d.getTargetPharmacyName(), d.getTargetPhone(), d.getTargetLatitude(), d.getTargetLongitude())
                    .map(PharmacyHours::hours)
                    .filter(h -> !h.isEmpty())
                    .ifPresent(h -> p.put("hours_today", h.label(hoursService.now().day())));
            pharmacies.add(p);
        }
        return toJson(Map.of(
                "radius_meters", radius,
                "count", pharmacies.size(),
                "pharmacies", pharmacies));
    }

    // 영업시간 데이터에서 "그때 연 약국" 을 찾고, 길안내 링크를 위해 Direction 으로 저장한다
    private String searchOpen(SearchOpenInput in, Session session) {
        int radius = clamp(in.radiusMeters(), DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS);
        int count = clamp(in.count(), DEFAULT_COUNT, 1, MAX_COUNT);
        int day = clamp(in.day(), hoursService.now().day(), 1, WeeklyHours.HOLIDAY);
        Integer hhmm = WeeklyHours.parseTime(in.time());
        if (hhmm == null) {
            throw new IllegalArgumentException("time 은 HHMM 형식이어야 합니다: " + in.time());
        }
        String label = session.lastLocationName != null ? session.lastLocationName : "AI 검색 위치";

        List<PharmacyHoursService.Nearby> open = hoursService.findOpenNear(
                in.latitude(), in.longitude(), radius, day, hhmm, count);
        List<DocumentDto> docs = open.stream()
                .map(n -> new DocumentDto(n.pharmacy().getName(), n.pharmacy().getAddress(),
                        n.pharmacy().getLatitude(), n.pharmacy().getLongitude(),
                        String.valueOf(Math.round(n.distanceMeters())), n.pharmacy().getTel(), null))
                .toList();
        session.directions = directionService.saveDirections(label, in.latitude(), in.longitude(), docs, count);

        List<Map<String, Object>> pharmacies = new ArrayList<>();
        for (PharmacyHoursService.Nearby n : open) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", n.pharmacy().getName());
            p.put("address", n.pharmacy().getAddress());
            p.put("distance_km", Math.round(n.distanceMeters() / 10.0) / 100.0);
            p.put("phone", n.pharmacy().getTel());
            p.put("hours_that_day", n.pharmacy().hours().label(day));
            pharmacies.add(p);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("day", WeeklyHours.dayName(day));
        result.put("time", in.time());
        result.put("radius_meters", radius);
        result.put("count", pharmacies.size());
        result.put("pharmacies", pharmacies);
        result.put("source", "국립중앙의료원 공공데이터 (실제 영업시간과 다를 수 있음)");
        return toJson(result);
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
