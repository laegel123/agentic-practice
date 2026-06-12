package com.legacy.shop.ecommerce.web;

import com.legacy.shop.core.web.ApiResponse;
import com.legacy.shop.core.web.PageRequestDto;
import com.legacy.shop.ecommerce.domain.Product;
import com.legacy.shop.ecommerce.dto.CreateProductRequest;
import com.legacy.shop.ecommerce.dto.ProductResponse;
import com.legacy.shop.ecommerce.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> list(PageRequestDto page) {
        List<ProductResponse> body = productService.list(page).stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.success(body);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.success(toDto(productService.get(id)));
    }

    @GetMapping("/{id}/stock")
    public ApiResponse<Integer> stock(@PathVariable Long id) {
        return ApiResponse.success(productService.getStock(id));
    }

    @GetMapping("/search")
    public ApiResponse<List<ProductResponse>> search(@RequestParam String keyword) {
        List<ProductResponse> body = productService.search(keyword).stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.success(body);
    }

    @PostMapping
    public ApiResponse<ProductResponse> create(@RequestBody CreateProductRequest req) {
        return ApiResponse.success(toDto(productService.create(req)));
    }

    private ProductResponse toDto(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getCategoryId(), p.getDescription());
    }
}
