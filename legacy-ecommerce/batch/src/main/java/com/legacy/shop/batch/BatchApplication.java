package com.legacy.shop.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication(scanBasePackages = "com.legacy.shop")
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }

    /**
     * 집계용 시계. 주문 시각이 UTC 로 저장되므로 '오늘' 판정도 UTC 로 한다(B7).
     * 테스트는 고정 Clock 을 주입해 시각을 박제한다.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
