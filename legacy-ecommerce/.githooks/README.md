# Git 훅 (.githooks)

이 디렉터리는 버전관리되는 git 훅을 담는다. `.git/hooks/` 는 커밋되지 않으므로, 추적되는 이
디렉터리를 `core.hooksPath` 로 연결해서 쓴다.

> 참고: 이 저장소의 git 루트는 상위 `agentic-practice` 다(`legacy-ecommerce` 는 하위 디렉터리).
> 따라서 훅은 저장소 전체(main 브랜치 등)에 적용된다.

## 1회 설정 (클론마다 한 번)

저장소 루트에서:

```bash
git config core.hooksPath legacy-ecommerce/.githooks
```

Windows PowerShell 에서도 동일:

```powershell
git config core.hooksPath legacy-ecommerce/.githooks
```

설정 확인: `git config --get core.hooksPath` → `legacy-ecommerce/.githooks`.

해제하려면: `git config --unset core.hooksPath`.

## 포함된 훅

### `pre-push` — main 강제 푸쉬/삭제 차단 + 푸쉬 전 테스트

두 가지 가드레일을 수행한다.

1. **main 강제 푸쉬/삭제 차단** — `main` 에 대한 강제 푸쉬(non-fast-forward)와 삭제를 막는다.
   일반 fast-forward 푸쉬, main 신규 생성, feature 브랜치 force-push 는 허용한다.
2. **푸쉬 전 테스트** — 삭제가 아닌(=실제로 커밋을 올리는) 푸쉬가 하나라도 있으면 저장소 루트의
   `legacy-ecommerce` 에서 `./gradlew test` 를 돌리고, 실패하면 푸쉬를 중단한다(브랜치 무관).
   main 강제 푸쉬가 감지되면 테스트를 돌리기 전에 먼저 차단한다.

- 두 검사 모두 정말 의도한 우회는 `git push --no-verify` 로만 가능하다.
- 클라이언트 훅이므로 강제 푸쉬 차단은 **확정적 보증이 아니다**. 원격에서 원천 차단하려면
  GitHub 브랜치 보호를 함께 설정한다:
  - GitHub → Settings → Branches → `main` 보호 규칙 → "Allow force pushes" 해제, "Allow deletions" 해제.

## 메모

- 훅은 POSIX `sh` 스크립트다. Windows 에서는 Git for Windows 가 번들 `sh` 로 실행한다.
- `.gitattributes` 가 훅 파일을 `eol=lf` 로 고정해 CRLF 로 인한 실행 오류를 방지한다.
- 실행 권한은 `git update-index --chmod=+x legacy-ecommerce/.githooks/pre-push` 로 인덱스에 보존된다.
  `pre-push` 와 `gradlew` 둘 다 **index 모드 `100755`** 여야 한다 — git 은 **실행권한 없는 훅을 조용히
  무시**하고, 훅이 호출하는 `./gradlew` 도 실행권한이 없으면 깨진다(둘 다 100755 로 커밋됨).

## 문제 해결 (Troubleshooting)

- **훅이 아예 안 도는 것 같다(POSIX)** — (1) `git config --get core.hooksPath` 가 `legacy-ecommerce/.githooks`
  인지, (2) `git ls-files -s legacy-ecommerce/.githooks/pre-push legacy-ecommerce/gradlew` 가 둘 다
  `100755` 인지 확인한다. `100644` 면 git 이 훅을 건너뛴다 → `git update-index --chmod=+x <file>`.
- **`✋ 테스트 실패` 가 아니라 `Unable to locate a Java Runtime` 으로 막힌다** — 푸쉬하는 **환경에 Java 가
  안 잡힌 것**이다(테스트 자체 실패가 아님). 훅의 `./gradlew test` 는 `JAVA_HOME` 또는 PATH 의 `java` 로
  JDK 를 찾는다. push 를 도는 셸에 JDK 가 보이게 한다:
  - macOS/Homebrew 의 keg-only JDK(`openjdk@21`)는 기본 PATH 에 없다. **비대화형 zsh 는 `~/.zshenv` 만**
    읽으므로(`.zshrc`/`.zprofile` 아님), `~/.zshenv` 에 `export JAVA_HOME=...`/PATH 를 둔다. 또는
    `sudo ln -sfn $(brew --prefix openjdk@21)/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`
    로 시스템에 등록한다.
  - Windows 는 JDK 설치 시 보통 `JAVA_HOME`/PATH 가 잡혀 있어 추가 조치가 없다.
