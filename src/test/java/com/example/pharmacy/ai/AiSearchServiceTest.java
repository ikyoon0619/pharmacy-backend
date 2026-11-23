package com.example.pharmacy.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import com.example.pharmacy.direction.entity.Direction;
import com.example.pharmacy.hours.PharmacyHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 실제 Claude 를 부르지 않고, Claude 응답(JSON)을 미리 정해 두고 에이전트 루프를 검증한다
@ExtendWith(MockitoExtension.class)
class AiSearchServiceTest {

    @Mock
    private AnthropicClient client;
    @Mock
    private MessageService messages;
    @Mock
    private PharmacyTools tools;
    @Mock
    private PharmacyHoursService hoursService;

    private AiSearchService service;

    @BeforeEach
    void setUp() {
        // 2026-11-23 월요일 10:22 (한국)
        lenient().when(hoursService.now()).thenReturn(
                new PharmacyHoursService.Now(1, 1022, LocalDateTime.of(2026, 11, 23, 10, 22)));
        service = new AiSearchService(Optional.of(client), tools, hoursService, "claude-opus-5-5");
    }

    // Claude API 응답 모양 그대로 Message 를 만든다
    private static Message message(String stopReason, String contentJson) throws Exception {
        String json = """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":10},
                 "content":%s}
                """.formatted(stopReason, contentJson);
        return ObjectMappers.jsonMapper().readValue(json, Message.class);
    }

    @Test
    void 도구를_차례로_부르고_마지막_문장과_약국_목록을_돌려준다() throws Exception {
        Message callFind = message("tool_use", """
                [{"type":"tool_use","id":"tu_1","name":"find_location","input":{"query":"강남역"}}]""");
        Message callSearch = message("tool_use", """
                [{"type":"tool_use","id":"tu_2","name":"search_pharmacies",
                  "input":{"latitude":37.49,"longitude":127.02,"radius_meters":3000,"count":3}}]""");
        Message done = message("end_turn", """
                [{"type":"text","text":"강남역 근처 약국 3곳을 찾았어요."}]""");

        given(client.messages()).willReturn(messages);
        given(messages.create(any(MessageCreateParams.class))).willReturn(callFind, callSearch, done);
        given(tools.execute(eq("find_location"), any(), any()))
                .willReturn("{\"found\":true,\"latitude\":37.49,\"longitude\":127.02}");
        Direction pharmacy = Direction.builder().id(1L).targetPharmacyName("온누리약국").build();
        given(tools.execute(eq("search_pharmacies"), any(), any())).willAnswer(inv -> {
            // 실제 도구처럼 세션에 결과를 담는다
            PharmacyTools.Session session = inv.getArgument(2);
            var field = PharmacyTools.Session.class.getDeclaredField("directions");
            field.setAccessible(true);
            field.set(session, List.of(pharmacy));
            return "{\"count\":1}";
        });

        AiSearchResult result = service.search("강남역 근처 약국 찾아줘");

        assertThat(result.answer()).isEqualTo("강남역 근처 약국 3곳을 찾았어요.");
        assertThat(result.directions()).containsExactly(pharmacy);

        // 3번 호출했고, 마지막 요청에는 질문 + (도구 호출 + 결과) x 2 = 메시지 5개가 쌓여 있어야 한다
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(messages, times(3)).create(captor.capture());
        MessageCreateParams last = captor.getAllValues().get(2);
        assertThat(last.messages()).hasSize(5);
        assertThat(last.messages().get(2).toString()).contains("tu_1");
    }

    @Test
    void 위치가_없으면_도구_없이_되묻는다() throws Exception {
        given(client.messages()).willReturn(messages);
        given(messages.create(any(MessageCreateParams.class))).willReturn(message("end_turn", """
                [{"type":"text","text":"어디 근처에서 찾을까요?"}]"""));

        AiSearchResult result = service.search("약국 찾아줘");

        assertThat(result.answer()).isEqualTo("어디 근처에서 찾을까요?");
        assertThat(result.directions()).isEmpty();
        verify(tools, never()).execute(any(), any(), any());
    }

    @Test
    void 도구를_계속_부르면_최대_횟수에서_멈춘다() throws Exception {
        given(client.messages()).willReturn(messages);
        given(messages.create(any(MessageCreateParams.class))).willReturn(message("tool_use", """
                [{"type":"tool_use","id":"tu_x","name":"find_location","input":{"query":"x"}}]"""));
        given(tools.execute(any(), any(), any())).willReturn("{\"found\":false}");

        AiSearchResult result = service.search("어딘가");

        verify(messages, times(AiSearchService.MAX_TURNS)).create(any(MessageCreateParams.class));
        assertThat(result.answer()).contains("마치지 못했어요");
    }

    @Test
    void 도구가_실패하면_is_error_결과를_보내고_계속한다() throws Exception {
        given(client.messages()).willReturn(messages);
        given(messages.create(any(MessageCreateParams.class))).willReturn(
                message("tool_use", """
                        [{"type":"tool_use","id":"tu_1","name":"find_location","input":{"query":"x"}}]"""),
                message("end_turn", """
                        [{"type":"text","text":"위치 검색이 잠시 안 돼요."}]"""));
        given(tools.execute(any(), any(), any())).willThrow(new IllegalStateException("kakao down"));

        AiSearchResult result = service.search("x 근처 약국");

        assertThat(result.answer()).isEqualTo("위치 검색이 잠시 안 돼요.");
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(messages, times(2)).create(captor.capture());
        assertThat(captor.getAllValues().get(1).messages().get(2).toString()).contains("isError=true");
    }

    @Test
    void API_오류는_안내_문장으로_바꾼다() {
        given(client.messages()).willReturn(messages);
        given(messages.create(any(MessageCreateParams.class))).willThrow(new AnthropicIoException("timeout"));

        AiSearchResult result = service.search("강남역 약국");

        assertThat(result.answer()).contains("AI 응답을 받지 못했어요");
        assertThat(result.directions()).isEmpty();
    }

    @Test
    void 키가_없으면_꺼진_상태() {
        AiSearchService disabled = new AiSearchService(Optional.empty(), tools, hoursService, "claude-opus-5-5");

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.search("강남역").answer()).contains("꺼져 있어요");
    }

    @Test
    void 현재_한국_시각을_질문_앞에_붙이고_영업시간_도구는_데이터가_있을_때만_준다() throws Exception {
        given(client.messages()).willReturn(messages);
        given(messages.create(any(MessageCreateParams.class))).willReturn(message("end_turn", """
                [{"type":"text","text":"ok"}]"""));
        given(tools.definitions()).willReturn(List.of());
        given(tools.openHoursDefinition()).willReturn(com.anthropic.models.messages.Tool.builder()
                .name("search_open_pharmacies")
                .inputSchema(com.anthropic.models.messages.Tool.InputSchema.builder().build()).build());
        given(hoursService.isAvailable()).willReturn(true);

        service.search("지금 문 연 약국");

        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(messages).create(captor.capture());
        MessageCreateParams sent = captor.getValue();
        assertThat(sent.messages().get(0).toString()).contains("2026-11-23 월요일(요일 번호 1) 10:22");
        assertThat(sent.tools().orElseThrow().toString()).contains("search_open_pharmacies");
        assertThat(sent.system().orElseThrow().toString()).contains("search_open_pharmacies 를 쓴다");
    }

    @Test
    void 영업시간_데이터가_없으면_모른다고_하라는_규칙을_쓴다() throws Exception {
        given(client.messages()).willReturn(messages);
        given(messages.create(any(MessageCreateParams.class))).willReturn(message("end_turn", """
                [{"type":"text","text":"ok"}]"""));
        given(hoursService.isAvailable()).willReturn(false);

        service.search("지금 문 연 약국");

        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(messages).create(captor.capture());
        assertThat(captor.getValue().system().orElseThrow().toString()).contains("영업시간 데이터가 없다");
        verify(tools, never()).openHoursDefinition();
    }
}
