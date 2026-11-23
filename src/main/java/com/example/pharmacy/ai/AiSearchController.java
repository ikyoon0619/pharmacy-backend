package com.example.pharmacy.ai;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.view.ResultViewAssembler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AiSearchController {

    private final AiSearchService aiSearchService;
    private final AiUsageLimiter usageLimiter;
    private final ResultViewAssembler resultViewAssembler;

    // 문장으로 찾기: "강남역 근처 약국 찾아줘"
    @PostMapping("/ai-search")
    public String aiSearch(@RequestParam("query") String query, HttpServletRequest request, Model model) {
        String q = query == null ? "" : query.strip();
        model.addAttribute("query", q);
        model.addAttribute("outputs", List.of());

        if (!StringUtils.hasText(q)) {
            model.addAttribute("answer", "찾을 위치를 문장으로 적어 주세요. 예: 강남역 근처 약국 찾아줘");
            return "ai-output";
        }
        if (q.length() > AiSearchService.MAX_QUERY_LENGTH) {
            model.addAttribute("answer", "질문은 " + AiSearchService.MAX_QUERY_LENGTH + "자 이내로 적어 주세요.");
            return "ai-output";
        }
        // 키가 없을 때는 한도를 쓰지 않는다
        if (aiSearchService.isEnabled() && !usageLimiter.tryAcquire(clientKey(request))) {
            model.addAttribute("answer", "오늘 AI 검색 한도를 다 썼어요. 내일 다시 시도하거나 주소 검색을 이용해 주세요.");
            return "ai-output";
        }

        AiSearchResult result = aiSearchService.search(q);
        List<OutputDto> outputs = resultViewAssembler.toOutputs(result.directions());
        model.addAttribute("answer", result.answer());
        model.addAttribute("outputs", outputs);
        return "ai-output";
    }

    // Render 같은 프록시 뒤에서는 실제 사용자 IP 가 X-Forwarded-For 의 첫 값에 들어 있다
    static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
