package com.legacy.shop.core.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PageRequestDto.getOffset 의 동작 고정.
 *
 * B5 수정(2026-06-16): page 는 1-based 이므로 offset 은 (page-1)*size 여야 한다.
 * (이전) page*size 라 첫 페이지(page=1)에서 size 만큼 건너뛰어 첫 페이지를 통째로 누락했다.
 * (docs/known-issues.md B5) — core-framework 모듈 첫 테스트.
 */
class PageRequestDtoTest {

    @Test
    void firstPage_offsetIsZero_notSkipped() {
        PageRequestDto dto = new PageRequestDto();
        dto.setPage(1);
        dto.setSize(5);

        // 1-based 첫 페이지는 0번부터 조회해야 한다. (수정 전이라면 5 → 첫 5건을 건너뜀)
        assertThat(dto.getOffset()).isZero();
    }

    @Test
    void secondPage_offsetIsOnePageSize() {
        PageRequestDto dto = new PageRequestDto();
        dto.setPage(2);
        dto.setSize(5);

        assertThat(dto.getOffset()).isEqualTo(5);
    }

    @Test
    void thirdPage_offsetIsTwoPageSizes() {
        PageRequestDto dto = new PageRequestDto();
        dto.setPage(3);
        dto.setSize(20);

        assertThat(dto.getOffset()).isEqualTo(40);
    }

    @Test
    void defaults_firstPageSizeTwenty_offsetZero() {
        // 기본값(page=1, size=20)도 첫 페이지이므로 offset 0.
        assertThat(new PageRequestDto().getOffset()).isZero();
    }

    @Test
    void zeroPage_clampsToFirstPage_offsetZero_notNegative() {
        // B5 후속(리뷰 차단): page=0 은 첫 페이지로 정규화돼 offset 0.
        // (클램프 전이라면 (0-1)*20 = -20 → 호출부 subList 음수 인덱스로 500 크래시)
        PageRequestDto dto = new PageRequestDto();
        dto.setPage(0);
        dto.setSize(20);

        assertThat(dto.getOffset()).isZero();
    }

    @Test
    void negativePage_clampsToFirstPage_offsetZero_notNegative() {
        // page 음수도 첫 페이지로 정규화돼 offset 은 절대 음수가 되지 않는다.
        PageRequestDto dto = new PageRequestDto();
        dto.setPage(-5);
        dto.setSize(20);

        assertThat(dto.getOffset()).isZero();
    }
}
