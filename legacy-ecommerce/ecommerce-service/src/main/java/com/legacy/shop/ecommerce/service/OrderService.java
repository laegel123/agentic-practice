package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.DateUtils;
import com.legacy.shop.core.domain.OrderStatus;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.client.PaymentClient;
import com.legacy.shop.ecommerce.domain.Cart;
import com.legacy.shop.ecommerce.domain.CartItem;
import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.domain.Order;
import com.legacy.shop.ecommerce.domain.OrderItem;
import com.legacy.shop.ecommerce.domain.Product;
import com.legacy.shop.ecommerce.repository.CartRepository;
import com.legacy.shop.ecommerce.repository.OrderRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 처리 오케스트레이션.
 *
 * <p>{@link #placeOrder} 는 주문 한 건을 처리하기 위한 7단계(재고 예약 → 주문 생성 → 가격 계산 →
 * 결제 → 재고 확정 → 장바구니 비우기 → 알림)를 한 트랜잭션 안에서 순서대로 호출한다. 각 단계는
 * 의도가 드러나도록 private 메서드로 분리했고(R1), {@code placeOrder} 자체는 단계의 흐름만 보여준다.
 * 협력자·호출 순서·관찰 가능한 동작은 추출 전과 동일하다(동작 보존).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final PricingService pricingService;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        ProductRepository productRepository,
                        InventoryService inventoryService,
                        CouponService couponService,
                        PricingService pricingService,
                        PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.couponService = couponService;
        this.pricingService = pricingService;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public Order placeOrder(Long customerId, String couponCode) {
        Cart cart = loadNonEmptyCart(customerId);

        reserveStock(cart);                       // 1) 재고 확인 + 예약(차감)
        Order order = buildOrder(customerId, cart); // 2) 주문/주문아이템 생성
        applyPricing(order, couponCode);          // 3) 쿠폰 적용 + 금액 계산
        order = orderRepository.save(order);

        pay(order, customerId);                   // 4) 결제 호출(HTTP) + 상태 전이
        confirmStock(order);                      // 5) 재고 확정
        clearCart(cart);                          // 6) 장바구니 비우기
        notifyOrderPlaced(order);                 // 7) 주문완료 알림

        return orderRepository.save(order);
    }

    /** 0) 고객의 장바구니를 조회하고, 없거나 비어 있으면 {@link ErrorCode#EMPTY_CART}. */
    private Cart loadNonEmptyCart(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPTY_CART));
        if (cart.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_CART);
        }
        return cart;
    }

    /** 1) 장바구니 품목마다 재고를 확인하고 예약(차감)한다. */
    private void reserveStock(Cart cart) {
        for (CartItem ci : cart.getItems()) {
            inventoryService.checkStock(ci.getProductId(), ci.getQuantity());
            inventoryService.reserve(ci.getProductId(), ci.getQuantity());
        }
    }

    /** 2) 장바구니로부터 주문과 주문아이템을 생성한다. */
    private Order buildOrder(Long customerId, Cart cart) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setOrderedAt(DateUtils.now());
        for (CartItem ci : cart.getItems()) {
            Product p = productRepository.findById(ci.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            OrderItem oi = new OrderItem();
            oi.setProductId(p.getId());
            oi.setProductName(p.getName());
            oi.setUnitPrice(ci.getUnitPrice());
            oi.setQuantity(ci.getQuantity());
            oi.setLineTotal(ci.getUnitPrice() * ci.getQuantity());
            order.addItem(oi);
        }
        return order;
    }

    /** 3) (선택) 쿠폰을 검증·적용하고 소계/할인/세금/합계를 주문에 채운다. */
    private void applyPricing(Order order, String couponCode) {
        Coupon coupon = null;
        if (couponCode != null && !couponCode.isEmpty()) {
            coupon = couponService.getValidCoupon(couponCode);
            order.setCouponCode(couponCode);
        }
        PricingResult pricing = pricingService.calculate(order.getItems(), coupon);
        order.setSubtotal(pricing.subtotal());
        order.setDiscountAmount(pricing.discountAmount());
        order.setTax(pricing.tax());
        order.setTotalAmount(pricing.total());
    }

    /** 4) 결제 서비스를 호출하고(실패 시 {@link ErrorCode#PAYMENT_FAILED}) 결제 ID·상태를 반영한다. */
    private void pay(Order order, Long customerId) {
        Long paymentId;
        try {
            paymentId = paymentClient.charge(order.getId(), customerId, order.getTotalAmount());
        } catch (Exception e) {
            // 결제 실패 처리
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        order.setPaymentId(paymentId);
        order.setStatus(OrderStatus.PAID);
    }

    /** 5) 예약했던 재고를 확정한다(차감은 1단계에서 끝났고 여기선 검증만 — B1). */
    private void confirmStock(Order order) {
        for (OrderItem oi : order.getItems()) {
            inventoryService.confirm(oi.getProductId(), oi.getQuantity());
        }
    }

    /** 6) 주문이 완료됐으니 장바구니를 비운다. */
    private void clearCart(Cart cart) {
        cart.clear();
        cartRepository.save(cart);
    }

    /** 7) 주문완료 알림(현재는 로깅). */
    private void notifyOrderPlaced(Order order) {
        log.info("[알림] 주문완료 orderId={}, total={}", order.getId(), order.getTotalAmount());
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public List<Order> getByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}
