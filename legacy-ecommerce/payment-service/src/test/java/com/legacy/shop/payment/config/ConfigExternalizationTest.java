package com.legacy.shop.payment.config;

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
 * R5/[ADR-0007]: payment 전용 DB 접속정보(PAYMENT_DB_*)가 환경변수로 외부 주입되며, 미설정 시
 * 외부화 이전의 하드코딩 리터럴과 동일한 기본값으로 떨어지는지 고정한다(동작 보존).
 *
 * <p>application.yml 만 로드해 placeholder 해석을 검증한다 — 컨텍스트·DB 기동 없음.
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
    void paymentDb_defaults_whenEnvUnset() throws IOException {
        StandardEnvironment env = envWith(Map.of());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:h2:file:~/legacypaydb;AUTO_SERVER=TRUE");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("sa");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("");
    }

    @Test
    void paymentDb_honorEnvOverride() throws IOException {
        StandardEnvironment env = envWith(Map.of(
                "PAYMENT_DB_URL", "jdbc:postgresql://db:5432/pay",
                "PAYMENT_DB_USERNAME", "pay_app",
                "PAYMENT_DB_PASSWORD", "p4ss"));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db:5432/pay");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("pay_app");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p4ss");
    }
}
