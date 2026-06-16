package com.legacy.shop.ecommerce.service;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.client.PaymentClient;
import com.legacy.shop.ecommerce.domain.Cart;
import com.legacy.shop.ecommerce.domain.CartItem;
import com.legacy.shop.ecommerce.domain.Order;
import com.legacy.shop.core.domain.OrderStatus;
import com.legacy.shop.ecommerce.domain.Product;
import com.legacy.shop.ecommerce.repository.CartRepository;
import com.legacy.shop.ecommerce.repository.OrderRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderService.placeOrder(God method) 의 현재 동작 고정. 7개 협력자를 모두 mock 한다.
 *
 * 핵심: 정상 흐름에서 품목당 inventory.reserve(1단계)·confirm(5단계) 가 모두 호출된다(주문 오케스트레이션).
 * 단, B1 수정 이후 confirm 은 재고를 다시 차감하지 않으므로 재고는 '한 번만' 빠진다 — 실제 차감 횟수
 * 검증은 InventoryService 단위에서 한다(InventoryServiceTest). (docs/known-issues.md B1)
 * 이 테스트는 향후 placeOrder 추출 리팩토링(R1)의 안전망이기도 하다.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryService inventoryService;
    @Mock private CouponService couponService;
    @Mock private PricingService pricingService;
    @Mock private PaymentClient paymentClient;

    @InjectMocks
    private OrderService orderService;

    private static final Long CUSTOMER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private Cart cartWithOneItem(int qty) {
        Cart cart = new Cart();
        cart.setCustomerId(CUSTOMER_ID);
        cart.addItem(new CartItem(PRODUCT_ID, qty, 100.0));
        return cart;
    }

    private Product product() {
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", PRODUCT_ID); // Product 에는 setId 가 없어 리플렉션으로 주입
        p.setName("상품");
        p.setPrice(100.0);
        return p;
    }

    @Test
    void placeOrder_happyPath_reservesThenConfirms_andCompletes() {
        Cart cart = cartWithOneItem(2);
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(pricingService.calculate(anyList(), nullable(com.legacy.shop.ecommerce.domain.Coupon.class)))
                .thenReturn(new PricingResult(200.0, 0.0, 20.0, 220.0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentClient.charge(any(), any(), anyDouble())).thenReturn(999L);

        Order order = orderService.placeOrder(CUSTOMER_ID, null);

        // 주문 흐름: reserve(1단계)·confirm(5단계) 모두 호출됨(confirm 은 더 이상 재고를 다시 차감하지 않음 — B1).
        verify(inventoryService).checkStock(PRODUCT_ID, 2);
        verify(inventoryService).reserve(PRODUCT_ID, 2);
        verify(inventoryService).confirm(PRODUCT_ID, 2);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo(999L);
        assertThat(order.getTotalAmount()).isEqualTo(220.0);
        assertThat(cart.getItems()).isEmpty(); // 성공 시 장바구니 비움
    }

    @Test
    void placeOrder_emptyCart_throwsEmptyCart() {
        Cart empty = new Cart();
        empty.setCustomerId(CUSTOMER_ID);
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(empty));

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> orderService.placeOrder(CUSTOMER_ID, null));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPTY_CART);
    }

    @Test
    void placeOrder_noCart_throwsEmptyCart() {
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> orderService.placeOrder(CUSTOMER_ID, null));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPTY_CART);
    }

    @Test
    void placeOrder_paymentFailure_mapsToPaymentFailed_andDoesNotConfirmOrClearCart() {
        Cart cart = cartWithOneItem(2);
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(pricingService.calculate(anyList(), nullable(com.legacy.shop.ecommerce.domain.Coupon.class)))
                .thenReturn(new PricingResult(200.0, 0.0, 20.0, 220.0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentClient.charge(any(), any(), anyDouble())).thenThrow(new RuntimeException("결제 게이트웨이 오류"));

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> orderService.placeOrder(CUSTOMER_ID, null));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);
        verify(inventoryService).reserve(PRODUCT_ID, 2);                 // 예약은 이미 일어남
        verify(inventoryService, never()).confirm(anyLong(), anyInt());  // 확정 단계엔 도달 못 함
        assertThat(cart.getItems()).hasSize(1);                          // 장바구니 비우지 않음
    }
}
