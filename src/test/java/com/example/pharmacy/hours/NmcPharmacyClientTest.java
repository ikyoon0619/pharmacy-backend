package com.example.pharmacy.hours;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 실제 공공데이터 서버 대신 가짜 응답(XML)으로 확인한다
@RestClientTest(NmcPharmacyClient.class)
@TestPropertySource(properties = {
        "opendata.base-url=https://apis.data.go.kr/B552657/ErmctInsttInfoInqireService",
        "opendata.service-key=abc+def/ghi=="
})
class NmcPharmacyClientTest {

    @Autowired
    private NmcPharmacyClient client;

    @Autowired
    private MockRestServiceServer server;

    static final String OK = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <response>
              <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
              <body>
                <items>
                  <item>
                    <dutyAddr>서울특별시 강남구 테헤란로 1</dutyAddr>
                    <dutyName>온누리강남약국</dutyName>
                    <dutyTel1>02-555-0100</dutyTel1>
                    <dutyTime1c>2200</dutyTime1c><dutyTime1s>0900</dutyTime1s>
                    <dutyTime7c>0200</dutyTime7c><dutyTime7s>1900</dutyTime7s>
                    <hpid>C1100001</hpid>
                    <wgs84Lat>37.4985</wgs84Lat><wgs84Lon>127.0281</wgs84Lon>
                  </item>
                  <item>
                    <dutyName>좌표없는약국</dutyName><hpid>C1100002</hpid>
                  </item>
                </items>
                <numOfRows>1000</numOfRows><pageNo>1</pageNo><totalCount>23456</totalCount>
              </body>
            </response>
            """;

    @Test
    void 키를_직접_인코딩해서_보내고_XML을_읽는다() {
        // + / = 가 %2B %2F %3D 로 바뀌어야 "등록되지 않은 키" 오류가 나지 않는다
        server.expect(requestTo(containsString("serviceKey=abc%2Bdef%2Fghi%3D%3D")))
                .andRespond(withSuccess(OK.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_XML));

        NmcPharmacyClient.Page page = client.fetchPage(1, 1000);

        assertThat(page.totalCount()).isEqualTo(23456);
        assertThat(page.items()).hasSize(1); // 좌표 없는 줄은 건너뛴다
        PharmacyHours p = page.items().get(0);
        assertThat(p.getHpid()).isEqualTo("C1100001");
        assertThat(p.getName()).isEqualTo("온누리강남약국");
        assertThat(p.getLatitude()).isEqualTo(37.4985);
        assertThat(p.hours().label(1)).isEqualTo("09:00~22:00");
        assertThat(p.hours().isOpen(1, 130)).isTrue(); // 일요일 밤 영업이 월요일 새벽까지
    }

    @Test
    void 키_오류_응답이면_예외() {
        String error = """
                <OpenAPI_ServiceResponse><cmmMsgHeader>
                  <errMsg>SERVICE ERROR</errMsg>
                  <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                  <returnReasonCode>30</returnReasonCode>
                </cmmMsgHeader></OpenAPI_ServiceResponse>
                """;
        server.expect(requestTo(containsString("getParmacyListInfoInqire")))
                .andRespond(withSuccess(error.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_XML));

        assertThatThrownBy(() -> client.fetchPage(1, 10))
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }
}
