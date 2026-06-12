package com.legacy.shop.payment.dto;

public record PaymentMethodResponse(
        Long id,
        Long customerId,
        String type,
        String cardNoMasked
) {
}
