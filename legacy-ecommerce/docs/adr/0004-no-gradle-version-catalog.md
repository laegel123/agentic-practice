# ADR-0004: Gradle 버전 카탈로그 미사용

- **상태**: Accepted
- **날짜**: 2026-06-12 (사후 기록)
- **결정자**: 원 개발팀 (복원)

## 맥락 (Context)

멀티모듈에서 의존성 버전을 어떻게 관리할지 정해야 했다. 루트 `build.gradle` 주석에 "버전 카탈로그
안 씀. 예전부터 굳어졌고 건드리면 깨질까봐 아무도 안 고침"이라고 남아 있다.

## 결정 (Decision)

- `gradle/libs.versions.toml`(버전 카탈로그)을 **쓰지 않는다**.
- Spring 생태계 의존성은 **Spring Boot BOM**(`io.spring.dependency-management`)이 버전을 관리하므로
  모듈에서 **버전 문자열 없이** 선언한다(`spring-boot-starter-web` 등).
- BOM이 다루지 않는 의존성만 모듈 `build.gradle` 에 **버전을 직접 박는다**(`commons-lang3:3.12.0`).

## 대안 (Alternatives)

- **버전 카탈로그 도입**: 버전을 한 곳(TOML)에 모아 가독성/일관성이 좋아진다. 그러나 기존 구조를
  바꾸는 리스크와 "굳어진 관행"에 대한 보수성으로 채택하지 않았다.

## 결과 (Consequences)

- (+) Boot BOM 덕에 Spring 의존성 버전은 사실상 한 곳(Boot 버전)에서 통제된다.
- (−) BOM 밖 의존성 버전이 모듈마다 흩어진다. `commons-lang3:3.12.0` 처럼 오래된 버전이
  방치되기 쉽다([known-issues.md](../known-issues.md) C3).
- 새 BOM 외 의존성을 추가할 때는 버전을 명시하고, 같은 의존성이 여러 모듈에 퍼질 경우 카탈로그
  도입을 재검토한다.
