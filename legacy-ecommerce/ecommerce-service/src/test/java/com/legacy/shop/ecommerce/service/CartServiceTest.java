package com.legacy.shop.ecommerce.service;

import com.legacy.shop.ecommerce.domain.Cart;
import com.legacy.shop.ecommerce.domain.CartItem;
import com.legacy.shop.ecommerce.repository.CartRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

/**
 * CartService.cartTotal 의 현재 동작 고정.
 *
 * 주의: cartTotal 은 unitPrice 만 더하고 quantity 를 무시한다(버그). 아래 단언은 그 현재 동작을
 * 박제한다 — 수량을 반영하도록 고치면 같은 커밋에서 단언을 뒤집어야 한다. (docs/known-issues.md B2)
 */
class CartServiceTest {

    private static final double EPS = 1e-9;

    // cartTotal 은 Cart 만 받으므로 협력자는 사용되지 않는다. 인스턴스화를 위해 mock 으로 채운다.
    private final CartService cartService = new CartService(
            mock(CartRepository.class),
            mock(ProductRepository.class),
            mock(InventoryService.class));

    @Test
    void cartTotal_ignoresQuantity_sumsUnitPriceOnly() {
        Cart cart = new Cart();
        cart.addItem(new CartItem(1L, 2, 10.0));  // 수량 2
        cart.addItem(new CartItem(2L, 3, 20.0));  // 수량 3

        // 수량을 반영하면 10*2 + 20*3 = 80.0 이어야 하지만, 현재는 unitPrice 합만 → 30.0.
        assertThat(cartService.cartTotal(cart)).isCloseTo(30.0, within(EPS));
    }

    @Test
    void cartTotal_emptyCart_isZero() {
        assertThat(cartService.cartTotal(new Cart())).isCloseTo(0.0, within(EPS));
    }
}
