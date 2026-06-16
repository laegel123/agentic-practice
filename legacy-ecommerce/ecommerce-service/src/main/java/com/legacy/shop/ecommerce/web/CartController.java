package com.legacy.shop.ecommerce.web;

import com.legacy.shop.common.util.MoneyUtils;
import com.legacy.shop.core.web.ApiResponse;
import com.legacy.shop.ecommerce.domain.Cart;
import com.legacy.shop.ecommerce.domain.CartItem;
import com.legacy.shop.ecommerce.dto.AddCartItemRequest;
import com.legacy.shop.ecommerce.dto.CartItemResponse;
import com.legacy.shop.ecommerce.dto.CartResponse;
import com.legacy.shop.ecommerce.service.CartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/{customerId}/items")
    public ApiResponse<CartResponse> addItem(@PathVariable Long customerId,
                                             @RequestBody AddCartItemRequest req) {
        Cart cart = cartService.addItem(customerId, req.productId(), req.quantity());
        return ApiResponse.success(toDto(cart));
    }

    @GetMapping("/{customerId}")
    public ApiResponse<CartResponse> get(@PathVariable Long customerId) {
        Cart cart = cartService.getOrCreateCart(customerId);
        return ApiResponse.success(toDto(cart));
    }

    private CartResponse toDto(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemDto)
                .toList();
        BigDecimal total = cartService.cartTotal(cart);
        return new CartResponse(cart.getId(), cart.getCustomerId(), items, total);
    }

    private CartItemResponse toItemDto(CartItem ci) {
        return new CartItemResponse(ci.getProductId(), ci.getQuantity(), ci.getUnitPrice(),
                MoneyUtils.multiply(ci.getUnitPrice(), ci.getQuantity()));
    }
}
