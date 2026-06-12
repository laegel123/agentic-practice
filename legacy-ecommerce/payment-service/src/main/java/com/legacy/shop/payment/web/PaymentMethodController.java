package com.legacy.shop.payment.web;

import com.legacy.shop.core.web.ApiResponse;
import com.legacy.shop.payment.domain.PaymentMethod;
import com.legacy.shop.payment.dto.AddPaymentMethodRequest;
import com.legacy.shop.payment.dto.PaymentMethodResponse;
import com.legacy.shop.payment.service.PaymentMethodService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @PostMapping
    public ApiResponse<PaymentMethodResponse> add(@RequestBody AddPaymentMethodRequest req) {
        PaymentMethod pm = paymentMethodService.add(req.customerId(), req.type(), req.cardNo());
        return ApiResponse.success(toDto(pm));
    }

    @GetMapping
    public ApiResponse<List<PaymentMethodResponse>> list(@RequestParam Long customerId) {
        List<PaymentMethodResponse> body = paymentMethodService.list(customerId).stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.success(body);
    }

    private PaymentMethodResponse toDto(PaymentMethod pm) {
        return new PaymentMethodResponse(pm.getId(), pm.getCustomerId(), pm.getType(), pm.getCardNoMasked());
    }
}
