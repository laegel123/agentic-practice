package com.legacy.shop.batch.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읽기 경계 명시화([ADR-0008]) 회귀 테스트.
 *
 * <p>batch 의 리포지토리는 공유 shop DB([ADR-0002])의 <b>읽기 전용 소비자</b>다. {@link ReadOnlyRepository}
 * 로 좁힌 결과, {@code save}/{@code saveAll}/{@code delete}/{@code deleteAll}/{@code flush} 같은 쓰기
 * 메서드가 API 표면에 없어야 한다(타입 차원 제거). 누군가 베이스를 다시 {@code JpaRepository} 로
 * 되돌리면 이 테스트가 깨져 경계가 풀린 걸 알린다.
 *
 * <p>순수 리플렉션 검증 — 스프링 컨텍스트/DB 기동 없음.
 */
class ReadOnlyBoundaryTest {

    private static final List<Class<?>> READ_ONLY_REPOSITORIES = List.of(
            ReadOnlyRepository.class,
            OrderRowRepository.class,
            CartRowRepository.class,
            InventoryRowRepository.class);

    /** 노출되면 공유 DB 쓰기가 가능해지는 mutator 접두사들. */
    private static final List<String> WRITE_METHOD_PREFIXES = List.of(
            "save", "delete", "remove", "insert", "update", "flush", "persist", "merge");

    @Test
    void readOnlyRepositories_exposeNoWriteMethods() {
        for (Class<?> repo : READ_ONLY_REPOSITORIES) {
            List<String> writeMethods = Arrays.stream(repo.getMethods())
                    .map(Method::getName)
                    .filter(name -> WRITE_METHOD_PREFIXES.stream().anyMatch(name::startsWith))
                    .toList();

            assertThat(writeMethods)
                    .as("%s 는 쓰기 메서드를 노출하면 안 된다(읽기 전용 소비자)", repo.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void readOnlyRepositories_stillExposeFindAll() {
        for (Class<?> repo : READ_ONLY_REPOSITORIES) {
            boolean hasFindAll = Arrays.stream(repo.getMethods())
                    .anyMatch(m -> m.getName().equals("findAll"));

            assertThat(hasFindAll)
                    .as("%s 는 읽기 메서드 findAll 을 제공해야 한다", repo.getSimpleName())
                    .isTrue();
        }
    }
}
