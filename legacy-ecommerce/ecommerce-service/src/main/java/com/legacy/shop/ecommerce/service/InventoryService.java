package com.legacy.shop.ecommerce.service;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.domain.Inventory;
import com.legacy.shop.ecommerce.repository.InventoryRepository;
import org.springframework.stereotype.Service;

/**
 * 재고 관리.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public int getStock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElse(0);
    }

    public void checkStock(Long productId, int qty) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (inv.getQuantity() < qty) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
    }

    /** 주문시 재고 예약(차감). */
    public void reserve(Long productId, int qty) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        inv.setQuantity(inv.getQuantity() - qty);
        inventoryRepository.save(inv);
    }

    /** 결제 확정 후 재고 확정. */
    public void confirm(Long productId, int qty) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        inv.setQuantity(inv.getQuantity() - qty);
        inventoryRepository.save(inv);
    }

    /** 주문 취소시 재고 복원. */
    public void restore(Long productId, int qty) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        inv.setQuantity(inv.getQuantity() + qty);
        inventoryRepository.save(inv);
    }
}
