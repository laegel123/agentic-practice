package com.legacy.shop.admin.web;

import com.legacy.shop.admin.client.ShopGateway;
import com.legacy.shop.admin.security.AdminAuth;
import com.legacy.shop.admin.util.AdminPriceCalculator;
import com.legacy.shop.core.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final ShopGateway gateway;
    private final AdminAuth adminAuth;
    private final AdminPriceCalculator priceCalculator;

    public AdminOrderController(ShopGateway gateway, AdminAuth adminAuth, AdminPriceCalculator priceCalculator) {
        this.gateway = gateway;
        this.adminAuth = adminAuth;
        this.priceCalculator = priceCalculator;
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> get(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                   @PathVariable Long id) {
        adminAuth.check(token);
        return ApiResponse.success(gateway.getOrder(id));
    }

    /** 금액 미리보기 (어드민 화면에서 합계 재계산용). */
    @GetMapping("/preview-total")
    public ApiResponse<Double> previewTotal(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                            @RequestParam double subtotal,
                                            @RequestParam(defaultValue = "0") double discount) {
        adminAuth.check(token);
        return ApiResponse.success(priceCalculator.calcTotal(subtotal, discount));
    }
}
