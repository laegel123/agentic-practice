package com.legacy.shop.admin.security;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 어드민 토큰 검증. 어드민 API 는 X-Admin-Token 헤더로 인증한다.
 */
@Component
public class AdminAuth {

    private final String token;

    public AdminAuth(@Value("${admin.token:admin-secret}") String token) {
        this.token = token;
    }

    public void check(String provided) {
        if (provided == null || !provided.equals(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
