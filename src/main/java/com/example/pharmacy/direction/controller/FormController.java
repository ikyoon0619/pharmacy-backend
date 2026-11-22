package com.example.pharmacy.direction.controller;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.service.DirectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class FormController {

    private final DirectionService directionService;

    // 첫 화면: 주소 입력 폼
    @GetMapping("/")
    public String main() {
        return "main";
    }

    // 검색: 추천 결과 화면
    @PostMapping("/search")
    public String search(@RequestParam("address") String address, Model model) {
        List<OutputDto> outputs = directionService.recommend(address).stream()
                .map(directionService::toOutput)
                .toList();

        model.addAttribute("address", address);
        model.addAttribute("outputs", outputs);
        return "output";
    }

    // 짧은 길안내 URL -> 카카오맵으로 이동
    @GetMapping("/dir/{encodedId}")
    public String redirect(@PathVariable String encodedId) {
        return "redirect:" + directionService.findDirectionUrl(encodedId);
    }
}