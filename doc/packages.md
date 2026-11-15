# 패키지별 기능

패키지 루트 `com.stai.pleos_red_light_voca_app`은 생략한다. 소스셋 표시가 없으면 `main`이다.

## 루트

| 파일 | 기능 |
|---|---|
| `VocaApplication` | `@HiltAndroidApp`. `onCreate`에서 로그 트리(`plantLogTree`)를 심고 `StartAppUseCase`로 학습 세션을 시작한다(진도만 복원, 학습 OFF). |

---

## core — 엔티티와 공통 경계 (순수 Kotlin)

### `core.common`

| 클래스 | 기능 |
|---|---|
| `LearningConfig` | 조정 가능한 상수 모음(정차 판정 속도·유지 시간, 단어와 뜻 사이 무음 `chapterSilenceMs=750`, 언어 태그). `init`에서 값 사이 관계를 검증한다. 상수 `LOG_TAG="VocaApp"`, `CATALOG_ASSET_PATH`, `CATALOG_SCHEMA_VERSION=2`. |
| `Logger` | 로그 출력 경계. domain·core는 이것만 본다. `Logger.NONE`은 테스트·기본값. |
| `MonotonicClock`, `Scheduler`, `Cancellable` | 시간 경계(`fun interface`). 앱·테스트에서 실제/가상 시간을 바꿔 끼운다. |
| `CoroutineScheduler` | `Scheduler`의 코루틴 구현(`launch { delay(); action() }`). |
| `metersPerSecondToKmh` | m/s(후진 시 음수) → km/h(절댓값). |

파일: `LearningConfig.kt`, `Logger.kt`, `Time.kt`(`MonotonicClock`·`Scheduler`·`Cancellable`·`CoroutineScheduler`), `Units.kt`(`metersPerSecondToKmh`).

### `core.entity`

| 클래스 | 기능 |
|---|---|
| `Catalog`, `Content`, `Chapter` | 커리큘럼 모델. 콘텐츠(하루 묶음) > 챕터(단어 1개: `word`, `partOfSpeech`, `meaningKo`). `Content`는 학습 언어 `languageId`(기본 `en`)와 데모용 기본 완료 표시 `initiallyCompleted`(기본 `false`)를 갖는다. |
| `Progress` | 진도와 학습 선택. 콘텐츠별 마지막 완료 챕터 ID, 마지막 활성 콘텐츠 ID, 등록한 언어 `enrolledLanguageIds`, 선택한 언어 `selectedLanguageId`, 언어별 활성 콘텐츠 `activeContentIdsByLanguage`를 저장한다(재생 위치 없음). |
| `Gear`, `VehicleSignal`, `SignalFailure`, `SignalBlock`, `VehicleEvent` | 차량 입력 모델. `VehicleEvent`는 `SpeedChanged`·`GearChanged`·`SignalFailed`(일시적)·`SignalBlocked`(권한 거부·미지원 같은 지속적 차단) sealed interface. |

---

## domain — 업무 규칙 (순수 Kotlin)

### `domain.gate`

| 클래스 | 기능 |
|---|---|
| `StopGate` | 정차 판정 상태 기계. 속도·기어 샘플을 받아 `GatePhase`를 바꾸고, 재생 중이면 멈춰야 할 이유(`HaltReason.MOVING`, `GEAR_NOT_DRIVE`)를 돌려준다. `consume()`으로 정차당 1회 재생을 잠근다. 학습 ON 직후 첫 정차는 주행 확인 없이 인정한다. `canResumeAtUsedStop`은 이미 쓴 정차가 아직 이어지는지(속도 0.5km/h 이하 유지, D단) 알려 주며, 사용자가 멈춘 단어를 같은 정차에서 **듣기**로 다시 재생할 때만 쓴다. |
| `GatePhase`, `HaltReason` | 판정 상태(8개)와 정지 이유. |

### `domain.learning`

| 클래스 | 기능 |
|---|---|
| `LearningController` | 학습 세션 상태 기계. 학습 ON/OFF, 콘텐츠·언어 선택, 차량 이벤트 처리, Play 판정(`PlayDecision`), 재생 티켓 발급·검증, 재생 경과 처리, 진도 저장(`Mutex`로 직렬화)을 맡는다. `StopGate`가 `ARMED`가 되면 `tryAutoPlay()`로 자동 재생을 요청한다. 정차 한 번에 단어 하나만 재생하고, 단어가 끝나면 세션 커서(`sessionChapterIndex`)만 다음 단어로 옮긴다. 마지막 단어 뒤에는 첫 단어로 돌아간다. 사용자가 멈춘(`USER_PAUSE`) 뒤에는 다음 정차에서도 자동 재생하지 않고 **듣기**를 기다린다. `restore()`는 `initiallyCompleted` 콘텐츠를 완료로 덧씌우고, 예전 단일 콘텐츠 진도를 언어별 선택으로 옮겨 저장한다(`migrateLearningSelections`). |
| `LearningState`, `VehicleSnapshot`, `DisabledReason` | UI·서비스가 구독하는 상태. 언어 선택(`hasExistingLearning`, `enrolledLanguageIds`, `selectedLanguageId`, `activeContentIdsByLanguage`)과 `canResumeAtUsedStop`을 포함한다. |
| `PlaybackPhase`, `PlaybackEnd`(`PauseReason`) | 재생 단계(`IDLE`/`PREPARING`/`PLAYING`)와 종료·중단 사유. |
| `PlayerControl` | 컨트롤러 → 플레이어 경계(`pause`, `canStartAutomatically`, `startAutomatically`). |
| `PlayTicket` | 재생 허가 표. 준비 중에 상황이 바뀌면 무효가 된다. |
| `PlayDecision`, `IgnoreReason`, `StartResult`, `PlaybackReport` | 판정·결과·보고 타입(sealed). |

파일: `LearningController.kt`, `LearningTypes.kt`(나머지 타입 전부).

### `domain.session`

| 클래스 | 기능 |
|---|---|
| `AppSession` | 프로세스 단위 학습 세션(Hilt `@Singleton`). 카탈로그 로드 → `LearningController` 생성·복원 → 차량 입력 수집 → 음원 확인을 하고, 학습 시작 전 음원 준비를 검사하며, 콘텐츠 음원을 미리 병합(prefetch)한다. 플레이어 부착·분리를 받는다. |
| `SessionState` | `Loading` / `Ready(catalog)` / `Failed`. |
| `AudioStatus` | 음원 확인 상태(`Checking` / `Checked(missing)` / `Unavailable`). |
| `StartOutcome` | 화면 '학습 시작' 결과. |
| `PlayerControlRelay` (internal) | 컨트롤러의 정지·자동 재생 요청을 지금 살아 있는 서비스 플레이어로 넘긴다. 플레이어가 없으면 로그만 남긴다. |

파일: `AppSession.kt`, `SessionTypes.kt`(`SessionState`·`AudioStatus`·`StartOutcome`).

### `domain.playback`

| 클래스 | 기능 |
|---|---|
| `PlaySource` | Play 명령 출처: `MEDIA_KEY`, `SELF`(debug 컨트롤러), `SYSTEM_AUTOPLAY`, `OTHER_CONTROLLER`, `APP_UI`(화면 '듣기'). |
| `PlaySourcePolicy`, `PlayGate` | 출처별 허용 규칙. 시스템 자동 재생은 항상 무시하고, 학습 OFF에서는 사용자 미디어 키만 학습을 켤 수 있으며, 음원이 없으면 학습을 켜지 않는다. |
| `PlayCommandResult` | `HandlePlayCommandUseCase` 결과(`Play` / `Forwarded` / `Ignored`). |

### `domain.repository`

| 인터페이스 | 기능 |
|---|---|
| `CatalogRepository` | `load(): CatalogResult` (`Success` / `Failure`). |
| `ProgressRepository` | 진도 읽기·완료 기록·초기화·완료 콘텐츠 재시작(원자적)·활성 콘텐츠 저장. `saveLearningSelection(languageId, contentId)`은 언어 등록·선택과 활성 콘텐츠를 한 번에 저장하고, `migrateLearningSelections(...)`는 예전 진도를 언어별 학습 정보로 옮긴다(둘 다 기본 구현이 있음). |

### `domain.source`

| 인터페이스 | 기능 |
|---|---|
| `VehicleStateSource` | 차량 입력 경계. cold `Flow<VehicleEvent>`: 수집 시작 시 연결·구독하고 현재값을 먼저 내보내며, 끊기면 끊긴 신호에 `DISCONNECTED`를 보낸다. |
| `VehicleStateSourceFactory` | 수집을 (다시) 시작할 때마다 새 소스를 만든다. |
| `ChapterAudioSource`, `AudioAvailability` | 챕터 음원 경계. 음원 확인, 챕터 WAV 1개 준비, 미리 병합. |
| `VehicleSimulator`, `SourceMode` (debug 소스셋) | 디버그 차량 시뮬레이터 인터페이스(REAL/FAKE 전환, 속도·기어·실패·차단 주입). |

### `domain.speech`

| 클래스 | 기능 |
|---|---|
| `ChapterScript`, `ChapterSegment` | 챕터 읽기 순서: 영어 → 무음 → 한국어 → 무음 → 영어. |
| `ChapterAudioResult` | `Ready(wavFile)` / `Failed`. |

### `domain.usecase`

모두 `AppSession`(또는 그 안의 `LearningController`)에 위임하는 얇은 클래스이고, `operator fun invoke`로 호출한다. `@Inject`가 없고 `di/UseCaseModule`이 만든다.

| 쓰는 곳 | UseCase |
|---|---|
| 앱 시작 | `StartAppUseCase` |
| 화면(ViewModel) | `ObserveSessionStateUseCase`, `ObserveLearningStateUseCase`, `ObserveAudioStatusUseCase`, `StartLearningUseCase`, `StopLearningUseCase`, `SelectContentUseCase`, `RestartVehicleUseCase` |
| 재생 서비스 | `AttachPlayerUseCase`, `DetachPlayerUseCase`, `GetLearningStateUseCase` (+ 위 Observe·Stop) |
| `GatedPlayer` | `HandlePlayCommandUseCase`(콜드 스타트 대기 → 출처 게이트 → Play 판정), `ReportPauseCommandUseCase`, `ReportStopCommandUseCase`, `ReportPlaybackUseCase`, `ValidatePlayTicketUseCase`, `PrepareChapterAudioUseCase` |

---

## data — 외부 세계 구현

### `data.vehicle`

| 클래스 | 기능 |
|---|---|
| `VehicleStateSourceImpl` | 실제 차량 입력. 속도·기어 두 Flow를 `merge`한다. |
| `CarSpeedDataSource` | AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`를 `callbackFlow`로 구독. 같은 값은 다시 보내지 않고, 권한이 없으면 `PERMISSION_DENIED`, 속성이 없으면 `UNSUPPORTED`. |
| `PleosGearDataSource` | Pleos Vehicle SDK 기어 리스너 + 주기 조회(연결 감시)와 재연결. 콜백은 메인 Looper로 모아 순서를 지킨다. |
| `GearLinkMonitor` | 기어 연결 상태 기계(`UNKNOWN`/`HEALTHY`/`FAILED`/`BLOCKED`). 시각을 인자로 받아 Android 없이 테스트한다. 실패는 진입할 때 한 번만 알리고, 오래된 조회 응답은 버리며, 재연결 시점을 정한다. |
| `VehicleMapping.kt` (최상위 함수) | 순수 매핑 함수(`mapGearName`, `classifyPleosFailure`, `isPleosConnectionError`, `mapSpeedStatus`): Pleos 기어 이름 → `Gear`, Pleos 오류 메시지 분류, Car 속성 상태 → 속도 이벤트. |

### `data.catalog`

| 클래스 | 기능 |
|---|---|
| `CatalogRepositoryImpl` | `CatalogRepository` 구현. |
| `CatalogLocalDataSource` | assets `content/catalog.json` 읽기. |
| `CatalogParser` | schemaVersion 2 파싱·검증. `languageId`(선택, 기본 `en`, 지원 `en`·`ja`)와 `initiallyCompleted`(선택, 불리언)도 읽는다. 예외 대신 `CatalogResult.Failure`를 돌려준다. |
| `json.MiniJson` | 의존성 없는 최소 JSON 파서(순수 Kotlin, 최대 깊이 200). |

### `data.audio`

| 클래스 | 기능 |
|---|---|
| `AssetChapterAudioSource` | `ChapterAudioSource` 구현. 단어 WAV(영어·한국어)를 `ChapterScript` 순서로 WAV 1개로 병합해 `cacheDir/chapter-audio`에 캐시한다. `manifest.tsv` 지문으로 catalog 문구와 음원이 맞는지 확인하고, 같은 파일을 동시에 병합하지 않는다(`inFlight`). |
| `AudioAssetPaths` | 음원 파일 규칙(`audio/<contentId>/<chapterId>.en.wav`·`.ko.wav`, `audio/manifest.tsv`), 지문(SHA-256) 계산. |
| `AudioAssets`, `AndroidAudioAssets` | 음원 읽기 경계와 APK assets 구현(테스트는 메모리 구현). |
| `ChapterAudioCacheKey` | 캐시 파일 이름 = 내용(음원 바이트·순서·무음·포맷 버전) 해시. 내용이 바뀌면 다른 파일이 된다. |
| `WavCodec`, `PcmAudio`, `AudioPiece`, `ChapterAudioAssembler` | 순수 Kotlin WAV 디코드(8/16/24/32비트 정수, 32비트 float)·채널 변환·리샘플·무음 생성·이어 붙이기·인코드. |

### `data.progress`

| 클래스 | 기능 |
|---|---|
| `ProgressRepositoryImpl` | DataStore Preferences(`learning_progress`) 기반 진도·학습 언어 저장. 키 `completed.<contentId>`, `activeContentId`, `enrolledLanguageIds`(문자열 집합), `selectedLanguageId`, `activeLanguage.<languageId>`. |

### `data.log`, `data.time`

| 클래스 | 기능 |
|---|---|
| `TimberLogger` | `core.common.Logger` → Timber. |
| `VocaLogTree` | 모든 로그를 태그 `VocaApp`으로 내보내는 Timber 트리. 긴 메시지는 나눠 남긴다. |
| `SystemMonotonicClock` | `SystemClock.elapsedRealtime()` 기반 `MonotonicClock`. |

### `data.vehicle.debug` (debug 소스셋)

| 클래스 | 기능 |
|---|---|
| `DebugVehicle` | 시뮬레이션 상태(모드 `StateFlow`, 가짜 소스). 프로세스당 하나. 기본 FAKE. |
| `FakeVehicleStateSource` | 주입한 속도·기어·실패·차단을 내보내는 가짜 입력. |
| `SwitchingVehicleStateSource` | 모드에 따라 실제/가짜 입력을 바꿔 끼운다. 바뀌면 새 소스의 현재값부터 다시 수집한다. |
| `VehicleSimulatorImpl` | `VehicleSimulator` 구현. |

---

## presentation — Android 진입점과 UI

### 루트 (`presentation`)

| 클래스 | 기능 |
|---|---|
| `MainActivity` | `@AndroidEntryPoint`. `LearningServiceConnection.start()`, Compose로 `LearningScreen`을 그린다. |
| `ApplicationScope` | 프로세스 단위 메인 스레드 `CoroutineScope` 한정자. presentation이 di를 보지 않도록 여기에 둔다. |

### `presentation.learning`

| 클래스 | 기능 |
|---|---|
| `LearningScreen` | 화면 진입 Composable. ViewModel 상태 구독, 권한 읽기·요청, `ON_RESUME` 권한 재확인, 일회성 이벤트 처리. |
| `LearningContent` | 화면 본문. 그리기만 한다. 학습 OFF에서는 `LearningPane`에 따라 홈·언어 선택(`LanguagePage`) → 커리큘럼(`CurriculumPage`) → 학습 준비(`ReadyPage`)를, 학습 ON에서는 단어 재생 화면(`PlaybackPage`)을 그린다. 정차가 아니면(속도 0.5km/h 초과, 학습 중 신호 오류) '주행 중'만 보이고 조작을 잠근다. 상단 **이전** 버튼과 시스템 뒤로 가기는 `GoBack`으로 보낸다. debug에서는 정차·학습 OFF일 때 아래로 스크롤해 `DebugPanel`을 볼 수 있다. |
| `CurriculumCarousel`, `CurriculumCardModel`, `CurriculumCardStatus` | 가로로 미는 커리큘럼 카드 목록. 화면 가운데와의 거리에 따라 카드가 1.00~1.06배로 커지고 파란색으로 강조된다. 카드 상태는 `COMPLETED`/`CURRENT`/`UPCOMING`/`UNRELEASED`(미공개). |
| `LearningViewModel` | `@HiltViewModel`. `LearningAction`을 받아 UseCase를 부르고 `StateFlow<LearningUiState>`와 `Flow<LearningEvent>`를 내보낸다. 화면 이동 기록(`navigationHistory`)을 두고 이전 화면으로 돌아간다. 권한 자동 요청은 처음 한 번만. |
| `LearningAction` | 사용자 의도(sealed): 기존 학습 열기(`OpenExistingLearning`)·언어 추가(`ClickAddLanguage`)·언어 선택(`SelectLanguage`)·커리큘럼 열기(`OpenCurriculum`)·이전(`GoBack`)·홈으로(`BackToHome`), 시작·종료·콘텐츠 선택·듣기·화면 열기·권한 응답·화면 복귀. |
| `LearningEvent` | 일회성 효과(sealed): 권한 요청, 듣기 명령 전송. |
| `LearningUiState`, `LearningPane`, `PermissionUiModel` | 화면 상태(data class). `LearningPane`은 학습 OFF일 때의 화면(`HOME`/`LANGUAGE_PICKER`/`CURRICULUM`/`READY`). |
| `LearningCard` | 상태 줄 문구·아이콘·버튼 활성을 정하는 순수 표시 모델(`LearningCard.of(state)`). 지금 화면(`LearningContent`)은 쓰지 않고 테스트(`LearningCardTest`)만 쓴다. |
| `ListenButton`, `ListenCommand` | '듣기/멈춤' 버튼 표시와 보낼 명령. 정차 확정(`ARMED`)이거나, 사용자가 멈춘 뒤 같은 정차가 이어질 때(`canResumeAtUsedStop`) '듣기'가 켜진다. 재생 허가는 하지 않는다. |

### `presentation.playback`

| 클래스 | 기능 |
|---|---|
| `VocaPlaybackService` | Media3 `MediaLibraryService`. ExoPlayer와 `GatedPlayer`, `MediaLibrarySession`을 만든다. 미디어 키(`onMediaButtonEvent`), 브라우즈 트리·검색, 재생 재개 요청을 처리한다. 수명: `onCreate` 때 `startService(self)`. `onTaskRemoved`에서 학습 ON이면 서비스를 유지해 화면을 닫아도 다음 정차를 기다리고, 학습 OFF일 때만 정지 → `stopSelf()`. `onDestroy`는 학습을 끄지 않는다. |
| `GatedPlayer` | `SimpleBasePlayer`. MediaSession에 노출하는 유일한 플레이어로, 실제 ExoPlayer를 감추고 모든 Play 명령을 출처 분류 → UseCase 판정에 통과시킨다. 플레이리스트는 항상 "현재 챕터 1개". `PlayerControl`도 구현해 세션의 정지·자동 재생 요청을 받는다. |
| `GatedPlayerUseCases`, `PlaybackServiceUseCases` | 플레이어·서비스가 쓰는 UseCase 묶음(`@Inject` 생성자). |
| `PlaySourceClassifier` | 컨트롤러 패키지 이름·연결 힌트로 `PlaySource`를 가려낸다(순수). |
| `PlaybackRules`, `FocusAction`, `StartTarget` | 포커스 상실 대응, 완료 판정, "처음부터 재생" 순서(순수 규칙). |
| `MediaIds`, `MediaNode` | 브라우즈 트리 ID(`root` → `content/<id>` → `chapter/<id>/<id>`), 검색, 페이지 나누기. |
| `MediaItems` | 브라우즈·표시용 `MediaItem` 생성(오디오북 분류). |
| `AppMediaController` | 화면 '듣기' 명령을 `APP_UI` 연결 힌트를 단 `MediaController`로 세션에 보낸다. |
| `LearningServiceConnection` | 학습 ON 동안 서비스에 바인딩을 유지해 정차 자동 재생이 가능하게 한다(`@Singleton`). |

### `presentation.theme`

| 클래스 | 기능 |
|---|---|
| `VocaColors`, `PleosredlightvocaappTheme` | 색 토큰(Pleos 다크 + 파랑)과 다크 고정 Material 3 테마. |
| `VocaType`, `Typography` | 글자 토큰. |

### `presentation.debug`

| 클래스 | 소스셋 | 기능 |
|---|---|---|
| `DebugPanel` | debug / release(빈 구현) | 시뮬레이터 조작·미디어 명령 패널. |
| `DebugMedia` | debug | debug 연결 힌트(`SELF`)를 단 `MediaController`로 명령 전송. |
| `DebugVehicleReceiver` | debug | adb 브로드캐스트(`…debug.VEHICLE`, `…debug.MEDIA`)로 시뮬레이터·미디어 명령 주입. |
| `HiltBroadcastReceiver` | debug | `@AndroidEntryPoint` 리시버의 바탕 클래스(Kotlin에서 추상 `onReceive`의 super 호출 문제 해결). |
| `VehicleSimulatorEntryPoint` | debug | Composable에서 Hilt 그래프의 `VehicleSimulator`를 꺼내는 `@EntryPoint`. |

---

## di — Hilt 조립

| 클래스 | 소스셋 | 기능 |
|---|---|---|
| `AppModule` | main | `LearningConfig`, `@ApplicationScope CoroutineScope`, `MonotonicClock`, `Logger`, `AppSession`을 `@Singleton`으로 제공. |
| `DataModule` | main | data 구현을 domain 인터페이스로 묶음(`CatalogRepository`, `ProgressRepository`, `ChapterAudioSource`). 실제 차량 소스 팩토리는 `@RealVehicleSource`로만 제공. |
| `UseCaseModule` | main | domain UseCase 생성(범위 없음). |
| `RealVehicleSource` | main | 실제 차량 소스 팩토리 한정자(di 안에서만). |
| `plantLogTree()` | main | Timber에 `VocaLogTree`를 심는다(`di/Logging.kt`). |
| `VariantModule` | debug / release | 앱이 쓰는 `VehicleStateSourceFactory`를 제공. debug는 시뮬레이션 전환을 끼우고 `DebugVehicle`·`VehicleSimulator`도 제공. |
