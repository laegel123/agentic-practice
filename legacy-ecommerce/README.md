# legacy-shop

사내 커머스 백엔드. 멀티모듈 프로젝트.

## 모듈

- common-util : 공통 유틸
- core-framework : 공통 베이스
- ecommerce-service : 상품/주문 (8080)
- payment-service : 결제
- admin : 어드민
- batch : 배치

## 빌드 / 실행

```
./gradlew build
./gradlew :ecommerce-service:bootRun
```

DB 는 H2 씀.

TODO: 문서 정리 필요 (담당자 퇴사로 방치됨)
