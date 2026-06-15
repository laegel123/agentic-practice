# payment-service

> legacy-shop 의 **결제 서비스**(`:8082`) — 결제 승인·환불·결제수단·원장(ledger)을 소유한다.
> ecommerce 가 주문 시 `PaymentClient` 로 **이 서비스를 호출**하지만, 이 서비스 자체는 외부로 나가는
> 호출이 없는 **리프(leaf)** 다. ecommerce 와 **별도의 H2 파일 DB**(`~/legacypaydb`)를 직접 소유한다.
> 전체 시스템 맥락은 모노레포 문서 [`../docs/`](../docs/) 를 본다(이 모듈은 자체 `docs/` 폴더가 없다).

## 정체성

- Java 21 / Spring Boot 3.5.15 / Gradle. 의존: `core-framework` + `common-util` +
  `spring-boot-starter-{web,data-jpa}` + runtime `h2`. ⚠️ **`validation` 스타터는 없다** —
  ecommerce 와 달리 `@Valid`/Bean Validation 미적용이라 요청 바디가 검증되지 않는다(아래 주의점).
- **자체 스키마를 직접 생성**한다(`ddl-auto: update`). 전용 DB `~/legacypaydb`(`AUTO_SERVER=TRUE`)를
  소유하며 ecommerce 의 `legacyshopdb` 와 **물리적으로 분리**돼 있다. 소유 테이블 4개:
  `payment` · `refund` · `ledger` · `payment_method`.
- 서비스 3 / 엔티티 4(+`PaymentStatus`·`LedgerType` enum) / 컨트롤러 2 / 리포지토리 4 / DTO 6.
  외부 의존 없음 → DB 만 있으면 **단독 기동 가능**(ecommerce 보다 먼저 떠도 됨).
- `@EnableJpaAuditing`, `@SpringBootApplication(scanBasePackages = "com.legacy.shop")` 로
  `core-framework` 의 공통 빈(`GlobalExceptionHandler`, `ApiResponse`, `ErrorCode` 등)을 함께 스캔한다.

## 빌드 / 실행 / 테스트

프로젝트 루트(`legacy-ecommerce/`)에서 실행한다. **Windows PowerShell**은 `.\gradlew.bat`,
Bash 도구(POSIX 셸)는 `./gradlew`.

```powershell
.\gradlew.bat :payment-service:build      # 빌드
.\gradlew.bat :payment-service:bootRun     # 실행 (:8082)
.\gradlew.bat :payment-service:test        # 테스트 — characterization 6개 (charge 2 + refund 4)
```

> 테스트는 **현재 동작(버그 포함)을 고정하는 characterization 테스트**다(JUnit5 + Mockito + AssertJ).
> 특히 `RefundServiceTest.overRefund_isNotBlocked_currentlyAllowed` 는 **과다 환불을 막지 않는 현재
> 동작(B6)을 일부러 박제**한다 — 한도 검증을 추가하면 동작이 바뀌므로 그 단언을 **같은 커밋에서
> 의도적으로 뒤집어야** 한다. "초록 만들기"로 약화시키지 말 것.

> 기동 순서: payment 는 외부 의존이 없어 단독으로 떠도 된다. 다만 ecommerce 의 주문 흐름이 런타임에
> 이 서비스를 호출하므로 권장 전체 순서는 **ecommerce → payment → (admin / batch)** 다.
> H2 콘솔은 `/h2-console`(JDBC `jdbc:h2:file:~/legacypaydb`, user `sa`, 비밀번호 없음).

## 구조 한눈에

```
src/main/java/com/legacy/shop/payment/
├── PaymentApplication.java     @EnableJpaAuditing · scanBasePackages="com.legacy.shop"
├── web/                        REST 컨트롤러 (얇게 → 서비스 위임 + DTO 변환)
│   ├── PaymentController        /api/payments        (charge / refund / get)
│   └── PaymentMethodController  /api/payment-methods (add / list)
├── service/                    비즈니스 로직 (3개)
│   ├── PaymentService           charge: APPROVED 결제 + CHARGE 원장 기록
│   ├── RefundService            refund: REFUND 원장 + 누적액으로 상태 전이 (⚠ 과다환불 미검증 B6)
│   └── PaymentMethodService     결제수단 등록/조회 (카드번호는 StringUtils.maskCard 로 마스킹 저장)
├── domain/                     JPA 엔티티 4 + enum 2 (자체 스키마 소유)
│   ├── Payment / Refund / Ledger / PaymentMethod
│   └── PaymentStatus(APPROVED·PARTIALLY_REFUNDED·REFUNDED·CANCELLED) · LedgerType(CHARGE·REFUND)
├── dto/                        요청/응답 record 6
└── repository/                 Spring Data JPA 4 (Payment·Refund·Ledger·PaymentMethod)
```

| 메서드 | 경로 | 요청 → 응답 | 핸들러 |
|--------|------|------------|--------|
| POST | `/api/payments/charge` | `ChargeRequest(orderId, customerId, amount, method)` → `PaymentResponse` | `PaymentController.charge` |
| POST | `/api/payments/refund` | `RefundRequest(paymentId, amount, reason)` → `RefundResponse` | `PaymentController.refund` (⚠ 과다환불 B6) |
| GET | `/api/payments/{id}` | → `PaymentResponse` | `PaymentController.get` (없으면 `PAYMENT_NOT_FOUND`) |
| POST | `/api/payment-methods` | `AddPaymentMethodRequest(customerId, type, cardNo)` → `PaymentMethodResponse` | `PaymentMethodController.add` |
| GET | `/api/payment-methods?customerId=` | → `List<PaymentMethodResponse>` | `PaymentMethodController.list` |

## 이 모듈에서 일할 때 주의점

- **표준 풀스택 레이아웃을 따른다**(`web → service → repository → domain`). 컨트롤러는 얇게(인자 받기 →
  서비스 호출 → `ApiResponse.success(...)` + DTO 변환), 비즈니스 로직은 서비스에 둔다. 새 코드도 이 규칙을 따른다.
- 공통 규칙 재확인: **생성자 주입만**(`private final`, `@Autowired`/Lombok 금지), DTO 는 `record`,
  엔티티는 `BaseTimeEntity` 상속·`@GeneratedValue(IDENTITY)`·`@Enumerated(STRING)`, 응답은
  `ApiResponse<T>`(성공 `code="0000"`), 오류는 `throw new BusinessException(ErrorCode.X)`.
- **원장(ledger) 불변식**: `charge` 는 반드시 `CHARGE` 원장 1행을, `refund` 는 반드시 `REFUND` 원장
  1행을 함께 기록한다. 결제/환불 로직을 손댈 때 이 짝 기록을 빠뜨리지 말 것(원장이 정산·대사의 근거다).
- 결제 관련 에러코드는 `core-framework` 의 **단일 `ErrorCode` enum** 에 모여 있다(`PM001` `PAYMENT_FAILED`,
  `PM002` `REFUND_EXCEEDS_PAYMENT`, `PM003` `PAYMENT_NOT_FOUND`). 현재 던지는 것은 `PAYMENT_NOT_FOUND`
  뿐이며 `PM001`·`PM002` 는 **정의만 되고 미사용**이다(아래 B6).
- ⚠️ 아래는 알려진 결함이다. **새 코드에서 모방하지 말 것.**
  - **B6 — 과다 환불 미검증(높음)**: `RefundService.refund()` 가 환불 누계가 결제액을 초과해도 막지
    않는다. `ErrorCode.REFUND_EXCEEDS_PAYMENT`(PM002)가 정의돼 있으나 **한 번도 던져지지 않는다**.
    characterization 테스트가 현재 동작을 박제하고 있으니 한도 검증을 추가할 때 같은 커밋에서 단언을
    뒤집는다 — [`../docs/known-issues.md`](../docs/known-issues.md) B6.
  - **금액 `double`(B3 / [ADR-0003](../docs/adr/0003-money-as-double.md))**: `amount`·원장·환불 누계 비교가
    전부 `double`. `refundedTotal >= payment.getAmount()` 같은 누적 비교에 부동소수 오차가 끼어들 수 있다.
    금액을 새로 다룰 때 임의 반올림을 끼워넣지 말고 기존 정책을 따른다.
  - **시각은 UTC**: `approvedAt`/`refundedAt` 는 `DateUtils.now()`(=**UTC**)로 찍힌다. ecommerce 의 집계
    타임존 혼용(B7)·`DateUtils` 의 thread-unsafe `SimpleDateFormat`(R3)과 같은 뿌리다 — 새 시각 처리에서 답습 금지.
  - **요청 검증 없음**: `validation` 스타터가 없어 `@RequestBody` 가 검증되지 않는다. 음수/`null`
    `amount` 같은 입력도 그대로 통과한다. `charge` 는 외부 PG 연동 없이 **항상 `APPROVED`** 를 반환하는
    스텁이다(실패 경로·멱등키 없음). 입력 검증을 추가할 때는 위 known-issues 와 동일하게 테스트로 현재 동작을 고정한 뒤 바꾼다.
- 카드번호는 평문 저장하지 않는다. `PaymentMethodService.add` 가 `StringUtils.maskCard` 로 마스킹한
  값(`cardNoMasked`)만 저장한다 — 원본 카드번호를 엔티티/로그에 남기지 말 것.

## 더 읽기

- 이 모듈은 자체 `docs/` 가 없다. 상세는 모노레포 공통 문서를 본다:
  [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) ·
  [`../docs/known-issues.md`](../docs/known-issues.md)(B6) · [`../docs/adr/`](../docs/adr/)(금액 [ADR-0003](../docs/adr/0003-money-as-double.md))
- 호출자 쪽 맥락: ecommerce 의 [`../ecommerce-service/CLAUDE.md`](../ecommerce-service/CLAUDE.md)
  (`PaymentClient` → payment `:8082`, raw Map HTTP R2·타임아웃 없음 R8).
