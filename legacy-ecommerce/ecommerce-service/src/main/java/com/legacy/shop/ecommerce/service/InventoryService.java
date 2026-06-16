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

    /**
     * 결제 확정 후 재고 확정.
     *
     * 재고는 {@link #reserve(Long, int)} 단계에서 이미 차감됐으므로 여기서는 다시 차감하지 않는다.
     * (과거: confirm 이 reserve 와 동일하게 또 차감해 주문 1건당 재고가 2배 빠지던 버그 — B1)
     * 확정 시점에 재고 레코드가 유효한지만 검증한다.
     */
    public void confirm(Long productId, int qty) {
        inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        // 차감하지 않는다: reserve 단계에서 이미 재고를 줄였다.
    }

    /** 주문 취소시 재고 복원. */
    public void restore(Long productId, int qty) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        inv.setQuantity(inv.getQuantity() + qty);
        inventoryRepository.save(inv);
    }
}
