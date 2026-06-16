package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.DateUtils;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.client.PaymentClient;
import com.legacy.shop.ecommerce.domain.Cart;
import com.legacy.shop.ecommerce.domain.CartItem;
import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.domain.Order;
import com.legacy.shop.ecommerce.domain.OrderItem;
import com.legacy.shop.ecommerce.domain.OrderStatus;
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
 * 주문 처리.
 *
 * placeOrder 하나에서 재고/가격/결제/장바구니/알림까지 전부 처리한다.
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
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPTY_CART));
        if (cart.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_CART);
        }

        // 1) 재고 확인 + 예약(차감)
        for (CartItem ci : cart.getItems()) {
            inventoryService.checkStock(ci.getProductId(), ci.getQuantity());
            inventoryService.reserve(ci.getProductId(), ci.getQuantity());
        }

        // 2) 주문/주문아이템 생성
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

        // 3) 쿠폰 적용 + 금액 계산
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

        order = orderRepository.save(order);

        // 4) 결제 호출 (HTTP)
        Long paymentId;
        try {
            paymentId = paymentClient.charge(order.getId(), customerId, pricing.total());
        } catch (Exception e) {
            // 결제 실패 처리
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        order.setPaymentId(paymentId);
        order.setStatus(OrderStatus.PAID);

        // 5) 재고 확정
        for (OrderItem oi : order.getItems()) {
            inventoryService.confirm(oi.getProductId(), oi.getQuantity());
        }

        // 6) 장바구니 비우기
        cart.clear();
        cartRepository.save(cart);

        // 7) 주문완료 알림
        log.info("[알림] 주문완료 orderId={}, total={}", order.getId(), order.getTotalAmount());

        return orderRepository.save(order);
    }

    public Order get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public List<Order> getByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}
