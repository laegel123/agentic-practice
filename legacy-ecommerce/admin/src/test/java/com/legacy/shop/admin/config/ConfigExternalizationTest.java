package com.legacy.shop.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5/[ADR-0007]: admin 의 호출 대상 서비스 주소(ecommerce/payment)가 환경변수로 외부 주입되며,
 * 미설정 시 외부화 이전의 하드코딩 리터럴과 동일한 로컬 기본값으로 떨어지는지 고정한다(동작 보존).
 *
 * <p>application.yml 을 그대로 로드해 placeholder 해석만 검증한다 — 앱 컨텍스트·웹서버·DB 기동 없음.
 * 외부화는 "기본값은 종전 그대로, 환경변수가 있으면 그것을 쓴다" 가 핵심이라 두 방향을 모두 박는다.
 * 기본값 리터럴이 드리프트하거나(예: 포트 오타) 환경변수 키 이름이 바뀌면 이 테스트가 깨진다.
 */
class ConfigExternalizationTest {

    /** application.yml 을 로드한 Environment 를 만든다. overrides 는 환경변수(높은 우선순위)를 흉내낸다. */
    private static StandardEnvironment envWith(Map<String, Object> overrides) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        if (!overrides.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("override", overrides));
        }
        List<PropertySource<?>> yml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yml.forEach(env.getPropertySources()::addLast);
        return env;
    }

    @Test
    void serviceUrls_defaultToLocalhost_whenEnvUnset() throws IOException {
        StandardEnvironment env = envWith(Map.of());

        assertThat(env.getProperty("ecommerce.base-url")).isEqualTo("http://localhost:8081");
        assertThat(env.getProperty("payment.base-url")).isEqualTo("http://localhost:8082");
    }

    @Test
    void serviceUrls_honorEnvOverride() throws IOException {
        StandardEnvironment env = envWith(Map.of(
                "ECOMMERCE_BASE_URL", "http://ecom.internal:9001",
                "PAYMENT_BASE_URL", "http://pay.internal:9002"));

        assertThat(env.getProperty("ecommerce.base-url")).isEqualTo("http://ecom.internal:9001");
        assertThat(env.getProperty("payment.base-url")).isEqualTo("http://pay.internal:9002");
    }
}
