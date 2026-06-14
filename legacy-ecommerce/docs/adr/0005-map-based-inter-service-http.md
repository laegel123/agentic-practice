# ADR-0005: 서비스 간 HTTP를 raw Map으로 통신

- **상태**: Accepted · 재검토 대상
- **날짜**: 2026-06-12 (사후 기록)
- **결정자**: 원 개발팀 (복원)

## 맥락 (Context)

`ecommerce-service` 는 결제를 위해 `payment-service` 를, `admin` 은 이커머스/결제 서비스를
HTTP로 호출해야 한다. 별도 공유 API 계약 모듈이나 클라이언트 생성 도구는 도입하지 않았다.

## 결정 (Decision)

`RestTemplate` 으로 호출하고, 요청·응답을 **타입 DTO 없이 `Map` 으로** 주고받는다.
응답은 `Map.class` 로 역직렬화한 뒤 필요한 값을 캐스팅해 꺼낸다(예: `((Number) data.get("paymentId")).longValue()`).
해당 코드는 `ecommerce/client/PaymentClient`, `admin/client/ShopGateway` 두 곳.

## 대안 (Alternatives)

- **공유 계약(DTO) 모듈**: 요청/응답을 타입으로 정의해 양쪽이 공유. 컴파일 타임 안전성이 생기지만
  모듈 결합과 관리 비용이 든다.
- **OpenAPI 스펙 + 클라이언트 코드 생성**: 계약을 명세화. 초기 도입 비용이 크다.
- **선언적 HTTP 클라이언트(`@HttpExchange`/Feign류)**: 보일러플레이트 감소. 당시엔 도입하지 않았다.

## 결과 (Consequences)

- (+) 새 의존성/계약 모듈 없이 즉시 호출 가능했다.
- (−) **계약이 코드에 드러나지 않는다.** 필드명 오타·타입 변경이 런타임에야 터지고, 컴파일러가
  잡아주지 못한다. 응답 구조가 바뀌면 캐스팅 지점에서 `ClassCastException`/`NPE` 위험.
- (−) `RestTemplate` 에 타임아웃이 없어(`new RestTemplate()`) 결제 지연 시 호출 스레드가 묶일 수
  있다([known-issues.md](../known-issues.md) R8).
- (−) 호출 대상 URL이 `@Value` 기본값으로 하드코딩되어 있다(R5).
- **재검토 트리거**: 서비스 간 계약이 늘거나 자주 바뀌는 시점. 응답 DTO 도입 + 타임아웃/에러
  처리 정비를 함께 진행한다. 동작보존 리팩토링이므로 호출 흐름을 고정하는 테스트를 먼저 둔다.
