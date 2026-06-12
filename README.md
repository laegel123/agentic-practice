# Agentic & Harness Engineering 연습 키트

에이전틱/하네스 엔지니어링이 **하나도 안 입혀진** 대규모 레거시 코드베이스에 그 기법들을 직접 "입히고",
**전/후를 객관적 지표로 비교**하는 연습용 키트입니다.

- **에이전틱 엔지니어링** = 코드베이스를 AI 에이전트가 잘 다루게 만들기(문서/컨텍스트/구조).
- **하네스 엔지니어링** = 에이전트를 둘러싼 도구/자동화(빌드·테스트·CI·정적분석·권한설정·검증루프).

## 구성

```
agentic-practice/
├─ legacy-ecommerce/   ◀ 레거시 코드베이스. 에이전트를 "여기"에 물려서 연습한다.
├─ evaluation/         ◀ 지표·벤치마크·정답지. (에이전트에 노출 금지 — 상위에 둠)
└─ guides/             ◀ 에이전틱/하네스 가이드 + 플레이북
```

> **핵심 규칙:** 에이전트는 항상 `legacy-ecommerce/` 안에서만 돌린다.
> `evaluation/`(정답지)와 `guides/`는 그 바깥에 있어 자동으로 읽히지 않는다 → 벤치마크 공정성 유지.

## 레거시 코드베이스 한눈에

Spring Boot 3.5 멀티모듈 MSA(Gradle, Java 21, H2 인메모리/파일, 서비스 간 RestTemplate).

| 모듈 | 포트 | 역할 |
|------|:----:|------|
| `common-util` | - | 날짜/문자열/금액/검증/암호 유틸 |
| `core-framework` | - | 공통 엔티티·응답·예외·페이징 |
| `ecommerce-service` | 8081 | 상품/장바구니/주문/재고/쿠폰/고객 |
| `payment-service` | 8082 | 결제/환불/결제수단/원장 |
| `admin` | 8083 | 어드민(HTTP로 서비스 호출) |
| `batch` | - | 정산/집계/재고대사/장바구니정리 |

**일부러 안 넣은 것:** CLAUDE.md·테스트·CI·정적분석·포매터·버전카탈로그·settings.json.
**일부러 심은 것:** 기능 버그 11개(금액 반올림, 할인 후 과세, 재고 이중차감, 환불 초과, 쿠폰 만료경계, 정산에 취소포함, 페이징 off-by-one, 어드민 권한누락, 장바구니 합계, 타임존, SQL인젝션) + 구조 악취 13종.
전체 목록은 `evaluation/seeded-defects.md`(스포일러).

## 빠른 시작

```bash
cd legacy-ecommerce
./gradlew clean build                    # 6개 모듈 빌드 (테스트는 일부러 없음)
./gradlew :payment-service:bootRun       # 8082
./gradlew :ecommerce-service:bootRun     # 8081 (첫 기동 시 시드 데이터)
```
동작/버그 확인 예:
```bash
curl "http://localhost:8081/api/products?page=1&size=5"   # 페이징 버그: 첫 페이지를 건너뜀
curl "http://localhost:8081/api/products/1"
```
DB 초기화: `~/legacyshopdb.*`, `~/legacypaydb.*` 삭제 후 재기동.

## 연습 방법 (요약)

1. **베이스라인 측정** — `evaluation/scripts/measure.ps1` + 벤치마크 과제를 레거시 상태로 에이전트에게 시켜 `evaluation/agent-scorecard.md` 기록.
2. **에이전틱 입히기** — `guides/agentic-engineering-guide.md` → 재측정.
3. **하네스 입히기** — `guides/harness-engineering-guide.md` → 재측정.
4. **비교/회고** — 점수표 전/후 비교.

전체 절차: `guides/playbook.md`.

## 지표 두 갈래

- **정적 저장소 건강도** (`evaluation/static-scorecard.md`): 빌드/테스트/커버리지/정적분석/문서/CI/파일크기 등.
- **에이전트 효율** (`evaluation/agent-scorecard.md`): 성공률·턴·토큰·시간·권한프롬프트·자가검증·회귀.

> 검증 가설: 입힌 뒤 → **성공률↑, 턴·토큰·시간↓, 권한프롬프트↓, 자가검증↑, 회귀↓**

## 참고

같은 워크스페이스의 `overseas/overseas-cost-app/` 는 이미 하네스가 갖춰진 프로젝트(CLAUDE.md·GitHub Actions·docs)라 "목표 상태"의 실제 예시로 참고하기 좋다.
