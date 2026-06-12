package com.legacy.shop.payment.service;

import com.legacy.shop.common.util.StringUtils;
import com.legacy.shop.payment.domain.PaymentMethod;
import com.legacy.shop.payment.repository.PaymentMethodRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 결제수단 관리.
 */
@Service
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodService(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    public PaymentMethod add(Long customerId, String type, String cardNo) {
        PaymentMethod pm = new PaymentMethod();
        pm.setCustomerId(customerId);
        pm.setType(type == null ? "CARD" : type);
        pm.setCardNoMasked(StringUtils.maskCard(cardNo));
        pm.setActive(true);
        return paymentMethodRepository.save(pm);
    }

    public List<PaymentMethod> list(Long customerId) {
        return paymentMethodRepository.findByCustomerId(customerId);
    }
}
