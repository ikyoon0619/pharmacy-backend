package com.example.pharmacy.ai;

// 에이전트 루프가 진행되는 동안 "지금 무엇을 하는지"를 밖으로 알려 주는 통로.
// SSE 화면은 이걸로 진행 단계와 답변 글자를 바로바로 받는다.
// 일반 검색(POST /ai-search)은 NONE 을 써서 아무것도 하지 않는다.
public interface AiSearchListener {

    AiSearchListener NONE = new AiSearchListener() {
    };

    // 도구를 부르기 직전. 예: "'강남역' 위치를 찾고 있어요"
    default void onStep(String label) {
    }

    // Claude 가 쓰는 답변의 글자 조각 (스트리밍)
    default void onText(String delta) {
    }
}
