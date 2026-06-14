# core-framework 아키텍처

> 이 문서는 `core-framework` 모듈 **내부** 구조에 집중한다. 모듈 간 의존 그래프·전체 그림은
> 모노레포 [`../../docs/architecture.md`](../../docs/architecture.md) 를 본다.

## 책임

전 서비스가 공유하는 **공통 기반 계층**. 네 가지를 제공한다.

1. **응답 포맷** — `ApiResponse<T>` (`{ code, message, data }`). 모든 REST 응답의 봉투.
2. **에러 체계** — `ErrorCode`(전사 단일 enum) + `BusinessException`(이를 들고 다니는 런타임 예외) +
   `GlobalExceptionHandler`(예외를 `ApiResponse` 로 변환하는 `@RestControllerAdvice`).
3. **페이징 요청** — `PageRequestDto`(page/size, offset 계산).
4. **감사 엔티티 베이스** — `BaseTimeEntity`(`createdAt`/`updatedAt`).

도메인 로직·상태·영속(repository)·HTTP 클라이언트가 **없다**. Spring 에 의존하지만 자체 부트 앱이
아니라 다른 앱에 링크되는 라이브러리 jar 다.

## 의존 그래프상 위치

`common-util` 바로 위, 실행 앱들 바로 아래에 있다.

```
common-util            (순수 Java 유틸 — 최하단)
   └── core-framework  (← 여기: 공통 기반)
          ├── ecommerce-service
          ├── payment-service
          ├── admin
          └── batch
```

- 위로 의존: `common-util` + Spring/jakarta/jackson(아래 빌드 절 참고).
- 아래에서 의존받음: ecommerce·payment·admin 은 `web`(`ApiResponse`/예외/페이징)·`error`·`domain`
  (`BaseTimeEntity`)을 모두 쓴다. batch 는 web 의존이 없어 주로 `error`/공통 클래스만 닿는다.
- 따라서 `ApiResponse` 모양·`ErrorCode` 코드값·`BaseTimeEntity` 컬럼 변경은 **전 서비스로 파급**된다.

## 패키지 / 계층

공통 컨벤션의 `web / service / domain / repository / client / config` 중 **`service`/`repository`/
`client`/`config` 가 없다**(기반 라이브러리라 비즈니스 상태·영속·외부 호출이 없으므로). 실제 패키지는 3개다.

```
com.legacy.shop.core
├── domain    BaseTimeEntity
├── error     ErrorCode, BusinessException
└── web       ApiResponse, GlobalExceptionHandler, PageRequestDto
```

> `web` 패키지에 컨트롤러는 없다. `GlobalExceptionHandler`(`@RestControllerAdvice`)는 상위 앱들의
> 컨트롤러 예외를 가로채는 cross-cutting 빈으로, 앱의 컴포넌트 스캔에 포함될 때 동작한다.

## 클래스 인벤토리

| 클래스 | 역할 | 주요 멤버 (시그니처) | 비고 |
|--------|------|---------------------|------|
| `BaseTimeEntity` | 감사 컬럼 베이스 | `getCreatedAt()`, `getUpdatedAt()`; `@MappedSuperclass`, `@EntityListeners(AuditingEntityListener)` | ⚠️ 동작하려면 부트 앱에 `@EnableJpaAuditing` 필요(이 모듈은 켜지 않음). getter 만, setter 없음 |
| `ErrorCode` | 전사 에러코드 enum | `getStatus()`(HTTP), `getCode()`(`"C001"` 등), `getMessage()`(한국어) | ⚠️ 모듈 구분 없는 **단일 enum**. `REFUND_EXCEEDS_PAYMENT`(PM002)는 정의만, 미사용(B6) |
| `BusinessException` | 업무 예외 | `BusinessException(ErrorCode)`, `BusinessException(ErrorCode, String)`, `getErrorCode()` | `RuntimeException` 상속. 메시지 미지정 시 `ErrorCode.getMessage()` 사용 |
| `ApiResponse<T>` | 공통 응답 봉투 | `static success(T)`→`("0000","OK",data)`, `static error(String,String)`, getter/**setter** | ⚠️ DTO=record 컨벤션과 달리 가변 클래스(jackson 역직렬화·제네릭 때문). 성공 코드 `"0000"` 은 `ErrorCode` 밖 리터럴 |
| `GlobalExceptionHandler` | 전역 예외 처리 | `handleBusiness(BusinessException)`, `handleEtc(Exception)` | ⚠️ `handleEtc` 가 **로그 없이** 500 일괄 처리(R4), `ErrorCode.INTERNAL_ERROR` 대신 문자열 하드코딩 |
| `PageRequestDto` | 페이징 요청 | `getPage()`/`setPage()`(1-based, 기본 1), `getSize()`/`setSize()`(기본 20), `getOffset()` | ⚠️ `getOffset()`=`page*size` → 1-based 라 첫 페이지 건너뜀(B5). `(page-1)*size` 가 맞다 |

## 응답 / 에러 계약

한 요청의 정상·오류 흐름은 다음과 같이 `ApiResponse` 봉투로 통일된다.

- **성공**: 컨트롤러가 `ApiResponse.success(data)` 반환 → `{ "code": "0000", "message": "OK", "data": ... }`.
- **업무 오류**: 서비스가 `throw new BusinessException(ErrorCode.X)` → `GlobalExceptionHandler.handleBusiness`
  가 `ErrorCode.getStatus()` 로 HTTP 상태, body 는 `ApiResponse.error(code, message)`.
- **그 외 예외**: `handleEtc` 가 **무조건 500 + `{ "code": "C001", ... }`** 로 내린다(R4 — 로깅 없음,
  원인 은폐). 코드/메시지를 `ErrorCode.INTERNAL_ERROR` 에서 가져오지 않고 문자열로 박아 둔 점도 부채.

> 성공 코드 `"0000"` 과 오류 코드 체계(`C/P/O/CP/PM/U` 접두)가 **서로 다른 출처**다 — 성공은
> `ApiResponse` 의 리터럴, 오류는 `ErrorCode` enum. 코드값을 다룰 때 이 비대칭을 유의한다.

### ErrorCode 코드 체계

| 접두 | 영역 | 예 |
|------|------|----|
| `C` | 공통 | `INTERNAL_ERROR`(C001), `INVALID_INPUT`(C002), `NOT_FOUND`(C003) |
| `P` | 상품/재고 | `PRODUCT_NOT_FOUND`(P001), `OUT_OF_STOCK`(P002) |
| `O` | 주문 | `ORDER_NOT_FOUND`(O001), `ORDER_ALREADY_CANCELLED`(O002), `EMPTY_CART`(O003) |
| `CP` | 쿠폰 | `COUPON_NOT_FOUND`(CP001), `COUPON_EXPIRED`(CP002) |
| `PM` | 결제 | `PAYMENT_FAILED`(PM001), `REFUND_EXCEEDS_PAYMENT`(PM002, 미사용), `PAYMENT_NOT_FOUND`(PM003) |
| `U` | 고객/권한 | `CUSTOMER_NOT_FOUND`(U001), `UNAUTHORIZED`(U002) |

## 상태와 스레드 안전성

`common-util` 과 달리 **공유 가변 static 상태가 없다**(`SimpleDateFormat` 같은 R3 류 문제 없음).

| 구성요소 | 상태 | 스레드 안전 |
|----------|------|------------|
| `ErrorCode` | enum 상수(불변) | ✅ |
| `BusinessException` | 요청별 인스턴스 | ✅ (공유 안 함) |
| `ApiResponse` | 요청별 가변 인스턴스 | ✅ (공유 안 함) |
| `PageRequestDto` | 요청별 가변 바인딩 객체 | ✅ (공유 안 함) |
| `GlobalExceptionHandler` | 무상태 싱글톤 빈 | ✅ |
| `BaseTimeEntity` | 엔티티별 인스턴스 필드 | ✅ (영속성 컨텍스트 규약 내) |

## 의존성 / 빌드

```gradle
// core-framework/build.gradle
dependencies {
    implementation project(':common-util')

    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-web'
    implementation 'org.springframework.data:spring-data-jpa'
    implementation 'jakarta.persistence:jakarta.persistence-api'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

- **내부 의존**: `:common-util` 하나. **외부**: Spring(context/web/data-jpa)·jakarta.persistence·
  jackson — 전부 Spring Boot BOM 이 버전 관리하므로 버전 문자열 없이 선언([ADR-0004](../../docs/adr/0004-no-gradle-version-catalog.md)).
- `spring-web` 의존은 `@RestControllerAdvice`·`ResponseEntity`(`GlobalExceptionHandler`)용,
  `spring-data-jpa`+`jakarta.persistence` 는 `BaseTimeEntity` 감사용, jackson 은 `ApiResponse` 직렬화용.
- 빌드 산출물은 라이브러리 jar 다. 실행(`bootRun`) 대상이 아니다. 빌드는 `.\gradlew.bat :core-framework:build`.
