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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/**
 * CouponService.getValidCoupon 의 현재 동작 고정.
 *
 * 주의: 만료 검사가 `!expiryDate.isAfter(오늘)` 이라 '만료일 당일' 쿠폰이 거부된다(off-by-one).
 * Coupon.expiryDate 주석은 "만료일 당일 포함" 이므로 의도와 어긋난다. (docs/known-issues.md B4)
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
        c.setMinOrderAmount(0);
        c.setExpiryDate(expiry);
        return c;
    }

    @Test
    void expiryToday_isRejected_offByOne() {
        when(couponRepository.findByCode("SAVE10"))
                .thenReturn(Optional.of(couponExpiring(LocalDate.now())));

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> couponService.getValidCoupon("SAVE10"));

        // "만료일 당일 포함" 의도와 달리 당일에 만료 처리된다.
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COUPON_EXPIRED);
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
