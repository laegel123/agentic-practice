package com.legacy.shop.ecommerce.service;

import com.legacy.shop.core.web.PageRequestDto;
import com.legacy.shop.ecommerce.domain.Product;
import com.legacy.shop.ecommerce.repository.InventoryRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import com.legacy.shop.ecommerce.repository.ProductSearchDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ProductService.list 페이지네이션 동작 고정.
 *
 * B5 수정(2026-06-16): PageRequestDto.getOffset 이 (page-1)*size 로 바뀌어 1-based 첫 페이지를
 * 더 이상 건너뛰지 않는다. (이전) page*size 라 page=1 이 두 번째 페이지부터 반환했다.
 * (docs/known-issues.md B5)
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductSearchDao productSearchDao;

    @InjectMocks
    private ProductService productService;

    /** 이름이 "P1".."Pn" 인 활성 상품 n 개. */
    private List<Product> products(int n) {
        List<Product> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Product p = new Product();
            p.setName("P" + i);
            list.add(p);
        }
        return list;
    }

    private PageRequestDto page(int page, int size) {
        PageRequestDto dto = new PageRequestDto();
        dto.setPage(page);
        dto.setSize(size);
        return dto;
    }

    private List<String> names(List<Product> products) {
        return products.stream().map(Product::getName).toList();
    }

    @Test
    void firstPage_returnsFirstSlice_notSkipped() {
        when(productRepository.findByActiveTrue()).thenReturn(products(10));

        List<Product> result = productService.list(page(1, 5));

        // 첫 페이지는 P1~P5. (수정 전이라면 offset=5 라 P6~P10 을 반환해 첫 페이지를 건너뜀)
        assertThat(names(result)).containsExactly("P1", "P2", "P3", "P4", "P5");
    }

    @Test
    void secondPage_returnsNextSlice() {
        when(productRepository.findByActiveTrue()).thenReturn(products(10));

        List<Product> result = productService.list(page(2, 5));

        assertThat(names(result)).containsExactly("P6", "P7", "P8", "P9", "P10");
    }

    @Test
    void pageBeyondData_returnsEmpty() {
        when(productRepository.findByActiveTrue()).thenReturn(products(10));

        // offset = (3-1)*5 = 10 >= 10 → 빈 결과.
        assertThat(productService.list(page(3, 5))).isEmpty();
    }

    @Test
    void zeroPage_clampsToFirstSlice_doesNotCrash() {
        // B5 후속(리뷰 차단): page=0(어드민 기본 호출 경로 등)이 음수 offset 으로 500 을 내지 않고
        // 첫 페이지를 반환해야 한다. (클램프 전이라면 offset=-5 → subList(-5, ..) IndexOutOfBounds → 500)
        when(productRepository.findByActiveTrue()).thenReturn(products(10));

        List<Product> result = productService.list(page(0, 5));

        assertThat(names(result)).containsExactly("P1", "P2", "P3", "P4", "P5");
    }

    @Test
    void negativePage_clampsToFirstSlice_doesNotCrash() {
        when(productRepository.findByActiveTrue()).thenReturn(products(10));

        List<Product> result = productService.list(page(-3, 5));

        assertThat(names(result)).containsExactly("P1", "P2", "P3", "P4", "P5");
    }
}
