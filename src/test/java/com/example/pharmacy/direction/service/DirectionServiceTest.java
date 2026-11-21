package com.example.pharmacy.direction.service;

import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.repository.DirectionRepository;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto.DocumentDto;
import com.example.pharmacy.kakao.dto.KakaoApiResponseDto.MetaDto;
import com.example.pharmacy.kakao.service.KakaoSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DirectionServiceTest {

    @Mock
    private KakaoSearchService kakaoSearchService;
    @Mock
    private DirectionRepository directionRepository;
    @Spy
    private Base62Service base62Service;

    @InjectMocks
    private DirectionService directionService;

    private static DocumentDto pharmacy(String name, String meters) {
        return new DocumentDto(name, name + " 주소", 37.6, 127.0, meters);
    }

    @Test
    void 약국이_여러개여도_최대_3곳만_저장한다() {
        given(kakaoSearchService.searchAddress("서울 성북구"))
                .willReturn(new KakaoApiResponseDto(new MetaDto(1),
                        List.of(new DocumentDto(null, "서울 성북구", 37.59, 127.03, null))));
        given(kakaoSearchService.searchPharmacies(37.59, 127.03, DirectionService.RADIUS_METERS))
                .willReturn(new KakaoApiResponseDto(new MetaDto(4), List.of(
                        pharmacy("A약국", "100"), pharmacy("B약국", "532"),
                        pharmacy("C약국", "1200"), pharmacy("D약국", "3000"))));
        given(directionRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        List<Direction> result = directionService.recommend("서울 성북구");

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Direction::getTargetPharmacyName)
                .containsExactly("A약국", "B약국", "C약국");
        assertThat(result.get(1).getDistanceKm()).isEqualTo(0.53); // 532m -> 0.53km
    }

    @Test
    void 주소를_찾지_못하면_빈_목록이고_약국_검색을_하지_않는다() {
        given(kakaoSearchService.searchAddress("없는주소"))
                .willReturn(new KakaoApiResponseDto(new MetaDto(0), List.of()));

        assertThat(directionService.recommend("없는주소")).isEmpty();
        verify(kakaoSearchService, never()).searchPharmacies(37.59, 127.03, DirectionService.RADIUS_METERS);
    }

    @Test
    void 짧은_코드로_카카오맵_길안내_URL을_만든다() {
        Direction direction = Direction.builder()
                .id(125L).targetPharmacyName("은혜약국")
                .targetLatitude(37.6).targetLongitude(127.04).build();
        given(directionRepository.findById(125L)).willReturn(Optional.of(direction));

        String url = directionService.findDirectionUrl(base62Service.encode(125L));

        assertThat(url).startsWith("https://map.kakao.com/link/to/")
                .endsWith(",37.6,127.04");
    }
}