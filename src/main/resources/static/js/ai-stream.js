// /ai?q=... 화면: POST /ai-search/stream 을 열고, SSE 이벤트가 올 때마다 화면을 채운다.
//
// 브라우저 기본 EventSource 는 GET 만 되므로, fetch 로 응답 본문(스트림)을 직접 읽는다.
// SSE 형식: 이벤트 하나 = "event:이름\ndata:JSON\n\n" (빈 줄로 구분)
(function () {
    var root = document.getElementById("ai-live");
    if (!root) return;

    var query = root.dataset.query;
    var steps = document.getElementById("ai-steps");
    var answer = document.getElementById("ai-answer");
    var answerBox = answer.parentElement;
    var results = document.getElementById("ai-results");
    var writing = false;   // "답변을 쓰고 있어요" 단계를 이미 넣었는지
    var finished = false;

    // 진행 단계 한 줄 추가. 앞 단계는 완료(✓)로 바꾼다.
    function addStep(text) {
        var active = steps.querySelector(".is-active");
        if (active) {
            active.classList.remove("is-active");
            active.classList.add("is-done");
        }
        var li = document.createElement("li");
        li.className = "ai-step is-active";
        li.textContent = text;   // textContent: AI 가 준 글자를 HTML 로 해석하지 않는다
        steps.appendChild(li);
    }

    function finishSteps() {
        steps.querySelectorAll(".ai-step").forEach(function (li) {
            li.classList.remove("is-active");
            li.classList.add("is-done");
        });
    }

    function unlockForm() {
        document.querySelectorAll("button.is-loading").forEach(function (b) {
            b.disabled = false;
            b.classList.remove("is-loading");
            b.querySelector(".label").textContent = b.dataset.label;
        });
        document.querySelectorAll(".status").forEach(function (s) { s.hidden = true; });
    }

    function onEvent(name, data) {
        if (name === "step") {
            // 도구를 부르기 전에 Claude 가 쓴 짧은 말("찾아볼게요")은 지우고, 단계로 보여 준다
            answer.textContent = "";
            writing = false;
            addStep(data.text);
        } else if (name === "token") {
            if (!writing) {
                writing = true;
                addStep("답변을 쓰고 있어요");
            }
            answer.textContent += data.text;
        } else if (name === "done") {
            finished = true;
            finishSteps();
            answer.textContent = data.answer;   // 최종 문장으로 한 번 더 맞춘다 (거절, 한도 안내 등)
            answerBox.classList.remove("is-streaming");
            results.innerHTML = data.html;      // 서버(Thymeleaf)가 그린 카드. 값은 서버에서 이스케이프됨
            if (data.count > 0) document.title = "약국 " + data.count + "곳 · 약국찾기";
        }
    }

    // "event:step\ndata:{...}" 블록 하나를 해석
    function parseBlock(block) {
        var name = "message";
        var data = [];
        block.split(/\r?\n/).forEach(function (line) {
            if (line.indexOf("event:") === 0) name = line.slice(6).trim();
            else if (line.indexOf("data:") === 0) data.push(line.slice(5).replace(/^ /, ""));
        });
        if (data.length) onEvent(name, JSON.parse(data.join("\n")));
    }

    function fail() {
        finishSteps();
        answerBox.classList.remove("is-streaming");
        answer.textContent = "연결이 끊겼어요. 잠시 후 다시 시도하거나 주소 검색을 이용해 주세요.";
    }

    addStep("질문을 읽고 있어요");

    fetch("/ai-search/stream", {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            "Accept": "text/event-stream"
        },
        body: "query=" + encodeURIComponent(query)
    }).then(function (res) {
        if (!res.ok || !res.body) throw new Error("HTTP " + res.status);
        var reader = res.body.getReader();
        var decoder = new TextDecoder();
        var buffer = "";

        function pump() {
            return reader.read().then(function (chunk) {
                if (chunk.done) {
                    if (buffer.trim()) parseBlock(buffer);
                    if (!finished) fail();
                    return;
                }
                // 네트워크 조각은 이벤트 경계와 상관없이 잘려 온다. 빈 줄이 나올 때까지 모아서 처리한다.
                buffer += decoder.decode(chunk.value, {stream: true});
                var blocks = buffer.split(/\r?\n\r?\n/);
                buffer = blocks.pop();
                blocks.forEach(parseBlock);
                return pump();
            });
        }
        return pump();
    }).catch(fail).then(unlockForm);
})();
