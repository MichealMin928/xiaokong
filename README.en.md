# XiaoKong 小空

**Offline Chinese voice and one-hand gesture control for Android.**

[中文](README.md) · [Preview downloads](https://github.com/MichealMin928/xiaokong/releases) · [Contribute](CONTRIBUTING.md) · [Report an issue](https://github.com/MichealMin928/xiaokong/issues/new/choose)

XiaoKong combines on-device speech recognition, front-camera hand tracking, an air mouse, and Android accessibility actions. We want to make phones easier to operate when touching the screen is inconvenient, together with users and contributors.

**0.6.1-rc2 is a community preview.** Recognition reliability, unintended actions, cross-device compatibility, and sustained power use remain open work. This is not a validated assistive device or a promise of universal app control.

## Features

- Chinese commands for scrolling, Back, Home, Recents, and gesture control.
- Numbered selection of actual accessible controls, rather than empty screen cells.
- One-hand scrolling and an index-finger cursor; two thumb approaches click the aimed target.
- Local diagnostics, fixed-command history, feedback export, and opt-in landmark training.
- Experimental camera-based gaze reading with calibration.

Android 8+; the preview APK targets ARM64 phones. The current app UI and speech vocabulary are Chinese. See the [Chinese command and gesture guide](docs/public/GETTING_STARTED.md).

## Build

Install JDK 17 or 21, Python 3.10+, Android SDK Platform 36 and Build Tools 35.0.0; configure `ANDROID_HOME` or `local.properties`.

```bash
git clone https://github.com/MichealMin928/xiaokong.git
cd xiaokong
# Read THIRD_PARTY_NOTICES.md before acknowledging model terms.
python3 scripts/fetch-artifacts.py --accept-model-licenses
python3 scripts/fetch-artifacts.py --check
./gradlew -PtargetAbi=arm64-v8a :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Models and the sherpa-onnx AAR are pinned to upstream URLs and SHA-256 checksums. Downloads happen during development, not in the app. No API key is required. See [build details](docs/public/BUILDING.md).

## Privacy and participation

The app has no Internet permission. It processes audio and camera frames in memory, without storing raw recordings. User-entered feedback, fixed-command history and explicitly collected hand landmarks stay on-device until the user exports them. Community links open an external browser and never attach local reports automatically. Private recordings and internal device evidence are excluded from public source.

Try the preview, submit a device report, improve documentation, or contribute a focused PR. Please report failures as well as successes. Start with [CONTRIBUTING.md](CONTRIBUTING.md), [the roadmap](docs/public/ROADMAP.md), and [good first issues](https://github.com/MichealMin928/xiaokong/labels/good%20first%20issue). Maintainer: [@MichealMin928](https://github.com/MichealMin928).

## License

Original project code and documentation: [Apache-2.0](LICENSE). Third-party libraries and models retain their own licenses. In particular, SenseVoiceSmall is covered by separate FunASR model terms; the project license does not relicense model weights. Read [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This project is not endorsed by the upstream organizations.
