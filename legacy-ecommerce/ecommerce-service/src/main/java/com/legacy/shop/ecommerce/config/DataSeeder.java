package com.legacy.shop.ecommerce.config;

import com.legacy.shop.common.util.CryptoUtils;
import com.legacy.shop.common.util.DateUtils;
import com.legacy.shop.ecommerce.domain.Category;
import com.legacy.shop.ecommerce.domain.Coupon;
import com.legacy.shop.ecommerce.domain.Customer;
import com.legacy.shop.ecommerce.domain.Inventory;
import com.legacy.shop.core.domain.OrderStatus;
import com.legacy.shop.ecommerce.domain.Order;
import com.legacy.shop.ecommerce.domain.Product;
import com.legacy.shop.ecommerce.repository.CategoryRepository;
import com.legacy.shop.ecommerce.repository.CouponRepository;
import com.legacy.shop.ecommerce.repository.CustomerRepository;
import com.legacy.shop.ecommerce.repository.InventoryRepository;
import com.legacy.shop.ecommerce.repository.OrderRepository;
import com.legacy.shop.ecommerce.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 초기 데이터 시딩. (DB 가 비어있을 때만 한 번 넣는다)
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;

    public DataSeeder(CategoryRepository categoryRepository,
                      ProductRepository productRepository,
                      InventoryRepository inventoryRepository,
                      CustomerRepository customerRepository,
                      CouponRepository couponRepository,
                      OrderRepository orderRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.customerRepository = customerRepository;
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        Category electronics = newCategory("전자기기");
        Category office = newCategory("사무용품");

        // 상품 12개 (페이지네이션 확인용). 가격은 일부러 소수점 둘째자리.
        newProduct("기계식 키보드", 29.99, electronics.getId(), 50);
        newProduct("게이밍 마우스", 19.99, electronics.getId(), 50);
        newProduct("27인치 모니터", 199.99, electronics.getId(), 30);
        newProduct("USB-C 허브", 39.95, electronics.getId(), 40);
        newProduct("웹캠 1080p", 49.90, electronics.getId(), 25);
        newProduct("노이즈캔슬링 헤드셋", 129.99, electronics.getId(), 20);
        newProduct("A4 복사용지 박스", 24.50, office.getId(), 100);
        newProduct("3색 볼펜", 2.99, office.getId(), 200);
        newProduct("스테이플러", 8.75, office.getId(), 80);
        newProduct("데스크 매트", 15.00, office.getId(), 60);
        newProduct("모니터 받침대", 34.99, office.getId(), 35);
        newProduct("노트북 거치대", 27.50, office.getId(), 45);

        // 고객 1명
        Customer c = new Customer();
        c.setEmail("hong@example.com");
        c.setName("홍길동");
        c.setPhone("010-1234-5678");
        c.setPassword(CryptoUtils.hashPassword("password1"));
        customerRepository.save(c);

        // 쿠폰 2종
        Coupon save20 = new Coupon();
        save20.setCode("SAVE20");
        save20.setDiscountRate(0.2);
        save20.setExpiryDate(LocalDate.now().plusYears(1));
        save20.setMinOrderAmount(0);
        couponRepository.save(save20);

        Coupon welcome = new Coupon();
        welcome.setCode("WELCOME");
        welcome.setDiscountRate(0.1);
        welcome.setExpiryDate(LocalDate.now()); // 오늘까지 유효한 쿠폰
        welcome.setMinOrderAmount(0);
        couponRepository.save(welcome);

        // 정산/집계 배치 확인용 이력 주문 (결제완료 2건 + 취소 1건)
        seedOrder(c.getId(), 100.00, OrderStatus.PAID);
        seedOrder(c.getId(), 50.00, OrderStatus.PAID);
        seedOrder(c.getId(), 30.00, OrderStatus.CANCELLED);

        log.info("[seed] 초기 데이터 적재 완료");
    }

    private void seedOrder(Long customerId, double total, OrderStatus status) {
        Order o = new Order();
        o.setCustomerId(customerId);
        o.setSubtotal(total);
        o.setDiscountAmount(0);
        o.setTax(0);
        o.setTotalAmount(total);
        o.setStatus(status);
        o.setOrderedAt(DateUtils.now());
        orderRepository.save(o);
    }

    private Category newCategory(String name) {
        Category cat = new Category();
        cat.setName(name);
        return categoryRepository.save(cat);
    }

    private void newProduct(String name, double price, Long categoryId, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setCategoryId(categoryId);
        p.setDescription(name + " 입니다.");
        p.setActive(true);
        p = productRepository.save(p);

        Inventory inv = new Inventory();
        inv.setProductId(p.getId());
        inv.setQuantity(stock);
        inventoryRepository.save(inv);
    }
}
