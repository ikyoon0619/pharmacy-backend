package com.example.pharmacy.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.example.pharmacy.hours.PharmacyHoursService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// 자연어 질문 -> Claude Tool Use -> 카카오 검색 -> 약국 추천.
//
// 흐름 (에이전트 루프):
//   1. 질문 + 도구 목록을 Claude 에게 보낸다
//   2. Claude 가 tool_use 로 "이 도구를 이 값으로 불러 줘" 라고 답한다
//   3. 서버가 도구를 실행하고 결과를 tool_result 로 돌려준다
//   4. Claude 가 더 부를 도구가 없으면(end_turn) 마지막 안내 문장을 쓴다
// 무한 반복을 막으려고 최대 MAX_TURNS 번만 돈다.
@Slf4j
@Service
public class AiSearchService {

    static final int MAX_QUERY_LENGTH = 200;
    static final int MAX_TURNS = 4;
    static final long MAX_TOKENS = 2048L;

    static final String SYSTEM_PROMPT = """
            너는 '가까운 약국 찾기' 서비스의 도우미다. 사용자의 문장에서 위치를 찾아 근처 약국을 찾아 준다.

            순서
            1. find_location 으로 사용자가 말한 장소를 좌표로 바꾼다.
            2. search_pharmacies 로 그 좌표 주변 약국을 찾는다.
            3. 결과를 한국어 2~3문장으로 짧게 안내한다. 약국 표는 화면에 따로 나오니 목록을 길게 반복하지 않는다.

            규칙
            - 문장에 위치가 없으면 도구를 부르지 말고, 어디 근처에서 찾을지 한 문장으로 되묻는다.
            - 반경: '바로 앞', '걸어서' 같은 말이면 1000, 특별한 말이 없으면 3000, '멀어도 괜찮아' 같은 말이면 10000.
            - 개수: 특별한 말이 없으면 3, 최대 5.
            %s
            - 약 추천, 진단 같은 의료 조언은 하지 않는다. 증상을 말하면 약사와 상담하라고 안내하고,
              숨이 차거나 가슴 통증처럼 위급해 보이면 119 나 응급실을 먼저 안내한다.
            - 약국 이름, 주소, 거리는 도구 결과에 있는 것만 말한다.
            - 약국 찾기와 상관없는 요청에는 이 서비스는 약국 찾기만 돕는다고 짧게 답한다.
            """;

    // 영업시간 데이터가 있을 때 / 없을 때 규칙
    static final String HOURS_RULE_ON = """
            - 영업시간: '지금 문 연', '24시', '일요일 밤', '새벽', '공휴일' 처럼 시간 조건이 있으면 search_pharmacies 대신 search_open_pharmacies 를 쓴다.
              '지금'은 사용자 메시지 앞의 [현재 한국 시각] 을 기준으로 요일 번호와 HHMM 을 정한다. '오늘이 공휴일'인지는 알 수 없으니
              사용자가 공휴일이라고 말할 때만 day=8 을 쓴다. 결과가 0곳이면 반경을 넓혀 한 번 더 찾아본다.
              영업시간은 공공데이터 기준이라 실제와 다를 수 있으니 방문 전에 전화로 확인하라고 한 문장 덧붙인다.""";
    static final String HOURS_RULE_OFF = """
            - 이 서비스에는 지금 영업시간 데이터가 없다. '지금 문 연', '24시', '일요일', '심야' 같은 조건을 물으면
              가까운 약국은 찾아 주되, 영업 여부는 확인할 수 없으니 방문 전에 전화로 확인하라고 분명히 말한다. 영업 여부를 지어내지 않는다.""";

    private final Optional<AnthropicClient> client;
    private final PharmacyTools tools;
    private final PharmacyHoursService hoursService;
    private final String model;

    public AiSearchService(Optional<AnthropicClient> client,
                           PharmacyTools tools,
                           PharmacyHoursService hoursService,
                           @Value("${anthropic.model:claude-opus-5-5}") String model) {
        this.client = client;
        this.tools = tools;
        this.hoursService = hoursService;
        this.model = model;
    }

    private static final String[] DAY_KO = {"", "월", "화", "수", "목", "금", "토", "일"};

    // 키가 없으면 AI 검색은 꺼진 상태 (주소 검색은 그대로 동작)
    public boolean isEnabled() {
        return client.isPresent();
    }

    public AiSearchResult search(String query) {
        if (!isEnabled()) {
            return AiSearchResult.message("AI 검색이 지금은 꺼져 있어요. 아래 주소 검색을 이용해 주세요.");
        }

        boolean hoursOn = hoursService.isAvailable();
        PharmacyHoursService.Now now = hoursService.now();
        // 시스템 프롬프트는 고정(캐시에 유리)하고, 바뀌는 현재 시각은 사용자 메시지 앞에 붙인다
        String userMessage = "[현재 한국 시각: %s %s요일(요일 번호 %d) %02d:%02d]\n%s".formatted(
                now.dateTime().toLocalDate(), DAY_KO[now.day()], now.day(), now.hhmm() / 100, now.hhmm() % 100, query);

        MessageCreateParams.Builder request = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT.formatted(hoursOn ? HOURS_RULE_ON : HOURS_RULE_OFF))
                // 간단한 길찾기라 깊게 생각할 필요가 없다. effort 를 낮춰 비용과 대기 시간을 줄인다.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(userMessage);
        tools.definitions().forEach(request::addTool);
        if (hoursOn) {
            request.addTool(tools.openHoursDefinition());
        }

        PharmacyTools.Session session = new PharmacyTools.Session();
        try {
            for (int turn = 0; turn < MAX_TURNS; turn++) {
                Message response = client.get().messages().create(request.build());
                request.addMessage(response); // 다음 요청에 Claude 의 답(도구 호출 포함)을 그대로 이어 붙인다

                StopReason stop = response.stopReason().orElse(StopReason.END_TURN);
                if (StopReason.TOOL_USE.equals(stop)) {
                    request.addUserMessageOfContentBlockParams(runTools(response, session));
                    continue;
                }
                if (StopReason.REFUSAL.equals(stop)) {
                    return new AiSearchResult("이 질문에는 답할 수 없어요. 약국을 찾을 위치를 알려 주세요.",
                            session.directions());
                }
                return new AiSearchResult(textOf(response), session.directions());
            }
            log.warn("AI 검색이 {}번 안에 끝나지 않음. query={}", MAX_TURNS, query);
            return new AiSearchResult("검색을 끝까지 마치지 못했어요. 장소를 조금 더 구체적으로 적어 주세요.",
                    session.directions());
        } catch (AnthropicException e) {
            log.error("Claude API 호출 실패. query={}", query, e);
            return AiSearchResult.message("AI 응답을 받지 못했어요. 잠시 후 다시 시도하거나 주소 검색을 이용해 주세요.");
        }
    }

    // 응답에 들어 있는 tool_use 를 모두 실행하고, 결과를 tool_result 묶음(하나의 user 메시지)으로 만든다
    private List<ContentBlockParam> runTools(Message response, PharmacyTools.Session session) {
        List<ContentBlockParam> results = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            Optional<ToolUseBlock> toolUse = block.toolUse();
            if (toolUse.isEmpty()) {
                continue;
            }
            ToolUseBlock call = toolUse.get();
            ToolResultBlockParam.Builder result = ToolResultBlockParam.builder().toolUseId(call.id());
            try {
                result.content(tools.execute(call.name(), call._input(), session));
            } catch (RuntimeException e) {
                // 도구가 실패해도 루프를 멈추지 않고, 실패했다고 Claude 에게 알려 준다
                log.warn("도구 실행 실패. tool={}", call.name(), e);
                result.content("도구 실행 중 오류가 났습니다: " + e.getMessage()).isError(true);
            }
            results.add(ContentBlockParam.ofToolResult(result.build()));
        }
        return results;
    }

    private static String textOf(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text().strip())
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\n"));
    }
}
