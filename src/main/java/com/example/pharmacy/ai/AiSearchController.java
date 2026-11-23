package com.example.pharmacy.ai;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.view.ResultViewAssembler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AiSearchController {

    private final AiSearchService aiSearchService;
    private final AiUsageLimiter usageLimiter;
    private final ResultViewAssembler resultViewAssembler;
    private final AiSearchStreamer streamer;

    // 문장으로 찾기: "강남역 근처 약국 찾아줘"
    // JS 가 없는 브라우저는 이 주소로 폼을 보내고, 결과를 한 번에 받는다.
    @PostMapping("/ai-search")
    public String aiSearch(@RequestParam("query") String query, HttpServletRequest request, Model model) {
        String q = query == null ? "" : query.strip();
        model.addAttribute("query", q);
        model.addAttribute("outputs", List.of());

        Optional<String> rejected = reject(q, request);
        if (rejected.isPresent()) {
            model.addAttribute("answer", rejected.get());
            return "ai-output";
        }

        AiSearchResult result = aiSearchService.search(q);
        List<OutputDto> outputs = resultViewAssembler.toOutputs(result.directions());
        model.addAttribute("answer", result.answer());
        model.addAttribute("outputs", outputs);
        return "ai-output";
    }

    // JS 가 있는 브라우저: 폼 대신 /ai?q=... 로 이동하고, 그 화면이 /ai-search/stream 을 열어 결과를 실시간으로 받는다.
    // 결과 화면에 주소가 생겨서 새로고침·뒤로 가기도 자연스럽다.
    @GetMapping("/ai")
    public String aiPage(@RequestParam(value = "q", required = false) String query, Model model) {
        String q = query == null ? "" : query.strip();
        if (!StringUtils.hasText(q)) {
            return "redirect:/";
        }
        model.addAttribute("query", q);
        model.addAttribute("outputs", List.of());
        model.addAttribute("streaming", true);
        return "ai-output";
    }

    // SSE: 진행 단계(step), 답변 조각(token), 마지막 결과(done) 를 차례로 보낸다
    @PostMapping(path = "/ai-search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter aiSearchStream(@RequestParam("query") String query,
                                     HttpServletRequest request, HttpServletResponse response) {
        // 프록시(Render, nginx)가 응답을 모았다가 한 번에 보내지 않도록
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        String q = query == null ? "" : query.strip();
        // 한도 검사는 스트림을 열기 전에. 걸리면 안내 문장 하나만 보내고 닫는다.
        return reject(q, request)
                .map(streamer::finished)
                .orElseGet(() -> streamer.start(q));
    }

    // 빈 질문, 너무 긴 질문, 하루 한도 초과면 안내 문장을 돌려준다. 통과하면 empty (한도 1회 차감).
    private Optional<String> reject(String q, HttpServletRequest request) {
        if (!StringUtils.hasText(q)) {
            return Optional.of("찾을 위치를 문장으로 적어 주세요. 예: 강남역 근처 약국 찾아줘");
        }
        if (q.length() > AiSearchService.MAX_QUERY_LENGTH) {
            return Optional.of("질문은 " + AiSearchService.MAX_QUERY_LENGTH + "자 이내로 적어 주세요.");
        }
        // 키가 없을 때는 한도를 쓰지 않는다
        if (aiSearchService.isEnabled() && !usageLimiter.tryAcquire(clientKey(request))) {
            return Optional.of("오늘 AI 검색 한도를 다 썼어요. 내일 다시 시도하거나 주소 검색을 이용해 주세요.");
        }
        return Optional.empty();
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
