package com.example.pharmacy.ai;

import com.example.pharmacy.direction.dto.OutputDto;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.direction.view.ResultViewAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// SSE 로 무엇을 어떤 순서로 보내는지 검증한다. 실제 네트워크 대신 보낸 이벤트를 모아 두는 SseEmitter 를 쓴다.
@ExtendWith(MockitoExtension.class)
class AiSearchStreamerTest {

    @Mock
    private AiSearchService aiSearchService;
    @Mock
    private ResultViewAssembler resultViewAssembler;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiSearchStreamer streamer;

    // 보낸 이벤트를 (이름, JSON) 으로 모아 두는 가짜 emitter
    static class RecordingEmitter extends SseEmitter {
        final List<String> names = new ArrayList<>();
        final List<String> data = new ArrayList<>();
        boolean completed;
        int failAfter = Integer.MAX_VALUE;   // 이 개수만큼 보낸 뒤에는 "브라우저가 떠난" 것처럼 IOException

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (names.size() >= failAfter) {
                throw new IOException("Broken pipe");
            }
            String raw = builder.build().stream()
                    .map(d -> String.valueOf(d.getData()))
                    .reduce("", String::concat);
            // raw 예: "event:step\ndata:{...}\n\n"
            for (String line : raw.split("\n")) {
                if (line.startsWith("event:")) names.add(line.substring(6));
                if (line.startsWith("data:")) data.add(line.substring(5));
            }
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        // 운영과 같은 SpringTemplateEngine (식은 SpEL 로 해석)
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        // 테스트에서는 바로 실행(같은 스레드)
        streamer = new AiSearchStreamer(aiSearchService, resultViewAssembler, templateEngine, objectMapper, Runnable::run);
    }

    @Test
    void 진행_단계_답변_조각_마지막_결과를_순서대로_보낸다() throws Exception {
        Direction d = Direction.builder().id(1L).targetPharmacyName("온누리약국").build();
        given(aiSearchService.search(eq("강남역 약국"), any(AiSearchListener.class))).willAnswer(inv -> {
            AiSearchListener listener = inv.getArgument(1);
            listener.onStep("'강남역' 위치를 찾고 있어요");
            listener.onText("3곳을 ");
            listener.onText("찾았어요.");
            return new AiSearchResult("3곳을 찾았어요.", List.of(d));
        });
        given(resultViewAssembler.toOutputs(List.of(d))).willReturn(List.of(
                new OutputDto("온누리<약국>", "서울 강남구", "210m", "/dir/1",
                        "https://map.kakao.com/link/roadview/1,2", "02-555-0100", null,
                        "오늘(월) 09:00~22:00", true)));

        RecordingEmitter emitter = new RecordingEmitter();
        streamer.run("강남역 약국", emitter);

        assertThat(emitter.names).containsExactly("step", "token", "token", "done");
        assertThat(objectMapper.readValue(emitter.data.get(0), Map.class).get("text"))
                .isEqualTo("'강남역' 위치를 찾고 있어요");
        Map<?, ?> done = objectMapper.readValue(emitter.data.get(3), Map.class);
        assertThat(done.get("answer")).isEqualTo("3곳을 찾았어요.");
        assertThat(done.get("count")).isEqualTo(1);
        // 카드는 Thymeleaf 가 그린 HTML. 이름의 < > 는 이스케이프되어 태그가 되지 않는다
        String html = (String) done.get("html");
        assertThat(html).contains("가까운 약국", "tel:02-555-0100", "영업 중")
                .contains("온누리&lt;약국&gt;").doesNotContain("온누리<약국>");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void 브라우저가_떠나면_검색을_멈춘다() {
        List<String> afterLeave = new ArrayList<>();
        given(aiSearchService.search(eq("q"), any(AiSearchListener.class))).willAnswer(inv -> {
            AiSearchListener listener = inv.getArgument(1);
            listener.onStep("첫 단계");
            listener.onStep("두 번째 단계");   // 여기서 IOException -> 예외로 루프가 끊긴다
            afterLeave.add("이 줄까지 오면 안 됨");
            return AiSearchResult.message("x");
        });

        RecordingEmitter emitter = new RecordingEmitter();
        emitter.failAfter = 1;
        streamer.run("q", emitter);

        assertThat(emitter.names).containsExactly("step");
        assertThat(afterLeave).isEmpty();
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void 예상하지_못한_오류는_안내_문장으로_끝낸다() throws Exception {
        given(aiSearchService.search(eq("q"), any(AiSearchListener.class))).willThrow(new IllegalStateException("boom"));

        RecordingEmitter emitter = new RecordingEmitter();
        streamer.run("q", emitter);

        assertThat(emitter.names).containsExactly("done");
        assertThat(objectMapper.readValue(emitter.data.get(0), Map.class).get("answer").toString())
                .contains("AI 응답을 받지 못했어요");
    }

    @Test
    void 작업_스레드가_가득_차면_바로_안내한다() {
        AiSearchStreamer full = new AiSearchStreamer(aiSearchService, resultViewAssembler, mock(TemplateEngine.class),
                objectMapper, task -> {
                    throw new TaskRejectedException("full");
                });

        SseEmitter emitter = full.start("q");

        assertThat(emitter).isNotNull();
    }
}
