package com.legacy.shop.admin.web;

import com.legacy.shop.admin.client.ShopGateway;
import com.legacy.shop.admin.dto.RefundCommand;
import com.legacy.shop.admin.security.AdminAuth;
import com.legacy.shop.core.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 어드민 환불 처리. 결제 서비스에 환불을 요청한다.
 *
 * 다른 어드민 컨트롤러와 동일하게 X-Admin-Token 으로 인증한다(A1 수정, 2026-06-15).
 */
@RestController
@RequestMapping("/admin/refunds")
public class AdminRefundController {

    private final ShopGateway gateway;
    private final AdminAuth adminAuth;

    public AdminRefundController(ShopGateway gateway, AdminAuth adminAuth) {
        this.gateway = gateway;
        this.adminAuth = adminAuth;
    }

    @PostMapping
    public ApiResponse<Object> refund(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @RequestBody RefundCommand cmd) {
        adminAuth.check(token);
        return ApiResponse.success(gateway.refund(cmd.paymentId(), cmd.amount(), cmd.reason()));
    }
}
