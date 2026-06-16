package com.legacy.shop.batch.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * batch 전용 <b>읽기 전용</b> 리포지토리 베이스.
 *
 * <p>공유 shop DB([ADR-0002])의 스키마 소유자는 ecommerce 이고 batch 는 <b>읽기 소비자</b>다
 * (batch/CLAUDE.md "잡은 읽기 전용으로 유지한다"). {@code JpaRepository}/{@code CrudRepository} 를
 * 직접 상속하면 {@code save}/{@code saveAll}/{@code delete}/{@code deleteAll}/{@code flush} 가 API
 * 표면에 노출되어, batch 가 소유하지도 않은 공유 DB 에 실수로 쓰는 길이 열린다.
 *
 * <p>그래서 marker {@link Repository} 만 상속하고 batch 가 실제 쓰는 읽기 메서드만 선언해
 * <b>쓰기 메서드를 타입 차원에서 제거</b>한다(읽기 경계 명시화 — [ADR-0008]). 공유 DB 에 쓰기가
 * 필요해지면 먼저 스키마 소유권·동시성([ADR-0002])을 검토한 뒤 의도적으로 메서드를 추가해야 한다.
 *
 * <p>{@code @NoRepositoryBean} 이라 이 인터페이스 자체로는 Spring Data 빈이 만들어지지 않는다.
 *
 * @param <T>  엔티티 타입(읽기 전용 프로젝션 {@code *Row})
 * @param <ID> 식별자 타입
 */
@NoRepositoryBean
public interface ReadOnlyRepository<T, ID> extends Repository<T, ID> {

    List<T> findAll();

    Optional<T> findById(ID id);
}
