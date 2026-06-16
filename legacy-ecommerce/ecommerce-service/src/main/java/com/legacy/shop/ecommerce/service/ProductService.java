package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.MoneyUtils;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.core.web.PageRequestDto;
import com.legacy.shop.ecommerce.domain.Inventory;
import com.legacy.shop.ecommerce.domain.Product;
import com.legacy.shop.ecommerce.dto.CreateProductRequest;
import com.legacy.shop.ecommerce.repository.InventoryRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import com.legacy.shop.ecommerce.repository.ProductSearchDao;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 상품 조회/등록.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductSearchDao productSearchDao;

    public ProductService(ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          ProductSearchDao productSearchDao) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.productSearchDao = productSearchDao;
    }

    public List<Product> list(PageRequestDto page) {
        List<Product> all = productRepository.findByActiveTrue();
        int offset = page.getOffset();
        if (offset >= all.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(offset + page.getSize(), all.size());
        return all.subList(offset, end);
    }

    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    public int getStock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElse(0);
    }

    public List<Product> search(String keyword) {
        return productSearchDao.searchByName(keyword);
    }

    public Product create(CreateProductRequest req) {
        Product p = new Product();
        p.setName(req.name());
        // 입력 단가를 정책 scale(2)로 정규화한다 — 같은 트랜잭션 내 lineTotal=multiply(price,n) 계산값이
        // 컬럼(DECIMAL(19,2))에 잘려 저장된 reload 값과 어긋나지 않도록([ADR-0006]). null 은 종전대로 통과.
        p.setPrice(req.price() == null ? null : MoneyUtils.round(req.price()));
        p.setCategoryId(req.categoryId());
        p.setDescription(req.description());
        p.setActive(true);
        p = productRepository.save(p);

        Inventory inv = new Inventory();
        inv.setProductId(p.getId());
        inv.setQuantity(req.initialStock());
        inventoryRepository.save(inv);

        return p;
    }
}
