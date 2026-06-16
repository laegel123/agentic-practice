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
.\gradlew.bat :payment-service:test        # 테스트 — 6개 (charge 2 + refund 4: 부분/누계전액/과다차단/미존재)
```

> 테스트는 JUnit5 + Mockito + AssertJ 기반이다. `RefundServiceTest.overRefund_isBlocked_throwsRefundExceedsPayment`
> 는 **B6(과다 환불) 수정의 회귀 테스트**다 — 과거의 "막지 않음" 단언을 차단(`REFUND_EXCEEDS_PAYMENT`)으로
> 뒤집은 것이다. 한도 검증을 약화시키지 말 것. 나머지 refund 케이스는 부분환불/누계 전액환불/미존재 결제를 고정한다.

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
│   ├── RefundService            refund: 한도 검증(B6 ✅) → REFUND 원장 + 누적액으로 상태 전이
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
| POST | `/api/payments/refund` | `RefundRequest(paymentId, amount, reason)` → `RefundResponse` | `PaymentController.refund` (한도 초과 시 `REFUND_EXCEEDS_PAYMENT` — B6 ✅) |
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
  `PM002` `REFUND_EXCEEDS_PAYMENT`, `PM003` `PAYMENT_NOT_FOUND`). `PM002` 는 환불 한도 검증에서 던진다(B6 ✅),
  `PM003` 은 결제 미존재 시. `PM001`(`PAYMENT_FAILED`)은 아직 미사용(`charge` 가 항상 성공하는 스텁이라).
- ⚠️ 아래는 알려진 결함이다. **새 코드에서 모방하지 말 것.**
  - **B6 — 과다 환불 ✅ 수정됨(2026-06-16)**: (이전) `RefundService.refund()` 가 환불 누계가 결제액을 초과해도
    막지 않아 과다 환불 가능(`REFUND_EXCEEDS_PAYMENT` PM002 미사용). → **환불 전 `기존 누계 + 이번 환불 > 결제액`
    이면 `BusinessException(REFUND_EXCEEDS_PAYMENT)`** 를 던진다(환불/원장 미기록). 회귀 테스트
    `RefundServiceTest.overRefund_isBlocked_throwsRefundExceedsPayment` — [`../docs/known-issues.md`](../docs/known-issues.md) B6.
  - **금액 `double`(B3 / [ADR-0003](../docs/adr/0003-money-as-double.md))**: `amount`·원장·환불 누계 비교가
    전부 `double`. `refundedTotal >= payment.getAmount()` 같은 누적 비교에 부동소수 오차가 끼어들 수 있다.
    금액을 새로 다룰 때 임의 반올림을 끼워넣지 말고 기존 정책을 따른다.
  - **시각은 UTC**: `approvedAt`/`refundedAt` 는 `DateUtils.now()`(=**UTC**)로 찍힌다. batch 집계 타임존 혼용은
    ✅ B7 수정(집계 측을 UTC `Clock` 기준으로 통일)으로 해소됐다 — UTC 저장 시각을 집계할 땐 UTC 기준 날짜를 쓴다.
    `DateUtils` 의 thread-unsafe `SimpleDateFormat`(R3)은 미해결 — 새 시각 처리에서 답습 금지.
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
  (`PaymentClient` → payment `:8082`; R2 ✅ 타입 record 로 교체·R8 ✅ 타임아웃 설정 — 둘 다 2026-06-16 수정).
  payment 의 요청 record(`ChargeRequest`/`RefundRequest`)는 ecommerce·admin 클라이언트가 보내는 와이어 계약과
  필드가 일치해야 한다 — 호출자는 자체 `client/dto/*` record 로 같은 바디를 조립한다(공유 계약 모듈은 미도입).
