package com.legacy.shop.ecommerce.service;

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
        p.setPrice(req.price());
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
