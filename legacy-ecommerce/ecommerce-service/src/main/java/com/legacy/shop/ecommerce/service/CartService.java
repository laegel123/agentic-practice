package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.MoneyUtils;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.domain.Cart;
import com.legacy.shop.ecommerce.domain.CartItem;
import com.legacy.shop.ecommerce.domain.Product;
import com.legacy.shop.ecommerce.repository.CartRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       InventoryService inventoryService) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Cart getOrCreateCart(Long customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            Cart c = new Cart();
            c.setCustomerId(customerId);
            return cartRepository.save(c);
        });
    }

    @Transactional
    public Cart addItem(Long customerId, Long productId, int qty) {
        Cart cart = getOrCreateCart(customerId);
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        inventoryService.checkStock(productId, qty);
        cart.addItem(new CartItem(productId, qty, p.getPrice()));
        return cartRepository.save(cart);
    }

    /** 장바구니 합계 (요약 화면 표시용). 품목별 단가 × 수량의 합. */
    public double cartTotal(Cart cart) {
        double total = 0;
        for (CartItem it : cart.getItems()) {
            total += MoneyUtils.multiply(it.getUnitPrice(), it.getQuantity());
        }
        return total;
    }
}
