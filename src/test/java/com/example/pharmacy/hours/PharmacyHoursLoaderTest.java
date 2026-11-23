package com.example.pharmacy.hours;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PharmacyHoursLoaderTest {

    @Mock
    private NmcPharmacyClient client;
    @Mock
    private PharmacyHoursRepository repository;
    @InjectMocks
    private PharmacyHoursLoader loader;

    private static PharmacyHours row(String id) {
        return PharmacyHours.builder().hpid(id).name(id).latitude(37.5).longitude(127.0).weeklyHours("").build();
    }

    @Test
    void 전체_건수만큼_페이지를_넘기며_저장한다() {
        given(client.hasKey()).willReturn(true);
        given(client.fetchPage(1, 1000)).willReturn(new NmcPharmacyClient.Page(2500, List.of(row("a"), row("b"))));
        given(client.fetchPage(2, 1000)).willReturn(new NmcPharmacyClient.Page(2500, List.of(row("c"))));
        given(client.fetchPage(3, 1000)).willReturn(new NmcPharmacyClient.Page(2500, List.of(row("d"))));

        assertThat(loader.load()).isEqualTo(4);
        verify(client, times(3)).fetchPage(anyInt(), anyInt());
    }

    @Test
    void 키가_없으면_부르지_않는다() {
        given(client.hasKey()).willReturn(false);

        assertThat(loader.load()).isEqualTo(-1);
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void 공공데이터가_실패해도_예외를_밖으로_던지지_않는다() {
        given(client.hasKey()).willReturn(true);
        given(client.fetchPage(1, 1000)).willThrow(new IllegalStateException("키 오류"));

        assertThat(loader.load()).isEqualTo(-1);
    }
}
