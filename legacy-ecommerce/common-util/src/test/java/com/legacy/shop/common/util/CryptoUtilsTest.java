package com.legacy.shop.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CryptoUtils 보안 회귀 테스트 (CU1).
 *
 * <p>이전엔 {@code hashPassword} 가 MD5·무 salt 였다. 이 테스트는 그 취약 동작이 아니라
 * 교체된 안전 동작(PBKDF2·임의 salt·self-describing 형식 + 레거시 MD5 검증 폴백)을 고정한다.
 */
class CryptoUtilsTest {

    @Test
    @DisplayName("hashPassword 는 더 이상 MD5(32자리 hex)가 아니라 PBKDF2 self-describing 형식이다")
    void hashPassword_isPbkdf2_notMd5() {
        String hash = CryptoUtils.hashPassword("password1");
        assertThat(hash).startsWith("pbkdf2$");
        assertThat(hash).doesNotMatch("^[0-9a-f]{32}$");                  // 과거 MD5 형식이 아님
        assertThat(hash).isNotEqualTo(CryptoUtils.md5("password1"));
    }

    @Test
    @DisplayName("같은 비밀번호라도 salt 때문에 해시가 매번 달라진다 (무 salt 아님)")
    void hashPassword_isSalted_soNonDeterministic() {
        assertThat(CryptoUtils.hashPassword("password1"))
                .isNotEqualTo(CryptoUtils.hashPassword("password1"));
    }

    @Test
    @DisplayName("verifyPassword: 올바른 비밀번호는 통과, 틀린 비밀번호는 실패")
    void verifyPassword_matchesCorrect_rejectsWrong() {
        String hash = CryptoUtils.hashPassword("password1");
        assertThat(CryptoUtils.verifyPassword("password1", hash)).isTrue();
        assertThat(CryptoUtils.verifyPassword("wrong", hash)).isFalse();
    }

    @Test
    @DisplayName("레거시 MD5 로 저장된 해시도 verifyPassword 로 검증된다 (점진 마이그레이션)")
    void verifyPassword_supportsLegacyMd5() {
        String legacy = CryptoUtils.md5("password1");
        assertThat(CryptoUtils.verifyPassword("password1", legacy)).isTrue();
        assertThat(CryptoUtils.verifyPassword("wrong", legacy)).isFalse();
    }

    @Test
    @DisplayName("needsRehash: 레거시 MD5 해시는 재해시 필요, PBKDF2 해시는 불필요")
    void needsRehash_trueForLegacy_falseForPbkdf2() {
        assertThat(CryptoUtils.needsRehash(CryptoUtils.md5("password1"))).isTrue();
        assertThat(CryptoUtils.needsRehash(CryptoUtils.hashPassword("password1"))).isFalse();
    }

    @Test
    @DisplayName("verifyPassword: null/빈 입력은 안전하게 false")
    void verifyPassword_nullSafe() {
        assertThat(CryptoUtils.verifyPassword(null, "x")).isFalse();
        assertThat(CryptoUtils.verifyPassword("x", null)).isFalse();
        assertThat(CryptoUtils.verifyPassword("x", "")).isFalse();
    }
}
