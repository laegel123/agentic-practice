package com.legacy.shop.ecommerce.service;

import com.legacy.shop.common.util.CryptoUtils;
import com.legacy.shop.core.error.BusinessException;
import com.legacy.shop.core.error.ErrorCode;
import com.legacy.shop.ecommerce.domain.Customer;
import com.legacy.shop.ecommerce.repository.CustomerRepository;
import org.springframework.stereotype.Service;

/**
 * 고객.
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer get(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    public Customer register(String email, String name, String phone, String rawPassword) {
        Customer c = new Customer();
        c.setEmail(email);
        c.setName(name);
        c.setPhone(phone);
        c.setPassword(CryptoUtils.hashPassword(rawPassword));
        return customerRepository.save(c);
    }
}
