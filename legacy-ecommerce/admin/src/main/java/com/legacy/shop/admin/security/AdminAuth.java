package com.legacy.shop.admin.security;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 어드민 토큰 검증. 어드민 API 는 X-Admin-Token 헤더로 인증한다.
 *
 * <p>토큰은 <b>반드시 외부 주입</b>한다({@code admin.token} → 운영은 환경변수 {@code ADMIN_TOKEN}).
 * 과거엔 {@code @Value("${admin.token:admin-secret}")} 로 공개·고정된 기본값을 갖고 있어, 설정을 빼먹은
 * 환경에선 누구나 아는 {@code admin-secret} 로 인증이 뚫리는 fail-open 이었다. 기본값을 제거해 토큰이
 * 비어 있으면 <b>기동 자체가 실패</b>하도록(fail-closed) 바꿨다.
 */
@Component
public class AdminAuth {

    private final String token;

    public AdminAuth(@Value("${admin.token:}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "admin.token 미설정: 어드민 토큰을 외부 주입하세요(환경변수 ADMIN_TOKEN 등). "
                            + "기본 시크릿은 제공하지 않습니다(fail-closed).");
        }
        this.token = token;
    }

    public void check(String provided) {
        // 상수시간 비교 — provided.equals(token) 은 길이/접두 일치에 따라 조기 반환해 타이밍 사이드채널 여지가 있다.
        if (provided == null
                || !MessageDigest.isEqual(
                        provided.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
