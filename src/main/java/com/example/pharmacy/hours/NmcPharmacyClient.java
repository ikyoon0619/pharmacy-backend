package com.example.pharmacy.hours;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// 공공데이터포털 "국립중앙의료원_전국 약국 정보 조회 서비스" 호출.
// 응답이 XML 이라 JDK 기본 DOM 파서로 필요한 태그만 꺼낸다 (의존성 추가 없음).
@Slf4j
@Component
public class NmcPharmacyClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceKey;

    public NmcPharmacyClient(RestClient.Builder builder,
                             @Value("${opendata.base-url}") String baseUrl,
                             @Value("${opendata.service-key:}") String serviceKey) {
        this.restClient = builder.build();
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public boolean hasKey() {
        return !serviceKey.isBlank();
    }

    public record Page(int totalCount, List<PharmacyHours> items) {
    }

    // 전국 약국 목록 한 페이지 (조건 없이 부르면 전체를 페이지로 나눠 준다)
    public Page fetchPage(int pageNo, int numOfRows) {
        // 공공데이터 키에는 + / = 가 들어 있어서 직접 인코딩한다. (스프링이 + 를 인코딩하지 않아 "등록되지 않은 키" 오류가 나는 흔한 문제)
        String url = baseUrl + "/getParmacyListInfoInqire"
                + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;
        byte[] body = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
        return parse(body);
    }

    static Page parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 외부 엔티티를 막는다 (XXE 방지)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

            String resultCode = text(doc.getDocumentElement(), "resultCode");
            if (resultCode != null && !"00".equals(resultCode)) {
                String msg = text(doc.getDocumentElement(), "resultMsg");
                throw new IllegalStateException("공공데이터 오류 " + resultCode + ": " + msg);
            }
            // 키가 틀리면 <OpenAPI_ServiceResponse> 로 다른 모양의 오류가 온다
            String authMsg = text(doc.getDocumentElement(), "returnAuthMsg");
            if (authMsg != null) {
                throw new IllegalStateException("공공데이터 인증 오류: " + authMsg);
            }

            String total = text(doc.getDocumentElement(), "totalCount");
            NodeList nodes = doc.getElementsByTagName("item");
            List<PharmacyHours> items = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                PharmacyHours row = toRow((Element) nodes.item(i));
                if (row != null) {
                    items.add(row);
                }
            }
            return new Page(total == null ? items.size() : Integer.parseInt(total), items);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("공공데이터 응답을 읽지 못했습니다", e);
        }
    }

    private static PharmacyHours toRow(Element item) {
        String hpid = text(item, "hpid");
        String lat = text(item, "wgs84Lat");
        String lng = text(item, "wgs84Lon");
        if (hpid == null || lat == null || lng == null) {
            return null; // 좌표가 없으면 거리 검색을 못 하니 건너뛴다
        }
        WeeklyHours hours = WeeklyHours.empty();
        for (int day = 1; day <= 8; day++) {
            hours.set(day, text(item, "dutyTime" + day + "s"), text(item, "dutyTime" + day + "c"));
        }
        return PharmacyHours.builder()
                .hpid(hpid)
                .name(text(item, "dutyName"))
                .address(text(item, "dutyAddr"))
                .tel(text(item, "dutyTel1"))
                .latitude(Double.parseDouble(lat))
                .longitude(Double.parseDouble(lng))
                .weeklyHours(hours.serialize())
                .build();
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        String value = list.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.strip();
    }
}
