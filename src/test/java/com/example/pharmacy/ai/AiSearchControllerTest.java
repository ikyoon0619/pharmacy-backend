package com.example.pharmacy.ai;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.view.ResultViewAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AiSearchController.class)
class AiSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiSearchService aiSearchService;
    @MockBean
    private AiUsageLimiter usageLimiter;
    @MockBean
    private ResultViewAssembler resultViewAssembler;
    @MockBean
    private AiSearchStreamer streamer;

    @Test
    void AI_답변과_약국_표를_보여_준다() throws Exception {
        Direction d = Direction.builder().id(1L).targetPharmacyName("온누리약국").build();
        given(aiSearchService.isEnabled()).willReturn(true);
        given(usageLimiter.tryAcquire("203.0.113.7")).willReturn(true);
        given(aiSearchService.search("강남역 근처 약국")).willReturn(new AiSearchResult("3곳을 찾았어요.", List.of(d)));
        given(resultViewAssembler.toOutputs(List.of(d))).willReturn(List.of(
                new OutputDto("온누리약국", "서울 강남구", "210m", "/dir/1",
                        "https://map.kakao.com/link/roadview/1,2", "02-555-0100", null,
                        "오늘(월) 09:00~22:00", true)));

        mockMvc.perform(post("/ai-search").param("query", "  강남역 근처 약국 ")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(view().name("ai-output"))
                .andExpect(model().attribute("answer", "3곳을 찾았어요."))
                .andExpect(model().attribute("outputs", hasSize(1)))
                .andExpect(content().string(containsString("온누리약국")))
                .andExpect(content().string(containsString("tel:02-555-0100")))
                .andExpect(content().string(containsString("영업 중")))
                .andExpect(content().string(containsString("오늘(월) 09:00~22:00")));
    }

    @Test
    void 한도를_넘으면_Claude를_부르지_않는다() throws Exception {
        given(aiSearchService.isEnabled()).willReturn(true);
        given(usageLimiter.tryAcquire(anyString())).willReturn(false);

        mockMvc.perform(post("/ai-search").param("query", "강남역 약국"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("answer", containsString("한도")));
        verify(aiSearchService, never()).search(anyString());
    }

    @Test
    void 너무_긴_질문은_거절한다() throws Exception {
        mockMvc.perform(post("/ai-search").param("query", "가".repeat(201)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("answer", containsString("200자")));
        verify(usageLimiter, never()).tryAcquire(anyString());
        verify(aiSearchService, never()).search(anyString());
    }

    @Test
    void AI_답변의_HTML은_이스케이프된다() throws Exception {
        given(aiSearchService.isEnabled()).willReturn(true);
        given(usageLimiter.tryAcquire(anyString())).willReturn(true);
        given(aiSearchService.search(anyString()))
                .willReturn(AiSearchResult.message("<script>alert(1)</script>"));

        mockMvc.perform(post("/ai-search").param("query", "강남역"))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void 실시간_화면은_질문을_담아_보내고_스트림_스크립트를_넣는다() throws Exception {
        mockMvc.perform(get("/ai").param("q", " 강남역 <약국> "))
                .andExpect(status().isOk())
                .andExpect(view().name("ai-output"))
                .andExpect(content().string(containsString("data-query=\"강남역 &lt;약국&gt;\"")))
                .andExpect(content().string(containsString("/js/ai-stream.js")));
        verify(aiSearchService, never()).search(anyString());
    }

    @Test
    void 질문_없이_실시간_화면에_오면_처음으로() throws Exception {
        mockMvc.perform(get("/ai"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 스트림은_한도를_먼저_확인하고_통과하면_시작한다() throws Exception {
        given(aiSearchService.isEnabled()).willReturn(true);
        given(usageLimiter.tryAcquire(anyString())).willReturn(true);
        given(streamer.start("강남역 약국")).willReturn(new SseEmitter());

        mockMvc.perform(post("/ai-search/stream").param("query", " 강남역 약국 "))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(streamer).start("강남역 약국");
        verify(streamer, never()).finished(anyString());
    }

    @Test
    void 스트림도_한도를_넘으면_안내만_보내고_검색하지_않는다() throws Exception {
        given(aiSearchService.isEnabled()).willReturn(true);
        given(usageLimiter.tryAcquire(anyString())).willReturn(false);
        given(streamer.finished(anyString())).willReturn(new SseEmitter());

        mockMvc.perform(post("/ai-search/stream").param("query", "강남역 약국"));

        verify(streamer).finished(org.mockito.ArgumentMatchers.contains("한도를 다 썼어요"));
        verify(streamer, never()).start(anyString());
    }
}
