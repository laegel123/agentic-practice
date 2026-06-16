package com.legacy.shop.admin.security;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * AdminAuth 토큰 검증 동작 고정 (리뷰 후속).
 *
 * <p>핵심 회귀: (1) 토큰을 외부 주입하지 않으면(빈/널) <b>기동이 실패</b>해야 한다(fail-closed) —
 * 과거의 {@code admin-secret} 공개 기본값(fail-open)을 되살리지 못하게 막는다. (2) check 는 올바른
 * 토큰만 통과시키고 그 외엔 {@code UNAUTHORIZED} 를 던진다(상수시간 비교).
 */
class AdminAuthTest {

    @Test
    void construct_withBlankToken_failsClosed() {
        // 빈 토큰(설정 누락)으로는 빈 생성 자체가 막혀야 한다 — 공개 기본값 fail-open 부활 차단.
        assertThatThrownBy(() -> new AdminAuth(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdminAuth("   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdminAuth(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void check_acceptsCorrectToken() {
        AdminAuth auth = new AdminAuth("secret-token");
        assertThatNoException().isThrownBy(() -> auth.check("secret-token"));
    }

    @Test
    void check_rejectsWrongOrMissingToken_withUnauthorized() {
        AdminAuth auth = new AdminAuth("secret-token");

        for (String bad : new String[] {null, "", "wrong", "secret-toke", "secret-token "}) {
            BusinessException ex = catchThrowableOfType(BusinessException.class,
                    () -> auth.check(bad));
            assertThat(ex).as("token=%s 는 거부돼야 한다", bad).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        }
    }
}
