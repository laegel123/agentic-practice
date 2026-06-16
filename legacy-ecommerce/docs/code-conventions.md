# 코드 컨벤션

> legacy-shop 코드베이스에서 **현재 통용되는** 규칙을 정리한다. "이상적인 규칙"이 아니라
> 실제 코드가 따르는 패턴이다. 새 코드는 일관성을 위해 이 규칙을 따른다. 단, 여기서
> **⚠️ 표시된 항목은 알려진 안티패턴**이며 모방하지 말고 [known-issues.md](./known-issues.md)의
> 개선 대상으로 다룬다.

## 패키지 구조

루트 그룹은 `com.legacy.shop`. 모듈별로 다음 하위 패키지를 사용한다.

```
com.legacy.shop.<module>.web         REST 컨트롤러
com.legacy.shop.<module>.service     비즈니스 로직
com.legacy.shop.<module>.domain      JPA 엔티티 + enum
com.legacy.shop.<module>.repository  Spring Data JPA 리포지토리
com.legacy.shop.<module>.dto         요청/응답 DTO
com.legacy.shop.<module>.client      외부 서비스 HTTP 클라이언트
com.legacy.shop.<module>.config      @Configuration 빈 정의
```

`<module>` 예: `ecommerce`, `payment`, `admin`, `batch`, `core`, `common`.

각 Spring Boot 앱은 `@SpringBootApplication(scanBasePackages = "com.legacy.shop")` 로 **전체
그룹 패키지**를 스캔한다. 이래야 `core-framework` 의 `@RestControllerAdvice` 등 공통 빈이 함께 잡힌다.

## 계층 규칙

`web → service → repository → domain` 의 단방향 흐름.

- **컨트롤러**(`web`): 얇게 유지. 요청 DTO 받기 → 서비스 호출 → 엔티티를 응답 DTO로 변환(`toDto`).
  DTO 매핑은 컨트롤러의 private 메서드로 둔다(`OrderController.toDto` 참고).
- **서비스**(`service`): `@Service`. 트랜잭션 경계는 서비스 메서드에 `@Transactional` 로 둔다(클래스가 아닌 메서드 단위).
- **리포지토리**(`repository`): `JpaRepository<T, Long>` 인터페이스 상속. 파생 쿼리 메서드 사용(`findByCustomerId`, `findByCode`, `findByPaymentId`).

## 의존성 주입

- **생성자 주입만 사용**한다. `@Autowired` 필드 주입은 쓰지 않는다.
- 필드는 `private final` 로 선언하고 생성자에서 대입한다.

```java
@Service
public class CouponService {
    private final CouponRepository couponRepository;
    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }
}
```

## 엔티티 규칙

- `core-framework` 의 `BaseTimeEntity` 를 상속해 `createdAt` / `updatedAt` 을 얻는다.
  감사 컬럼이 채워지려면 부트 앱에 `@EnableJpaAuditing` 이 있어야 한다(ecommerce, payment 는 적용됨).
- **Lombok 미사용**. getter/setter 를 손으로 작성한다. 새 엔티티도 같은 스타일을 유지한다.
- PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` + `Long id`.
- enum 필드는 `@Enumerated(EnumType.STRING)` 으로 저장한다(`Order.status` 참고).
- 테이블명이 예약어와 겹치면 `@Table(name = "orders")` 처럼 명시한다.
- ⚠️ **연관관계 대신 ID 참조**: `Product.categoryId` 처럼 FK 연관(`@ManyToOne`)을 맺지 않고 `Long` id만 들고 있는 경우가 많다. 참조 무결성 보장이 없다.
- ⚠️ **금액은 `double`**: `price`, `subtotal`, `amount` 등 모든 돈을 `double` 로 저장/계산한다. 정밀도 문제가 있다. [ADR-0003](./adr/0003-money-as-double.md).

## DTO 규칙

- 요청/응답 DTO는 **Java `record`** 로 정의한다(`PricingResult`, `OrderResponse`, `ChargeRequest` 등).
- 엔티티를 그대로 노출하지 않고 응답 DTO로 변환해서 내려준다.

## API 응답 / 에러 처리

- 모든 컨트롤러는 `ApiResponse<T>` 로 감싸 반환한다.
  - 성공: `ApiResponse.success(data)` → `{ "code": "0000", "message": "OK", "data": ... }`
  - 실패: `ApiResponse.error(code, message)`
- 도메인 오류는 `throw new BusinessException(ErrorCode.XXX)` 로 던진다.
  - 새 에러는 **`core-framework` 의 단일 `ErrorCode` enum**에 추가한다(모듈 구분 없이 한 enum에 모은다).
  - 코드 체계: `C00x`(공통), `P00x`(상품/재고), `O00x`(주문), `CP00x`(쿠폰), `PM00x`(결제), `U00x`(고객/권한).
- `GlobalExceptionHandler`(`@RestControllerAdvice`)가 전역에서 받아 `ApiResponse.error` 로 변환한다.
  - ⚠️ 현재 `BusinessException` 외의 예외는 원인을 **로그도 남기지 않고** 일괄 500(`C001`)으로 삼킨다. [known-issues.md](./known-issues.md).

## 유틸리티 사용

`common-util` 의 정적 유틸을 우선 재사용한다(새로 만들지 말 것).

- `MoneyUtils` — 금액 계산. `round`, `applyTax`, `taxOf`, `multiply`, `discount`, `format`, 상수 `TAX_RATE=0.1`.
  - ✅ `MoneyUtils.round()` 는 소수 둘째자리 **반올림(HALF_UP)** 이다(B3 수정 2026-06-16; 이전엔 `Math.floor` 버림).
- `DateUtils` — 시각. `now()` 는 **UTC** `LocalDateTime`(주문/환불 시각 저장용), `localToday()` 는 **서버 로컬** `LocalDate`(집계/조회용).
  - ⚠️ 같은 유틸 안에서 UTC와 서버 로컬을 혼용한다. 또 `SDF` 가 thread-unsafe 한 static `SimpleDateFormat` 이다.
- `StringUtils.isBlank` 등 — 입력 검증 보조.

⚠️ **로직 복제 금지**: `admin` 의 `AdminPriceCalculator` 처럼 `PricingService` 계산식을 복사해 둔 사례가 있다. 새 코드에서는 계산 로직을 한 곳(`MoneyUtils`/`PricingService`)에 모은다.

## 페이징

- `core-framework` 의 `PageRequestDto`(page는 **1-based**, 기본 size 20)를 컨트롤러 파라미터로 받는다.
- ⚠️ `getOffset()` 이 `page * size` 라 1-based 기준에서 오프셋이 어긋난다. size 상한도 강제하지 않는다.

## 로깅

- ⚠️ 현재 다수 지점에서 `System.out.println` 으로 로그를 남긴다(`OrderService`, `BatchRunner`, 배치 잡들, `DataSeeder`).
- **새 코드는 SLF4J** (`private static final Logger log = LoggerFactory.getLogger(...)`)를 사용한다. Spring Boot 가 Logback을 기본 제공한다.

## 설정

- 모듈별 `src/main/resources/application.yml`. 포트/DB/외부 URL/토큰을 여기서 관리한다.
- ⚠️ 서비스 URL, `admin.token`(=`admin-secret`), DB 경로가 하드코딩 기본값으로 들어가 있다. 운영에서는 환경변수/프로파일로 외부화가 필요하다.

## 빌드 / 버전

- Java 21, UTF-8. 루트 `subprojects` 블록이 공통 설정을 적용한다.
- 버전 카탈로그 미사용. Spring 의존성은 Boot BOM이 버전을 관리하므로 **버전 문자열 없이** 선언한다(`spring-boot-starter-web`). BOM 밖 의존성만 버전을 직접 박는다(`commons-lang3:3.12.0`). [ADR-0004](./adr/0004-no-gradle-version-catalog.md).

## 주석 / 언어

- 코드 주석과 도메인 용어는 한국어를 쓴다(기존 코드 스타일 유지). 식별자/타입은 영어.
- 새 주석도 주변 밀도에 맞춘다 — 과도한 주석을 추가하지 않는다.
