package com.legacy.shop.ecommerce.web;

import com.legacy.shop.core.web.ApiResponse;
import com.legacy.shop.ecommerce.domain.Order;
import com.legacy.shop.ecommerce.domain.OrderItem;
import com.legacy.shop.ecommerce.dto.OrderItemResponse;
import com.legacy.shop.ecommerce.dto.OrderResponse;
import com.legacy.shop.ecommerce.dto.PlaceOrderRequest;
import com.legacy.shop.ecommerce.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> place(@RequestBody PlaceOrderRequest req) {
        Order order = orderService.placeOrder(req.customerId(), req.couponCode());
        return ApiResponse.success(toDto(order));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.success(toDto(orderService.get(id)));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> byCustomer(@RequestParam Long customerId) {
        List<OrderResponse> body = orderService.getByCustomer(customerId).stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.success(body);
    }

    private OrderResponse toDto(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(this::toItemDto)
                .toList();
        return new OrderResponse(
                o.getId(),
                o.getCustomerId(),
                items,
                o.getSubtotal(),
                o.getDiscountAmount(),
                o.getTax(),
                o.getTotalAmount(),
                o.getStatus().name(),
                o.getOrderedAt(),
                o.getPaymentId());
    }

    private OrderItemResponse toItemDto(OrderItem oi) {
        return new OrderItemResponse(oi.getProductId(), oi.getProductName(), oi.getUnitPrice(),
                oi.getQuantity(), oi.getLineTotal());
    }
}
