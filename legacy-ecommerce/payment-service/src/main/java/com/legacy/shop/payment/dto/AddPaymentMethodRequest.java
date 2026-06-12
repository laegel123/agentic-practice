package com.legacy.shop.payment.dto;

public record AddPaymentMethodRequest(
        Long customerId,
        String type,
        String cardNo
) {
}
