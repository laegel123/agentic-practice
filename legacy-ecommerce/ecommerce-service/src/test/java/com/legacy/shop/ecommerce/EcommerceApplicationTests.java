package com.legacy.shop.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링 컨텍스트가 정상 로드되는지 확인하는 스모크 테스트(전체 빈 배선 검증).
 * 인메모리 H2 프로파일("test")로 실행해 운영 파일 DB(~/legacyshopdb)를 건드리지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class EcommerceApplicationTests {

    @Test
    void contextLoads() {
        // 컨텍스트 로딩 자체가 검증 대상.
    }
}
