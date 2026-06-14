# core-framework 코드 컨벤션

> 이 모듈은 모노레포 공통 컨벤션 [`../../docs/code-conventions.md`](../../docs/code-conventions.md) 을
> 따른다(패키지 루트 `com.legacy.shop`, 한국어 주석/영어 식별자, 생성자 주입, Lombok 없음,
> BOM 기반 버전 선언 등). 단 이 모듈은 **공통 기반 라이브러리**라 일부 규칙이 다르게 적용된다.
> 아래에는 **core-framework 에 한정된 보충·차이점만** 적는다.

## 구조상의 차이

- **기반 라이브러리**라 공통 컨벤션의 `service`/`repository`/`client`/`config` 계층이 **없다**.
  실제 패키지는 `domain`(베이스 엔티티)·`error`·`web` 3개뿐이다.
- `web` 패키지에 **컨트롤러가 없다.** `GlobalExceptionHandler` 는 상위 앱의 컨트롤러 예외를 가로채는
  cross-cutting 빈이다.
- 자체 부트 앱·`application.yml`·포트가 없다. 다른 앱에 링크되는 jar 다.

## 에러: ErrorCode 에 추가하고 BusinessException 으로 던진다 (핵심 규칙)

새 업무 오류는 **모듈별 enum 을 새로 만들지 않고** 전사 단일 `ErrorCode` enum 에 항목을 추가한다.
(이 단일화는 known-issue 이기도 하지만 — 아래 — 현재 컨벤션은 이걸 유지한다.)

```java
// 1) core-framework 의 ErrorCode 에 항목 추가 — (httpStatus, "코드", "한국어 메시지")
SHIPMENT_NOT_FOUND(404, "S001", "배송 정보를 찾을 수 없습니다"),

// 2) 서비스에서 던진다
throw new BusinessException(ErrorCode.SHIPMENT_NOT_FOUND);
// 메시지를 덮어쓰려면:
throw new BusinessException(ErrorCode.SHIPMENT_NOT_FOUND, "주문 " + orderId + " 배송 없음");
```

- 코드값은 영역 접두를 따른다: `C`(공통)·`P`(상품)·`O`(주문)·`CP`(쿠폰)·`PM`(결제)·`U`(고객/권한).
  새 영역이면 새 접두 + 001 부터. 전체 표는 [`architecture.md`](./architecture.md) 참고.
- 예외는 `RuntimeException` 계열(`BusinessException`)이라 throws 선언이 필요 없다. checked 예외를
  새로 만들지 않는다.

## 응답: ApiResponse 로 통일한다

- 성공 응답은 `ApiResponse.success(data)` — 코드 `"0000"`, 메시지 `"OK"`.
- 오류는 컨트롤러에서 직접 `ApiResponse.error(...)` 를 만들지 말고 **`BusinessException` 을 던져**
  `GlobalExceptionHandler` 가 변환하게 둔다(예외 → 응답 변환을 한 곳에 모은다).
- ⚠️ **`ApiResponse` 는 `record` 가 아니다.** 공통 컨벤션은 "DTO=record" 지만, `ApiResponse` 는 jackson
  역직렬화와 제네릭(`<T>`)을 위해 getter/setter 를 가진 **가변 클래스**다. 의도된 예외이니 record 로
  "고치지" 말 것. 반면 일반 도메인 DTO 는 여전히 record 로 만든다.

## 엔티티: BaseTimeEntity 상속 + @EnableJpaAuditing

- 시각 컬럼이 필요한 엔티티는 `BaseTimeEntity` 를 상속한다(`createdAt`/`updatedAt` 자동).
- ⚠️ 감사 필드가 채워지려면 **부트 앱 쪽에 `@EnableJpaAuditing` 이 켜져 있어야** 한다. 이 모듈은
  `AuditingEntityListener` 만 붙이고 auditing 을 활성화하지 않는다(앱의 책임). 새 엔티티에서
  `createdAt` 이 null 이면 이 설정부터 확인한다.
- `BaseTimeEntity` 는 getter 만 두고 setter 가 없다(시각은 프레임워크가 채운다). 이 형태를 유지한다.

## 네이밍 / 주석

- 에러 enum 상수: `UPPER_SNAKE` 업무 의미명(`ORDER_NOT_FOUND`), 코드 문자열은 접두+3자리(`O001`).
- 응답 팩토리: `success`/`error` 동사. 예외 핸들러: `handle*`.
- 클래스 상단에 한 줄 `/** ... */` 로 역할을 적는 기존 스타일을 유지한다(과도한 주석 금지).
- 본문 주석·도메인 용어는 한국어, 식별자/타입은 영어(공통 규칙과 동일).

## ⚠️ 모방 금지 안티패턴 (이 모듈에 존재)

손대는 김에 (테스트 선행 후) 개선하되, **새 코드에서 그대로 답습하지 않는다.** 코드(B/R)는 모노레포
[`../../docs/known-issues.md`](../../docs/known-issues.md) 체계를 따른다.

- **B5 — 페이징 오프셋 오류**: `PageRequestDto.getOffset()` 이 `page*size`. page 가 1-based 라 첫
  페이지를 건너뛴다. 새 페이징 코드는 `(page-1)*size` 로 계산하거나 Spring `Pageable`(0-based)로 변환한다.
- **R4 — 예외 삼킴**: `GlobalExceptionHandler.handleEtc()` 가 비즈니스 외 예외를 **로그 없이** 500 으로
  내린다. 새 예외 처리에는 반드시 로깅(SLF4J)을 넣고, 코드/메시지는 `ErrorCode.INTERNAL_ERROR` 를 쓴다
  (문자열 하드코딩 금지).
- **단일 `ErrorCode` enum**: 모든 모듈 코드가 한 enum 에 모여 비대·드리프트 위험이 있다. 현재 컨벤션은
  이 단일화를 유지하지만, 신규 항목은 영역 접두를 지켜 그룹이 흐트러지지 않게 한다. PM002
  (`REFUND_EXCEEDS_PAYMENT`)처럼 **정의만 하고 안 쓰는 코드를 남기지 않는다**(B6 — 정의했으면 던지는 곳까지).
