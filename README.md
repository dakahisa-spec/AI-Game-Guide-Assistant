# AI 게임 공략 비서 v0.3

Galaxy Z Fold 계열을 우선으로 설계한 로컬 우선 Android 게임 공략 앱입니다. 게임별 진행도와 대화를 Room에 저장하고, 최대 5장의 스크린샷과 질문을 선택한 AI Provider에 전달해 맞춤 공략을 생성합니다.

## v0.1 구현 내용

- 게임 등록, 플랫폼·플레이 시간·챕터·지역·메인 퀘스트·진행률 관리
- 게임별 스포일러 3단계와 플레이 스타일 저장
- 게임별 대화·진행 메모리·웹 출처·스크린샷 경로 Room 저장
- 갤러리/최근 스크린샷 또는 카메라로 사진 최대 5장 첨부
- 이미지가 있거나 최신성이 필요한 질문이면 OpenAI `web_search` 자동 사용
- 힌트 1/2/3 및 정답 보기 단계
- AI가 높은 확신도로 감지한 진행 정보만 자동 갱신
- Fold 펼친 화면은 게임 목록 + 채팅 2열, 접은 화면은 자연스러운 1열 탐색
- 첨부 시안을 바탕으로 새로 제작한 블루·퍼플 3D 앱 아이콘과 라이트 디자인 시스템
- API 키를 Android Keystore 기반 AES-GCM으로 암호화 저장
- 인터넷이 없어도 게임 목록, 진행도, 이전 공략, 메모 조회 가능

## v0.2 AI 모델 선택

- OpenAI, Google Gemini, Anthropic Claude, OpenAI-compatible Provider 계층
- 자동/빠름/균형/고성능 Capability 기반 라우팅과 자동 Fallback
- 질문별 임시 모델, 게임별 기본 모델, 앱 전체 기본 모델 우선순위
- 이미지·다중 이미지·웹 검색 지원 여부를 호출 전에 검사
- 절약/균형/최고 품질 모드, 즐겨찾기, 최근 사용 모델
- Provider API Key Keystore 암호화, Base URL·Model ID·연결 테스트·목록 동기화
- 로컬 DB로 답할 수 있는 진행률 질문은 AI API를 호출하지 않음
- Room v1→v2 명시적 Migration으로 기존 게임·진행도·공략 기록 보존

## v0.3 Galaxy Z Fold8 Adaptive UI

- Material 3 WindowSizeClass와 Jetpack WindowManager `FoldingFeature` 기반 화면 전환
- Fold8 펼친 화면은 게임 목록 36~40% + 공략 채팅 60~64% List-Detail 구성
- 분리 힌지가 있는 기기에서는 힌지 폭을 콘텐츠 간격으로 반영
- 접은 화면은 게임 목록과 채팅이 자연스럽게 전환되는 1열 UI
- 대화 패널 읽기 너비 제한, 펼친 화면 진행률·남은 시간·현재 진행 요약 카드
- 펼친 화면 AI 모델 선택은 Provider/모델 2열, 접은 화면은 1열 Bottom Sheet
- 선택 게임·첨부 이미지·임시 AI 모델·웹 검색 상태를 `SavedStateHandle`로 보존
- 화면 변경 후에도 질문 입력과 게임별 채팅 스크롤 위치 유지

## 실행

1. Android Studio에서 프로젝트 루트를 엽니다.
2. JDK 17과 Android SDK 35를 설치합니다.
3. Gradle 동기화 후 `app`을 실행합니다.
4. 앱 오른쪽 위 설정에서 사용할 Provider의 API 키와 기본 모델을 저장합니다.

API 키는 소스나 `local.properties`에 넣지 않습니다. 기본값은 `자동`이며 모델 카탈로그는 한 곳에서 관리하고, 지원 Provider는 API에서 모델 목록을 동기화할 수 있습니다.

## 구조

```text
app/src/main/java/com/aigameguide/app
├── data/db          Room Entity, DAO, Database
├── data/ai          Provider, Gateway, Capability, 자동 모델 선택
├── data/model       플랫폼, 스포일러, 요청/응답 모델
├── data/repository  로컬 저장과 AI 흐름 조합
├── data/security    Keystore API 키 보관
├── ui               Fold/폰 Compose UI
├── ui/theme         블루·퍼플 Material 3 디자인
└── viewmodel        화면 상태와 사용자 동작
```

## 보안·개인정보

- 원본 스크린샷은 앱 내부 저장소에 복사하고 DB에는 경로만 저장합니다.
- AI 질문 시 현재 질문에 필요한 진행 정보와 선택한 이미지만 외부 API에 전송합니다.
- 앱 삭제 시 내부 DB, 스크린샷, 암호화된 API 키가 함께 제거됩니다.
- 다중 사용자 배포 버전에서는 개인 API 키를 앱에 보관하는 대신 별도 인증 백엔드와 단기 토큰을 사용하는 것을 권장합니다.

## 빌드

```bash
./gradlew assembleDebug
```

생성 APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions의 **Android debug APK** 워크플로도 포함되어 있으며, 실행하면 APK를 artifact로 올립니다.
