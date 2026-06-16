package com.legacy.shop.ecommerce.service;

import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.domain.Inventory;
import com.legacy.shop.ecommerce.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryService 의 재고 차감 의미를 고정한다.
 *
 * B1(이중차감) 수정의 회귀 안전망: reserve 는 차감하고, confirm 은 (reserve 가 이미 차감했으므로)
 * 다시 차감하지 않는다 → 주문 흐름(reserve→confirm) 전체에서 재고는 '한 번만' 빠진다.
 * 과거에는 confirm 이 reserve 와 동일하게 또 차감해 50→46(2배 차감)이 됐다. (docs/known-issues.md B1)
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks private InventoryService inventoryService;

    private static final Long PRODUCT_ID = 10L;

    private Inventory inventoryOf(int qty) {
        Inventory inv = new Inventory();
        inv.setProductId(PRODUCT_ID);
        inv.setQuantity(qty);
        return inv;
    }

    @Test
    void reserve_deductsStock() {
        Inventory inv = inventoryOf(50);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inv));

        inventoryService.reserve(PRODUCT_ID, 2);

        assertThat(inv.getQuantity()).isEqualTo(48);
        verify(inventoryRepository).save(inv);
    }

    @Test
    void confirm_doesNotDeductStock() {
        // B1 수정: confirm 은 reserve 가 이미 차감한 재고를 다시 차감하지 않는다.
        Inventory inv = inventoryOf(48);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inv));

        inventoryService.confirm(PRODUCT_ID, 2);

        assertThat(inv.getQuantity()).isEqualTo(48); // 불변(46 아님)
    }

    @Test
    void reserveThenConfirm_deductsStockOnce_notTwice() {
        // 주문 흐름 전체(reserve→confirm)에서 재고는 한 번만 빠진다: 50 → 48 (이전 버그: 46).
        Inventory inv = inventoryOf(50);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inv));

        inventoryService.reserve(PRODUCT_ID, 2);
        inventoryService.confirm(PRODUCT_ID, 2);

        assertThat(inv.getQuantity()).isEqualTo(48);
    }

    @Test
    void restore_addsStockBack() {
        Inventory inv = inventoryOf(48);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(inv));

        inventoryService.restore(PRODUCT_ID, 2);

        assertThat(inv.getQuantity()).isEqualTo(50);
        verify(inventoryRepository).save(inv);
    }

    @Test
    void confirm_unknownProduct_throwsProductNotFound() {
        when(inventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(BusinessException.class,
                () -> inventoryService.confirm(99L, 1));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
