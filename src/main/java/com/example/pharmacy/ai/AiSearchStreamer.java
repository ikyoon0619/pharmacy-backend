package com.example.pharmacy.ai;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.view.ResultViewAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// AI 검색을 SSE(Server-Sent Events)로 흘려보낸다.
//
//   브라우저 ──POST /ai-search/stream──► 컨트롤러: SseEmitter 를 바로 돌려줌 (요청 스레드는 끝)
//                                         │
//                                         └─ aiStreamExecutor 스레드에서 에이전트 루프 실행
//   ◄── event:step   {"text":"'강남역' 위치를 찾고 있어요"}
//   ◄── event:step   {"text":"반경 1km 안에서 약국을 찾고 있어요"}
//   ◄── event:token  {"text":"강남역 근처"}   (Claude 답변 조각, 여러 번)
//   ◄── event:done   {"answer":"...","count":3,"html":"<section>...약국 카드...</section>"}
//
// 약국 카드는 서버에서 Thymeleaf 조각으로 그려서 HTML 로 보낸다 (일반 검색 화면과 똑같은 카드).
@Slf4j
@Component
public class AiSearchStreamer {

    static final long TIMEOUT_MILLIS = 90_000L;

    private final AiSearchService aiSearchService;
    private final ResultViewAssembler resultViewAssembler;
    private final ITemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;

    public AiSearchStreamer(AiSearchService aiSearchService,
                            ResultViewAssembler resultViewAssembler,
                            ITemplateEngine templateEngine,
                            ObjectMapper objectMapper,
                            @Qualifier("aiStreamExecutor") TaskExecutor executor) {
        this.aiSearchService = aiSearchService;
        this.resultViewAssembler = resultViewAssembler;
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    // 브라우저가 연결을 끊으면 send 가 IOException 을 낸다. 그때 루프를 멈춰서 Claude 호출(=비용)을 더 쓰지 않는다.
    static class ClientGoneException extends RuntimeException {
        ClientGoneException(Throwable cause) {
            super(cause);
        }
    }

    public SseEmitter start(String query) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitter.onTimeout(emitter::complete);
        try {
            executor.execute(() -> run(query, emitter));
        } catch (TaskRejectedException e) {
            log.warn("AI 스트리밍 작업이 가득 참");
            return finished("지금 사용하는 사람이 많아요. 잠시 후 다시 시도해 주세요.");
        }
        return emitter;
    }

    // 검사에서 걸린 경우(빈 질문, 한도 초과 등): 안내 문장 하나만 보내고 바로 닫는다
    public SseEmitter finished(String answer) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        try {
            send(emitter, "done", done(answer, List.of()));
            emitter.complete();
        } catch (ClientGoneException e) {
            emitter.complete();
        }
        return emitter;
    }

    void run(String query, SseEmitter emitter) {
        try {
            AiSearchResult result = aiSearchService.search(query, new AiSearchListener() {
                @Override
                public void onStep(String label) {
                    send(emitter, "step", Map.of("text", label));
                }

                @Override
                public void onText(String delta) {
                    send(emitter, "token", Map.of("text", delta));
                }
            });
            List<OutputDto> outputs = resultViewAssembler.toOutputs(result.directions());
            send(emitter, "done", done(result.answer(), outputs));
            emitter.complete();
        } catch (ClientGoneException e) {
            log.info("사용자가 AI 검색 도중 연결을 끊음. query={}", query);
            emitter.complete();
        } catch (RuntimeException e) {
            log.error("AI 스트리밍 실패. query={}", query, e);
            try {
                send(emitter, "done", done("AI 응답을 받지 못했어요. 잠시 후 다시 시도하거나 주소 검색을 이용해 주세요.", List.of()));
            } catch (ClientGoneException ignored) {
                // 이미 끊긴 연결
            }
            emitter.complete();
        }
    }

    private Map<String, Object> done(String answer, List<OutputDto> outputs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer);
        data.put("count", outputs.size());
        data.put("html", outputs.isEmpty() ? "" : renderCards(outputs));
        return data;
    }

    // fragments.html 의 pharmacy-section 조각만 HTML 문자열로 그린다 (일반 결과 화면과 같은 카드)
    String renderCards(List<OutputDto> outputs) {
        Context context = new Context(Locale.KOREAN);
        context.setVariable("outputs", outputs);
        return templateEngine.process("fragments", Set.of("pharmacy-section"), context);
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // IOException: 브라우저가 떠남, IllegalStateException: 이미 complete/timeout 된 emitter
            throw new ClientGoneException(e);
        }
    }
}
