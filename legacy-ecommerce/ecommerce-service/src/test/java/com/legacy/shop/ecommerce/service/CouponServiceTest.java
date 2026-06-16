package com.legacy.shop.ecommerce.service;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/**
 * CouponService.getValidCoupon 의 동작 고정.
 *
 * B4 수정(2026-06-16): 만료 검사가 `isBefore(오늘)` 이라 '만료일 당일' 쿠폰이 유효하다(이전에는
 * `!isAfter(오늘)` 라 당일 거부 = off-by-one). Coupon.expiryDate 주석 "만료일 당일 포함" 과 일치.
 * (docs/known-issues.md B4)
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponService couponService;

    private Coupon couponExpiring(LocalDate expiry) {
        Coupon c = new Coupon();
        c.setCode("SAVE10");
        c.setDiscountRate(0.1);
        c.setMinOrderAmount(BigDecimal.ZERO);
        c.setExpiryDate(expiry);
        return c;
    }

    @Test
    void expiryToday_isValid_inclusive() {
        Coupon valid = couponExpiring(LocalDate.now());
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(valid));

        // "만료일 당일 포함" — 당일 쿠폰은 유효(B4 수정 전에는 당일에 만료 처리되었다).
        assertThat(couponService.getValidCoupon("SAVE10")).isSameAs(valid);
    }

    @Test
    void expiryTomorrow_isValid() {
        Coupon valid = couponExpiring(LocalDate.now().plusDays(1));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(valid));

        assertThat(couponService.getValidCoupon("SAVE10")).isSameAs(valid);
    }

    @Test
    void expiryYesterday_isExpired() {
        when(couponRepository.findByCode("SAVE10"))
                .thenReturn(Optional.of(couponExpiring(LocalDate.now().minusDays(1))));

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> couponService.getValidCoupon("SAVE10"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COUPON_EXPIRED);
    }

    @Test
    void unknownCode_throwsCouponNotFound() {
        when(couponRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> couponService.getValidCoupon("NOPE"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    void blankCode_returnsNull() {
        assertThat(couponService.getValidCoupon("  ")).isNull();
        assertThat(couponService.getValidCoupon("")).isNull();
    }
}
