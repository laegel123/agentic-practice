package com.legacy.shop.admin.web;

import com.legacy.shop.admin.client.ShopGateway;
import com.legacy.shop.admin.security.AdminAuth;
import com.legacy.shop.core.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ShopGateway gateway;
    private final AdminAuth adminAuth;

    public AdminProductController(ShopGateway gateway, AdminAuth adminAuth) {
        this.gateway = gateway;
        this.adminAuth = adminAuth;
    }

    @GetMapping
    public ApiResponse<Object> list(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        adminAuth.check(token);
        return ApiResponse.success(gateway.listProducts(page, size));
    }

    @PostMapping
    public ApiResponse<Object> create(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @RequestBody Map<String, Object> body) {
        adminAuth.check(token);
        return ApiResponse.success(gateway.createProduct(body));
    }
}
