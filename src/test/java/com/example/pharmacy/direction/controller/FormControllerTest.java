package com.example.pharmacy.direction.controller;

import com.example.pharmacy.direction.service.DirectionService;
import com.example.pharmacy.direction.view.ResultViewAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(FormController.class)
class FormControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DirectionService directionService;
    @MockBean
    private ResultViewAssembler resultViewAssembler;

    @Test
    void 첫_화면은_main_뷰() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("main"));
    }

    @Test
    void 검색하면_output_뷰와_결과_목록을_돌려준다() throws Exception {
        given(directionService.recommend("서울 성북구")).willReturn(List.of());

        mockMvc.perform(post("/search").param("address", "서울 성북구"))
                .andExpect(status().isOk())
                .andExpect(view().name("output"))
                .andExpect(model().attributeExists("outputs"));
    }

    @Test
    void 짧은_URL은_카카오맵으로_리다이렉트() throws Exception {
        given(directionService.findDirectionUrl("21")).willReturn("https://map.kakao.com/link/to/x,1,2");

        mockMvc.perform(get("/dir/21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://map.kakao.com/link/to/x,1,2"));
    }

    @Test
    void 없는_길안내_코드는_404() throws Exception {
        given(directionService.findDirectionUrl("zz"))
                .willThrow(new IllegalArgumentException("없음"));

        mockMvc.perform(get("/dir/zz"))
                .andExpect(status().isNotFound());
    }
}