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
