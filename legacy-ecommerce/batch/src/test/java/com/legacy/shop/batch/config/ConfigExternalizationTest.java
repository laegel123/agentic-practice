package com.legacy.shop.batch.config;

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
 * R5/[ADR-0007]: batch 의 datasource 가 환경변수로 외부 주입되며, 미설정 시 외부화 이전의 하드코딩
 * 리터럴과 동일한 기본값으로 떨어지는지 고정한다(동작 보존).
 *
 * <p>핵심: batch 는 ecommerce 와 '같은' shop DB 를 공유하므로 ecommerce 와 <b>동일한 SHOP_DB_*</b>
 * 환경변수를 읽어야 한다 — {@code SHOP_DB_URL} 을 주면 batch 의 datasource 도 그쪽을 가리킨다.
 * 이 공유 키가 곧 공유 결합의 외부 주입 지점이며, 향후 DB 분리([ADR-0002]) 시 갈라지는 지점이다.
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
    void shopDb_defaults_whenEnvUnset() throws IOException {
        StandardEnvironment env = envWith(Map.of());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:h2:file:~/legacyshopdb;AUTO_SERVER=TRUE");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("sa");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("");
    }

    @Test
    void datasource_isDeclaredReadOnly() throws IOException {
        // 읽기 경계 명시화([ADR-0008]): batch 는 공유 shop DB 의 읽기 소비자 — 커넥션이 read-only 다.
        StandardEnvironment env = envWith(Map.of());

        assertThat(env.getProperty("spring.datasource.hikari.read-only", Boolean.class)).isTrue();
    }

    @Test
    void shopDb_honorsSharedShopDbEnvVar() throws IOException {
        // ecommerce 와 같은 SHOP_DB_URL 을 읽는다(공유 결합) — 주입하면 batch 도 같은 DB 를 가리킨다.
        StandardEnvironment env = envWith(Map.of(
                "SHOP_DB_URL", "jdbc:postgresql://db:5432/shop",
                "SHOP_DB_USERNAME", "shop_app",
                "SHOP_DB_PASSWORD", "s3cret"));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db:5432/shop");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("shop_app");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cret");
    }
}
