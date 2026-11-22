package com.example.pharmacy.ai;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.service.DirectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
    private DirectionService directionService;

    @Test
    void AI_답변과_약국_표를_보여_준다() throws Exception {
        Direction d = Direction.builder().id(1L).targetPharmacyName("온누리약국").build();
        given(aiSearchService.isEnabled()).willReturn(true);
        given(usageLimiter.tryAcquire("203.0.113.7")).willReturn(true);
        given(aiSearchService.search("강남역 근처 약국")).willReturn(new AiSearchResult("3곳을 찾았어요.", List.of(d)));
        given(directionService.toOutput(d)).willReturn(
                new OutputDto("온누리약국", "서울 강남구", "210m", "/dir/1",
                        "https://map.kakao.com/link/roadview/1,2", "02-555-0100", null));

        mockMvc.perform(post("/ai-search").param("query", "  강남역 근처 약국 ")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(view().name("ai-output"))
                .andExpect(model().attribute("answer", "3곳을 찾았어요."))
                .andExpect(model().attribute("outputs", hasSize(1)))
                .andExpect(content().string(containsString("온누리약국")))
                .andExpect(content().string(containsString("tel:02-555-0100")));
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
}
