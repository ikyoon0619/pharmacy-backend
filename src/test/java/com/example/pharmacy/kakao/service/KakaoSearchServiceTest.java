package com.example.pharmacy.kakao.service;

import com.example.pharmacy.config.RestClientConfig;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

// 실제 카카오 서버 대신 가짜 서버(MockRestServiceServer)로 응답을 흉내 낸다
@RestClientTest(KakaoSearchService.class)
@Import(RestClientConfig.class)
@TestPropertySource(properties = {
        "kakao.base-url=https://dapi.kakao.com",
        "kakao.rest-api-key=test-key"
})
class KakaoSearchServiceTest {

    @Autowired
    private KakaoSearchService kakaoSearchService;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void 주소가_비어있으면_API를_호출하지_않고_null() {
        assertThat(kakaoSearchService.searchAddress(" ")).isNull();
        server.verify(); // 요청이 한 번도 없어야 통과
    }

    @Test
    void 주소_검색_결과를_DTO로_변환한다() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json")))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess("""
                        {"meta":{"total_count":1},
                         "documents":[{"address_name":"서울 성북구 종암로 10길","x":"127.03","y":"37.59"}]}
                        """, MediaType.APPLICATION_JSON));

        KakaoApiResponseDto result = kakaoSearchService.searchAddress("서울 성북구 종암로 10길");

        assertThat(result.meta().totalCount()).isEqualTo(1);
        assertThat(result.documents().get(0).latitude()).isEqualTo(37.59);
        assertThat(result.documents().get(0).longitude()).isEqualTo(127.03);
    }

    @Test
    void 약국_검색은_PM9_카테고리와_거리순으로_요청한다() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/category.json")))
                .andExpect(queryParam("category_group_code", "PM9"))
                .andExpect(queryParam("sort", "distance"))
                .andRespond(withSuccess("""
                        {"meta":{"total_count":1},
                         "documents":[{"place_name":"은혜약국","address_name":"서울 성북구 종암동 1","x":"127.04","y":"37.60","distance":"532"}]}
                        """, MediaType.APPLICATION_JSON));

        KakaoApiResponseDto result = kakaoSearchService.searchPharmacies(37.59, 127.03, 10_000);

        assertThat(result.documents().get(0).placeName()).isEqualTo("은혜약국");
        assertThat(result.documents().get(0).distance()).isEqualTo("532");
    }

    @Test
    void 서버_오류가_계속되면_3번_시도_후_null을_돌려준다() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json")))
                    .andRespond(withServerError());
        }

        assertThat(kakaoSearchService.searchAddress("서울")).isNull();
        server.verify();
    }

    @Test
    void 키가_틀린_401은_재시도하지_않고_null() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json")))
                .andRespond(withUnauthorizedRequest());

        assertThat(kakaoSearchService.searchAddress("서울")).isNull();
        server.verify(); // 요청은 딱 1번
    }

    @Test
    void 키워드_검색은_장소_이름으로_1건만_요청한다() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
                .andExpect(queryParam("size", "1"))
                .andRespond(withSuccess("""
                        {"meta":{"total_count":1},
                         "documents":[{"place_name":"강남역 2호선","address_name":"서울 강남구 역삼동 858","x":"127.027","y":"37.498"}]}
                        """, MediaType.APPLICATION_JSON));

        KakaoApiResponseDto result = kakaoSearchService.searchKeyword("강남역");

        assertThat(result.documents().get(0).placeName()).isEqualTo("강남역 2호선");
        assertThat(result.documents().get(0).latitude()).isEqualTo(37.498);
    }
}
