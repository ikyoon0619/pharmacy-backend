package com.example.pharmacy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 애플리케이션이 뜨는지만 확인 (진짜 키 없이 가짜 값 사용)
@SpringBootTest(properties = "kakao.rest-api-key=test-key")
class PharmacyApplicationTests {

    @Test
    void contextLoads() {
    }
}