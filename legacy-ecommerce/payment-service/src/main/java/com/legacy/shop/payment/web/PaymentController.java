package com.legacy.shop.payment.web;

import com.legacy.shop.core.web.ApiResponse;
import com.legacy.shop.payment.domain.Payment;
import com.legacy.shop.payment.domain.Refund;
import com.legacy.shop.payment.dto.ChargeRequest;
import com.legacy.shop.payment.dto.PaymentResponse;
import com.legacy.shop.payment.dto.RefundRequest;
import com.legacy.shop.payment.dto.RefundResponse;
import com.legacy.shop.payment.service.PaymentService;
import com.legacy.shop.payment.service.RefundService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping("/charge")
    public ApiResponse<PaymentResponse> charge(@RequestBody ChargeRequest req) {
        Payment p = paymentService.charge(req.orderId(), req.customerId(), req.amount(), req.method());
        return ApiResponse.success(toDto(p));
    }

    @PostMapping("/refund")
    public ApiResponse<RefundResponse> refund(@RequestBody RefundRequest req) {
        Refund r = refundService.refund(req.paymentId(), req.amount(), req.reason());
        Payment p = paymentService.get(req.paymentId());
        return ApiResponse.success(new RefundResponse(r.getId(), r.getPaymentId(), r.getAmount(), p.getStatus().name()));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> get(@PathVariable Long id) {
        return ApiResponse.success(toDto(paymentService.get(id)));
    }

    private PaymentResponse toDto(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getCustomerId(), p.getAmount(),
                p.getStatus().name(), p.getMethod());
    }
}
