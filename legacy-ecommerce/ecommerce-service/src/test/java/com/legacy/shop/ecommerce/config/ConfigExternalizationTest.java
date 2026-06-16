package com.legacy.shop.ecommerce.config;

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
 * R5/[ADR-0007]: ecommerce 의 공유 shop DB 접속정보(SHOP_DB_*)와 결제 서비스 주소(PAYMENT_BASE_URL)가
 * 환경변수로 외부 주입되며, 미설정 시 외부화 이전의 하드코딩 리터럴과 동일한 기본값으로 떨어지는지
 * 고정한다(동작 보존).
 *
 * <p>application.yml 만 로드해 placeholder 해석을 검증한다 — 컨텍스트·DB 기동 없음. (테스트 프로파일
 * {@code application-test.yml} 이 datasource 를 인메모리로 덮어쓰므로, 외부화 기본값 자체는 여기서만
 * 직접 확인된다.)
 */
class ConfigExternalizationTest {

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
    void shopDbAndPaymentUrl_defaults_whenEnvUnset() throws IOException {
        StandardEnvironment env = envWith(Map.of());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:h2:file:~/legacyshopdb;AUTO_SERVER=TRUE");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("sa");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("");
        assertThat(env.getProperty("payment.base-url")).isEqualTo("http://localhost:8082");
    }

    @Test
    void shopDbAndPaymentUrl_honorEnvOverride() throws IOException {
        StandardEnvironment env = envWith(Map.of(
                "SHOP_DB_URL", "jdbc:postgresql://db:5432/shop",
                "SHOP_DB_USERNAME", "shop_app",
                "SHOP_DB_PASSWORD", "s3cret",
                "PAYMENT_BASE_URL", "http://pay.internal:9002"));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db:5432/shop");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("shop_app");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cret");
        assertThat(env.getProperty("payment.base-url")).isEqualTo("http://pay.internal:9002");
    }
}
