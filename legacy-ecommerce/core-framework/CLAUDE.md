# core-framework

> legacy-shop 의 **공통 기반 라이브러리**. 모든 웹 앱이 공유하는 응답 포맷(`ApiResponse`)·에러 체계
> (`ErrorCode`/`BusinessException`)·전역 예외 처리·페이징 요청·감사 컬럼 베이스 엔티티를 제공한다.
> Spring 에 의존하지만 **실행 대상이 아니다**(`application.yml`·포트·엔드포인트 없음 — 라이브러리 jar).
> 의존 그래프상 `common-util` 바로 위, 앱들 바로 아래라 ecommerce·payment·admin·batch 가 모두 이걸
> 의존한다. 전체 시스템 맥락·기술부채 백로그는 모노레포 문서 [`../docs/`](../docs/) 를 본다.

## 정체성

- Java 21 / Spring Boot 3.5.15 / Gradle. **공유 라이브러리** — 자체 부트 앱·포트·`application.yml` 없음.
- 의존: `:common-util` + Spring(`spring-context`/`spring-web`/`spring-data-jpa`)·`jakarta.persistence`·
  `jackson-databind`. 전부 Boot BOM 버전(버전 문자열 없음 — [ADR-0004](../docs/adr/0004-no-gradle-version-catalog.md)).
- 내부 모듈 의존은 `common-util` 하나뿐. 역으로 거의 모든 상위 모듈이 이걸 의존하므로 **변경 파급이 크다**
  (응답 포맷·에러코드 변경은 전 서비스에 전파).
- 패키지 `com.legacy.shop.core` 아래 `domain`·`error`·`web` 3개. 공통 컨벤션의 `service`/`repository`/
  `client`/`config` 계층은 **없다**(상태·영속성·HTTP 호출이 없는 기반 라이브러리이므로).

## 빌드 / 테스트

프로젝트 루트(`legacy-ecommerce/`)에서 실행한다. **Windows PowerShell**은 `.\gradlew.bat`,
Bash 도구(POSIX 셸)는 `./gradlew`.

```powershell
.\gradlew.bat :core-framework:build    # 빌드 (라이브러리 jar)
.\gradlew.bat :core-framework:test     # 테스트 — 현재 테스트 소스 없음
```

> 실행(`bootRun`) 대상이 아니다. 다른 모듈에 링크되는 라이브러리 jar 일 뿐이다.

## 구조 한눈에

```
src/main/java/com/legacy/shop/core/
├── domain/
│   └── BaseTimeEntity          감사 컬럼(createdAt/updatedAt) @MappedSuperclass — 엔티티가 상속
├── error/
│   ├── ErrorCode               전사 단일 에러 enum (status + code + message)
│   └── BusinessException       ErrorCode 를 들고 다니는 RuntimeException
└── web/
    ├── ApiResponse<T>          공통 응답 포맷 { code, message, data } — success/error 팩토리
    ├── GlobalExceptionHandler  @RestControllerAdvice 전역 예외 → ApiResponse
    └── PageRequestDto           페이징 요청(page 1-based, size)
```

| 클래스 | 책임 | ⚠️ 함정 |
|--------|------|---------|
| `BaseTimeEntity` | 생성/수정 시각 공통 컬럼 | 동작하려면 부트 앱에 `@EnableJpaAuditing` 필요(이 모듈은 켜지 않음). getter 만, setter 없음 |
| `ErrorCode` | 전사 에러코드 | 모듈 구분 없이 **단일 enum**(비대·드리프트). `REFUND_EXCEEDS_PAYMENT`(PM002)는 B6 수정으로 `RefundService` 에서 사용됨(✅) |
| `BusinessException` | 업무 예외 | `RuntimeException` 상속, throws 불필요 |
| `ApiResponse<T>` | 공통 응답 | DTO=record 컨벤션과 달리 **가변 클래스+setter**(jackson 역직렬화용). 성공 코드 `"0000"` 은 enum 밖 리터럴 |
| `GlobalExceptionHandler` | 전역 예외 처리 | `handleEtc()` 가 예외를 **로그 없이** 일괄 500 으로 삼킴(R4). `ErrorCode.INTERNAL_ERROR` 대신 문자열 하드코딩 |
| `PageRequestDto` | 페이징 요청 | `getOffset()` 이 `page*size` → 1-based page 에서 첫 페이지 건너뜀(B5) |

## 이 모듈에서 일할 때 주의점

- **기반 라이브러리 = 파급 최대.** `ApiResponse` 모양, `ErrorCode` 코드값, `BaseTimeEntity` 컬럼을
  바꾸면 모든 서비스의 응답/에러/스키마로 전파된다. 변경 전 영향 범위를 항상 의식한다.
- **에러는 새로 만들지 말고 `ErrorCode` 에 추가.** 새 업무 예외는 단일 `ErrorCode` enum 에 항목을 더하고
  `throw new BusinessException(ErrorCode.X)` 로 던진다. 모듈별 enum 을 새로 만들지 않는다(현 컨벤션).
- **응답은 항상 `ApiResponse`.** 성공은 `ApiResponse.success(data)`(코드 `"0000"`), 실패는 컨트롤러에서
  직접 만들지 말고 `BusinessException` 을 던져 `GlobalExceptionHandler` 가 변환하게 둔다.
- ⚠️ 아래는 known-issues 등록 결함이다. **새 코드에서 모방하지 말고**, 손대는 김에 (테스트 선행 후) 개선한다.
  - **B5 — 페이징 오프셋 오류**: `PageRequestDto.getOffset()` 이 `page*size`. page 가 1-based 라 첫
    페이지를 건너뛴다(`(page-1)*size` 가 맞다).
  - **R4 — 예외 삼킴**: `GlobalExceptionHandler.handleEtc()` 가 비즈니스 외 예외를 **로그 없이** 500 으로
    내린다. 원인 추적 불가 — 개선 시 로깅(SLF4J)을 넣고 `ErrorCode.INTERNAL_ERROR` 를 쓴다.
  - **단일 `ErrorCode` enum**: 모든 모듈 코드가 한 enum 에 몰려 비대·드리프트 위험. PM002
    (`REFUND_EXCEEDS_PAYMENT`)는 B6 수정으로 `RefundService` 에서 던져진다(✅ 2026-06-16). PM001(`PAYMENT_FAILED`)은 여전히 미사용.
  - 전체 목록은 모노레포 [`../docs/known-issues.md`](../docs/known-issues.md).
- 금액은 전사적으로 `double`(배경 [ADR-0003](../docs/adr/0003-money-as-double.md)). `ApiResponse` 의
  `data` 에 금액이 실려도 동일하다.

## 더 읽기

모노레포 공통 문서: [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/code-conventions.md`](../docs/code-conventions.md) · [`../docs/known-issues.md`](../docs/known-issues.md)(core 항목 B5·R4·B6) · [`../docs/adr/`](../docs/adr/)
