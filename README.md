# 체력바 모드

Minecraft Java **1.20.1 Fabric** 클라이언트용 모드입니다. 플레이어의 이름표 위에 하트 또는 숫자로 체력을 표시합니다. AdyTech99의 [Health Indicators 2.5.7](https://modrinth.com/mod/health-indicators/version/2.5.7)을 바탕으로 만든 수정본입니다.

## 설치

1. Modrinth App에서 **Minecraft 1.20.1 · Fabric** 프로필을 엽니다. Fabric Loader **0.19.3**과 Java 17로 검증했습니다.
2. 프로필의 **콘텐츠 추가**에서 **Fabric API**와 **YetAnotherConfigLib (YACL)**의 1.20.1 Fabric 버전을 설치합니다. [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=1.20.1&l=fabric) · [YACL 3.6.6](https://modrinth.com/mod/yacl/version/3.6.6%2B1.20.1-fabric)
3. **[수정본 JAR 다운로드](https://github.com/simsim01210-blip/HealthIndicators-Visibility/releases/download/v2.5.7-visibility.2/HealthIndicators-2.5.7-1.20.1-visibility.2.jar)** → 프로필 **폴더 열기** → `mods` 폴더에 넣고 실행합니다.

기존 Health Indicators가 설치돼 있다면 게임을 종료하고 기존 JAR을 교체하세요. **Health Indicators JAR은 하나만** 넣으면 됩니다.

## 체력 표시를 숨기는 조건

- **대상** · 몹, 투명 상태나 투명화 효과가 있는 플레이어, 웅크린 플레이어, 관전자는 숨깁니다.
- **이름표와 화면 경계** · 이름표 텍스트가 실제 렌더링 경로에서 그려지고, 몸과 이름표가 화면 안에 들어올 때만 표시합니다. 이름표 숨김 설정과 HUD 숨김을 따릅니다.
- **벽과 모서리** · 내 캐릭터의 눈과 카메라 양쪽에서 시야를 확인합니다. 일반 블록, 용암 등 유체, 시야를 가리는 블록·아이템·텍스트 전시 엔티티도 검사합니다.
- **머리 위 공간** · 화면에 그려지는 위치와 현재 틱 위치에서 모두 머리 위에 2블록의 공간이 있어야 합니다. 이동 중 천장 경계도 검사합니다.
- **실명·어둠·거리** · 보는 사람에게 실명이나 어둠이 적용되거나 안개 범위를 벗어나면 숨깁니다. 카메라에서 64블록 이상 떨어져도 숨깁니다.
- **끄기 설정** · 전체 표시나 플레이어 표시를 끄면 필터 무시 키를 눌러도 표시하지 않습니다. 높이 오프셋은 20으로 고정합니다.

Minecraft 1.20.1 로컬 환경에서 이름표 취소, 화면 경계, 실명·어둠, 일반 벽·전시 벽·용암, 3인칭 모서리, 이동 중 천장 경계를 확인했습니다. 서버에서 허용하는 모드인지 여부는 서버 운영진의 기준을 따릅니다.

## 소스 빌드

일반 설치에는 빌드가 필요 없습니다. `src`에는 추가 가시성 코드, `tools`에는 원본 JAR에 수정 사항을 적용하는 코드가 있습니다. Windows용 빌드 스크립트는 PowerShell 7, JDK 17 이상, Modrinth의 1.20.1 Fabric 프로필과 라이브러리, Fabric Loom의 Yarn 1.20.1+build.10 intermediary 게임 JAR, Gradle의 ASM 9.8 캐시를 사용합니다.

원본 2.5.7의 **1.20.1 Fabric JAR**을 준비한 다음 실행합니다.

```powershell
./Build.ps1 -OriginalJar "C:/path/to/HealthIndicators-2.5.7-1.20.1.jar"
```

## 라이선스

이번 수정분은 **비상업적 사용 및 수정 없는 비상업적 재배포**를 허용합니다. 재배포 시 원저작자 표기, 라이선스 고지, 이 저장소 링크를 함께 유지하세요. 상업적 이용과 편집·개조에는 별도 허가가 필요합니다. [수정분 이용 조건](./LICENSE)

AdyTech99의 원본 코드와 자산에는 [원본 MIT 라이선스](./LICENSE-UPSTREAM-MIT)가 계속 적용됩니다.
