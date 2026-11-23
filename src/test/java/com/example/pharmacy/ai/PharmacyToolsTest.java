package com.example.pharmacy.ai;

import com.anthropic.core.JsonValue;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.service.DirectionService;
import com.example.pharmacy.hours.PharmacyHours;
import com.example.pharmacy.hours.PharmacyHoursService;
import com.example.pharmacy.hours.WeeklyHours;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto.DocumentDto;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto.MetaDto;
import com.example.pharmacy.kakao.service.KakaoSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PharmacyToolsTest {

    @Mock
    private KakaoSearchService kakaoSearchService;
    @Mock
    private DirectionService directionService;
    @Mock
    private PharmacyHoursService hoursService;

    private PharmacyTools tools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tools = new PharmacyTools(kakaoSearchService, directionService, hoursService, objectMapper);
    }

    private static KakaoApiResponseDto docs(DocumentDto... documents) {
        return new KakaoApiResponseDto(new MetaDto(documents.length), List.of(documents));
    }

    @Test
    void 도구는_2개이고_strict_스키마다() {
        assertThat(tools.definitions())
                .extracting(t -> t.name())
                .containsExactly("find_location", "search_pharmacies");
        assertThat(tools.definitions()).allSatisfy(t -> assertThat(t.strict()).contains(true));
    }

    @Test
    void 주소로_못_찾으면_장소_키워드로_찾는다() throws Exception {
        given(kakaoSearchService.searchAddress("강남역")).willReturn(docs());
        given(kakaoSearchService.searchKeyword("강남역"))
                .willReturn(docs(new DocumentDto("강남역 2호선", "서울 강남구 역삼동 858", 37.498, 127.027, null)));

        String json = tools.execute("find_location", JsonValue.from(Map.of("query", "강남역")),
                new PharmacyTools.Session());

        Map<?, ?> result = objectMapper.readValue(json, Map.class);
        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("name")).isEqualTo("강남역 2호선");
        assertThat(result.get("latitude")).isEqualTo(37.498);
    }

    @Test
    void 위치를_못_찾으면_found_false() throws Exception {
        given(kakaoSearchService.searchAddress("없는곳")).willReturn(null);
        given(kakaoSearchService.searchKeyword("없는곳")).willReturn(docs());

        String json = tools.execute("find_location", JsonValue.from(Map.of("query", "없는곳")),
                new PharmacyTools.Session());

        assertThat(objectMapper.readValue(json, Map.class).get("found")).isEqualTo(false);
    }

    @Test
    void 약국_검색은_범위를_벗어난_값을_안전하게_맞추고_결과를_세션에_담는다() {
        Direction d = Direction.builder().id(1L).targetPharmacyName("온누리약국")
                .targetAddress("서울 강남구").distanceKm(0.21).build();
        given(directionService.recommendNear("AI 검색 위치", 37.5, 127.0, 20_000, 5)).willReturn(List.of(d));

        PharmacyTools.Session session = new PharmacyTools.Session();
        String json = tools.execute("search_pharmacies", JsonValue.from(Map.of(
                "latitude", 37.5, "longitude", 127.0,
                "radius_meters", 999_999, "count", 50)), session);   // AI 가 너무 큰 값을 보낸 경우

        verify(directionService).recommendNear("AI 검색 위치", 37.5, 127.0, 20_000, 5);
        assertThat(json).contains("온누리약국");
        assertThat(session.directions()).containsExactly(d);
    }

    @Test
    void 모르는_도구는_예외() {
        assertThatThrownBy(() -> tools.execute("delete_all", JsonValue.from(Map.of()), new PharmacyTools.Session()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clamp는_null이면_기본값() {
        assertThat(PharmacyTools.clamp(null, 3, 1, 5)).isEqualTo(3);
        assertThat(PharmacyTools.clamp(0, 3, 1, 5)).isEqualTo(1);
    }

    @Test
    void 영업_중인_약국_검색은_요일과_시각으로_찾고_길안내용으로_저장한다() throws Exception {
        PharmacyHours night = PharmacyHours.builder().hpid("C1").name("심야약국").address("서울 송파구")
                .tel("02-111-2222").latitude(37.513).longitude(127.100)
                .weeklyHours(WeeklyHours.empty().set(7, "1900", "0200").serialize()).build();
        given(hoursService.now()).willReturn(new PharmacyHoursService.Now(1, 1000, java.time.LocalDateTime.now()));
        given(hoursService.findOpenNear(37.51, 127.10, 3000, 7, 2200, 3))
                .willReturn(List.of(new PharmacyHoursService.Nearby(night, 350)));
        Direction saved = Direction.builder().id(9L).targetPharmacyName("심야약국").build();
        given(directionService.saveDirections(eq("AI 검색 위치"), eq(37.51), eq(127.10), anyList(), eq(3)))
                .willReturn(List.of(saved));

        PharmacyTools.Session session = new PharmacyTools.Session();
        String json = tools.execute("search_open_pharmacies", JsonValue.from(Map.of(
                "latitude", 37.51, "longitude", 127.10, "radius_meters", 3000,
                "day", 7, "time", "22:00", "count", 3)), session);

        Map<?, ?> result = objectMapper.readValue(json, Map.class);
        assertThat(result.get("day")).isEqualTo("일");
        assertThat(json).contains("심야약국").contains("19:00~다음날 02:00");
        assertThat(session.directions()).containsExactly(saved);
    }

    @Test
    void 시각_형식이_틀리면_예외() {
        given(hoursService.now()).willReturn(new PharmacyHoursService.Now(1, 1000, java.time.LocalDateTime.now()));
        assertThatThrownBy(() -> tools.execute("search_open_pharmacies", JsonValue.from(Map.of(
                "latitude", 37.5, "longitude", 127.0, "radius_meters", 3000,
                "day", 1, "time", "밤", "count", 3)), new PharmacyTools.Session()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 도구_호출을_화면에_보여_줄_진행_단계_문장으로_바꾼다() {
        assertThat(PharmacyTools.describe("find_location", JsonValue.from(Map.of("query", "강남역"))))
                .isEqualTo("'강남역' 위치를 찾고 있어요");
        assertThat(PharmacyTools.describe("search_pharmacies", JsonValue.from(Map.of("radius_meters", 1500))))
                .isEqualTo("반경 1.5km 안에서 약국을 찾고 있어요");
        assertThat(PharmacyTools.describe("search_pharmacies", JsonValue.from(Map.of("radius_meters", 100))))
                .isEqualTo("반경 300m 안에서 약국을 찾고 있어요");
        assertThat(PharmacyTools.describe("search_open_pharmacies", JsonValue.from(Map.of("day", 7, "time", "22:00"))))
                .isEqualTo("일요일 22:00에 여는 약국을 찾고 있어요");
        assertThat(PharmacyTools.describe("search_open_pharmacies", JsonValue.from(Map.of())))
                .isEqualTo("지금 여는 약국을 찾고 있어요");
        assertThat(PharmacyTools.describe("search_open_pharmacies", JsonValue.from(Map.of("day", 8))))
                .isEqualTo("공휴일에 여는 약국을 찾고 있어요");
        assertThat(PharmacyTools.describe("unknown", JsonValue.from(Map.of())))
                .isEqualTo("정보를 찾고 있어요");
    }
}
