package com.legacy.shop.ecommerce.service;

import com.legacy.shop.ecommerce.domain.Cart;
import com.legacy.shop.ecommerce.domain.CartItem;
import com.legacy.shop.ecommerce.repository.CartRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * CartService.cartTotal 의 동작 고정.
 *
 * B2 수정(2026-06-16): cartTotal 이 단가 × 수량의 합을 낸다(이전에는 unitPrice 만 더해 수량 무시).
 * 금액 타입 전환(2026-06-16, [ADR-0006]): 반환이 BigDecimal. 값 동등성(isEqualByComparingTo)으로 단언.
 * (docs/known-issues.md B2)
 */
class CartServiceTest {

    // cartTotal 은 Cart 만 받으므로 협력자는 사용되지 않는다. 인스턴스화를 위해 mock 으로 채운다.
    private final CartService cartService = new CartService(
            mock(CartRepository.class),
            mock(ProductRepository.class),
            mock(InventoryService.class));

    @Test
    void cartTotal_usesQuantity_sumsLineTotals() {
        Cart cart = new Cart();
        cart.addItem(new CartItem(1L, 2, new BigDecimal("10.0")));  // 수량 2
        cart.addItem(new CartItem(2L, 3, new BigDecimal("20.0")));  // 수량 3

        // 단가 × 수량의 합: 10*2 + 20*3 = 80.0 (B2 수정 전에는 수량 무시로 30.0 이었다).
        assertThat(cartService.cartTotal(cart)).isEqualByComparingTo("80.0");
    }

    @Test
    void cartTotal_emptyCart_isZero() {
        assertThat(cartService.cartTotal(new Cart())).isEqualByComparingTo("0");
    }
}
