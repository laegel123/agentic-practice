package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.DateUtils;
import com.legacy.shop.common.util.StringUtils;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.repository.CouponRepository;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 조회/검증.
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public Coupon getValidCoupon(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 만료 검사: 만료일은 '당일 포함' 유효. 만료일이 오늘보다 이전일 때만 만료 처리한다.
        if (coupon.getExpiryDate().isBefore(DateUtils.localToday())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        return coupon;
    }
}
