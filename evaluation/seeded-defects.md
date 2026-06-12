# 심어둔 결함 카탈로그 (SPOILER)

> ⚠️ 이 파일은 **정답지**입니다. 벤치마크를 진행하는 에이전트의 작업 디렉토리(`legacy-ecommerce/`)에 노출하지 마세요.
> 에이전트는 항상 `legacy-ecommerce/` 안에서만 돌리고, 이 `evaluation/` 폴더는 그 바깥(상위)에 둡니다.

경로는 모두 `legacy-ecommerce/` 기준입니다.

---

## 1. 기능 버그 (객관적으로 검증 가능)

| ID | 한 줄 요약 | 위치 | 카테고리 | 라이브 재현 결과 |
|----|-----------|------|----------|------------------|
| **D1** | 금액 반올림이 사실은 '버림'이라 1센트씩 어긋남 | `common-util/.../common/util/MoneyUtils.java` `round()` (`Math.floor`) | 금액/정확성 | 주문 합계 32.98→**32.97**, 세금 8.00→**7.99** |
| **D2** | 할인을 세금 '뒤'에 빼서 과청구 (세금을 할인 전 소계에 매김) | `ecommerce-service/.../service/PricingService.java` `calculate()` | 금액/정확성 | SAVE20 적용 주문 total **71.97** (정상 ≈70.37) |
| **D3** | 주문 시 재고가 두 번 차감됨 (reserve + confirm 둘 다 차감) | `ecommerce-service/.../service/OrderService.java` `placeOrder()` + `InventoryService.confirm()` | 재고/정확성 | 키보드 2개 주문 → 재고 50→**46** (정상 48) |
| **D4** | 환불 누적액이 결제액을 넘어도 막지 않음 | `payment-service/.../service/RefundService.java` `refund()` | 결제/검증누락 | 71.97 결제에 60+60=120 환불 **통과** |
| **D5** | 쿠폰 만료일 경계 오류 (만료일 당일을 만료 처리) | `ecommerce-service/.../service/CouponService.java` `getValidCoupon()` | 날짜/경계 | 오늘 만료 쿠폰 WELCOME **거부됨** |
| **D6** | 정산 매출에 '취소' 주문까지 합산 | `batch/.../job/SettlementJob.java` `settle()` | 배치/정확성 | 정산 **180.0** (PAID 150 + CANCELLED 30) |
| **D7** | 상품목록 페이지네이션 off-by-one (첫 페이지를 건너뜀) | `core-framework/.../web/PageRequestDto.java` `getOffset()` (`page*size`) | 조회/경계 | `?page=1&size=5` 가 6~10번 상품 반환 (page=0이 1~5) |
| **D8** | 어드민 환불 API 만 권한 체크 누락 | `admin/.../web/AdminRefundController.java` | 보안/인증 | 토큰 없이 `/admin/refunds` **통과** (`/admin/products`는 401) |
| **D9** | 장바구니 합계가 수량을 무시 (단가만 합산) | `ecommerce-service/.../service/CartService.java` `cartTotal()` | 금액/정확성 | 키보드 3개 → 합계 **29.99** (정상 89.97, 라인합계는 89.97로 맞음) |
| **D10** | 주문시각을 UTC로 저장, 집계는 서버 로컬 날짜 기준 → 자정 부근 하루 밀림 | `common-util/.../common/util/DateUtils.java` `now()` + `batch/.../job/DailySalesAggregationJob.java` | 날짜/타임존 | 코드분석 (KST 00:00~09:00 구간 재현). 테스트로 확인 권장 |
| **D11** | 상품검색 네이티브쿼리에 검색어 문자열 직접 결합 (SQL 인젝션) | `ecommerce-service/.../repository/ProductSearchDao.java` `searchByName()` | 보안/인젝션 | `keyword=' OR 1=1 OR name LIKE '` → active 필터 우회, 전체 12건 반환 (정상 'zzz'는 0건) |

---

## 2. 구조적 악취 (리팩터링/품질 과제용)

| ID | 요약 | 위치 |
|----|------|------|
| **S1** | 거대 메서드/God class: 재고·가격·결제·장바구니·알림을 한 메서드에서 처리 | `OrderService.placeOrder()` |
| **S2** | 금액을 `double` 로 다룸 (primitive obsession, 부동소수 오차 근원) | 전 모듈 (`MoneyUtils`, 엔티티의 `price/amount/total`) |
| **S3** | 가격계산 로직 복붙 중복 (D2 버그까지 같이 복사됨) | `admin/.../util/AdminPriceCalculator.java` vs `PricingService` |
| **S4** | 비밀번호 MD5 + salt 없음 | `common-util/.../common/util/CryptoUtils.java` |
| **S5** | 예외 삼킴 (로그 없이 null 반환/뭉갬) | `GlobalExceptionHandler`(catch-all), `DateUtils.parse()`, `JsonUtils.toJson()` |
| **S6** | thread-unsafe `SimpleDateFormat` 을 static 공유 | `DateUtils.SDF` |
| **S7** | N+1 조회 (주문 목록 → 주문아이템 지연로딩 반복) | `OrderService.getByCustomer()` → `OrderController.toDto()` |
| **S8** | 공유 DB 안티패턴 + 엔티티/enum 중복 정의 | `batch` 가 ecommerce 와 같은 `~/legacyshopdb` 사용, `batch/.../domain/OrderRow.java`·`OrderStatus.java` 중복 |
| **S9** | 설정/URL/시크릿 하드코딩 | `PaymentClient`, `ShopGateway`, `admin` 의 `application.yml`(`admin.token`) |
| **S10** | 레이어 붕괴: 컨트롤러/게이트웨이가 RestTemplate 직접 호출, 응답을 `Map` 으로 처리 | `admin` 전반, `PaymentClient` |
| **S11** | 의존성 버전 중앙관리 없음 (버전 카탈로그 미사용, 모듈마다 직접 박음) | 루트 `build.gradle`, `common-util/build.gradle`(`commons-lang3:3.12.0`) |
| **S12** | 미사용 의존성 방치 | `common-util` 의 `commons-lang3` (코드에서 안 씀, `StringUtils` 를 자체 재구현) |
| **S13** | 연관관계 일관성 없음 (어떤 건 `@ManyToOne`, 어떤 건 raw id) | `Product.categoryId`, `Inventory.productId`, `CartItem.productId` 등 |

---

## 3. 빠져있는 것들 (에이전틱/하네스 공백 = 입혀야 할 대상)

| ID | 없는 것 | 영향 |
|----|---------|------|
| **A1** | `CLAUDE.md` / 에이전트용 컨텍스트 | 에이전트가 매번 구조를 처음부터 탐색 |
| **A2** | 테스트 (단위/통합) | 회귀 감지 불가, 자가검증 불가 |
| **A3** | CI 파이프라인 (`.github/workflows`) | 빌드/검증 자동화 없음 |
| **A4** | 정적분석·포매터 (Checkstyle/SpotBugs/PMD/Spotless), 커버리지(Jacoco) | 품질 게이트 없음 |
| **A5** | 모듈별 README / 아키텍처 문서 / ADR / 도메인 용어집 | 의도·결정 이력 부재 |
| **A6** | `.claude/settings.json` (권한 허용목록·훅) | 권한 프롬프트 다발, 가드레일 없음 |
| **A7** | pre-commit 훅 / 슬래시 커맨드 / 검증 루프 | 반복 작업 수동, 실수 차단 없음 |

---

## 요약 통계 (베이스라인)

- 기능 버그: **11개** (D1~D11, 그중 9개는 API/배치로 즉시 재현, 2개는 코드분석/테스트로 확인)
- 구조적 악취: **13종** (S1~S13)
- 빠진 affordance: **7종** (A1~A7)
