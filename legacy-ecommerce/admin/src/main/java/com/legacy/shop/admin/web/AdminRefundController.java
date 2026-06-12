package com.legacy.shop.admin.web;

import com.legacy.shop.admin.client.ShopGateway;
import com.legacy.shop.admin.dto.RefundCommand;
import com.legacy.shop.core.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 어드민 환불 처리. 결제 서비스에 환불을 요청한다.
 */
@RestController
@RequestMapping("/admin/refunds")
public class AdminRefundController {

    private final ShopGateway gateway;

    public AdminRefundController(ShopGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping
    public ApiResponse<Object> refund(@RequestBody RefundCommand cmd) {
        return ApiResponse.success(gateway.refund(cmd.paymentId(), cmd.amount(), cmd.reason()));
    }
}
