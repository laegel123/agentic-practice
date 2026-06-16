package com.legacy.shop.ecommerce.domain;

import com.legacy.shop.common.util.MoneyUtils;
import com.legacy.shop.core.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "coupon")
public class Coupon extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    // 0.1 = 10% 할인. 비율(무차원 계수)이라 double 로 둔다 ([ADR-0006]).
    private double discountRate;

    // 이 날짜까지 사용 가능 (만료일 당일 포함)
    private LocalDate expiryDate;

    @Column(precision = MoneyUtils.MONEY_PRECISION, scale = MoneyUtils.MONEY_SCALE)
    private BigDecimal minOrderAmount;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(double discountRate) {
        this.discountRate = discountRate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }
}
