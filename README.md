# 레드보카 (RedVoca)

레드보카는 신호 대기 중에 영어 단어를 들려주는 Pleos Connect 차량용 Android 앱입니다.

출발 전에 학습할 과정을 고르면, 정차할 때마다 화면을 누르지 않아도 단어가 재생됩니다.

## 영상

### 피칭 영상

https://github.com/user-attachments/assets/027c8331-0c9f-4972-95fb-d1f5e2990144

### 데모 영상

https://github.com/user-attachments/assets/c64eeda7-b5fa-4d93-9aaf-dc41dfae55e4

## 학습 방법

### 1. 출발 전에 과정을 고릅니다

처음에는 **학습할 언어를 선택하세요** 카드에서 영어를 고릅니다.

다음부터는 홈의 **기존 학습 → 영어**로 들어갑니다.

커리큘럼을 좌우로 밀면 가운데 카드가 점차 커지고 파란색으로 강조됩니다.

원하는 카드를 눌러 단어 수를 확인한 뒤 **학습 시작**을 누릅니다.

언어 선택, 커리큘럼, 학습 준비 화면에서는 왼쪽 위 **이전** 버튼으로 바로 앞 화면에 돌아갈 수 있습니다.

| 과정 | 이용 상태 |
| --- | --- |
| 영어 Day1 · 도로와 이동 | 기본 완료(10/10) · 첫 단어부터 복습 가능 |
| 영어 Day2 · 일상과 업무 | 학습 가능 · 단어 10개 |
| 영어 Day3~Day12 | 미공개 |
| 일본어 · 중국어 · 스페인어 | 미공개 |

### 2. 정차하면 단어를 듣습니다

정차가 확인되면 **영어 단어 → 한국어 뜻 → 영어 단어** 순서로 들려줍니다.

화면에는 단어와 뜻이 나타나고, 오른쪽 위의 `4/10`은 열 단어 중 네 번째라는 뜻입니다.

정차 한 번에 단어 하나만 들려줍니다. 같은 정차에서 계속 서 있어도 다음 단어로 넘어가지 않고, 다시 주행했다가 정차하면 다음 단어를 들려줍니다.

마지막 단어 뒤에는 같은 과정의 첫 단어로 돌아갑니다.

주행 중에는 **주행 중**만 표시되고 화면 조작은 잠깁니다.

### 3. 정차 중에 멈추거나 종료합니다

| 버튼 | 누르면 어떻게 되나요? |
| --- | --- |
| **멈춤** | 다음에 정차해도 일시정지 상태를 유지합니다. |
| **듣기** | 멈춰 둔 단어부터 다시 재생합니다. |
| **학습 종료** | 반복을 끝내고 커리큘럼으로 돌아갑니다. |

끝까지 들은 단어의 진도는 기기에 저장됩니다.

## Mac에서 데모 실행하기

공모전용 Mac에서는 저장소 옆 `demo-tools/` 폴더를 열고 아래 파일을 더블클릭하세요.

`demo-tools/`는 공모전용 Mac에만 있고 이 저장소에는 들어 있지 않습니다. 다른 환경에서는 아래 [개발 환경에서 빌드하기](#개발-환경에서-빌드하기)의 명령을 쓰세요.

| 할 일 | 실행할 파일 |
| --- | --- |
| 코드를 수정한 뒤 APK 빌드 | `00_앱_빌드.command` |
| 에뮬레이터에서 앱 열기 | `01_앱_실행.command` |
| 출발 재현 | `02_주행_30km.command` |
| 정차 재현 | `03_정차.command` |
| 에뮬레이터 닫기 | `07_에뮬레이터_종료.command` |

**시연 순서**

1. 앱에서 **영어 → Day2 → 학습 시작**을 누릅니다.
2. **출발** 파일을 실행하고 ‘주행 중’ 화면에서 3초 이상 기다립니다.
3. **정차** 파일을 실행하고 약 3초 뒤 단어와 뜻이 나타나는지 확인합니다.

**소리가 들리지 않을 때**

1. 학습을 시작했는지 확인합니다.
2. 화면에 **일시정지**가 보이면 정차 상태에서 **듣기**를 누릅니다.
3. 차량 미디어 볼륨과 Mac의 출력 장치·음소거 설정을 확인합니다.

음원 검사 결과와 단어별 재생 시작 기록은 `adb logcat -s VocaApp`에서 확인할 수 있습니다.

## 개발 환경에서 빌드하기

JDK 25, Android SDK Platform 37, Android SDK Platform Tools가 필요합니다.

차량용 에뮬레이터를 켜고 저장소 루트에서 실행하세요.

```sh
# 디버그 APK 빌드 및 단위 테스트
./gradlew :app:assembleDebug :app:testDebugUnitTest

# APK 설치 및 앱 실행
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start --user current -n com.stai.pleos_red_light_voca_app/.presentation.MainActivity
```

설치된 앱을 업데이트할 때는 이전 APK와 같은 디버그 서명 키를 사용하세요.

에뮬레이터에서 화면 계측 테스트를 실행하려면 아래 명령을 사용하세요.

```sh
./gradlew :app:connectedDebugAndroidTest
```

<details>
<summary>ADB로 주행·정차 신호 보내기</summary>

디버그 앱에서 학습을 시작한 뒤 아래 명령을 순서대로 실행하세요.

```sh
receiver=com.stai.pleos_red_light_voca_app/.presentation.debug.DebugVehicleReceiver
action=com.stai.pleos_red_light_voca_app.debug.VEHICLE

# D단, 30km/h 주행
adb shell am broadcast --user current -n "$receiver" -a "$action" --es source FAKE --es gear DRIVE --ef speedKmh 30
sleep 4

# D단, 0km/h 정차
adb shell am broadcast --user current -n "$receiver" -a "$action" --es source FAKE --es gear DRIVE --ef speedKmh 0
```

</details>

## 차량 신호와 재생 기준

기어는 Pleos Vehicle SDK에서, 속도는 AAOS의 `PERF_VEHICLE_SPEED`에서 읽습니다.

| 상황 | 판정 조건 | 앱 동작 |
| --- | --- | --- |
| 이미 정차한 상태에서 학습 시작 | 기어와 관계없이 0.5km/h 이하로 3초 유지 | 첫 단어 재생 |
| 주행 후 정차 | D단에서 8km/h 이상으로 3초 주행 후, D단에서 0.5km/h 이하로 3초 정차 | 학습 재개 |
| 서행 | 0.5km/h 초과 | 다음 단어 재생 보류 |
| 출발 | 3km/h 초과 | 재생 중인 단어 중단 |
| 차량 신호 오류 | 속도 또는 기어 신호를 읽을 수 없음 | 재생 중단 및 학습 위치 유지 |

차량 신호가 복구되고 정차 조건이 다시 충족되면 중단된 단어부터 재생합니다.

## 구현 자료

| 항목 | 구현 | 관련 파일 |
| --- | --- | --- |
| 앱 화면 | Kotlin · Jetpack Compose | [개발 문서](doc/README.md) |
| 음성 재생 | Media3 · 앱에 포함한 영어·한국어 WAV를 단어별로 조합 | [음원 목록](app/src/main/assets/audio/manifest.tsv) |
| 학습 콘텐츠와 진도 | JSON 카탈로그 · Jetpack DataStore | [콘텐츠 카탈로그](app/src/main/assets/content/catalog.json) |
| 차량 상태 판정 | 속도·기어·유지 시간으로 재생 여부 결정 | [판정 기준값](app/src/main/java/com/stai/pleos_red_light_voca_app/core/common/LearningConfig.kt) |
| 빌드 | Android Gradle Plugin · Gradle JVM | [앱 빌드 설정](app/build.gradle.kts) · [Gradle JVM 설정](gradle/gradle-daemon-jvm.properties) |