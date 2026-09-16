# Third-party components and models

The project Apache-2.0 license covers original XiaoKong code and documentation. It does **not** relicense third-party libraries or model weights. Binary dependencies and models are fetched from upstream by `scripts/fetch-artifacts.py`; exact URLs, archive hashes and extracted-file hashes are in [config/artifacts.json](config/artifacts.json).

| Component | Upstream / version | Terms retained |
| --- | --- | --- |
| AndroidX / CameraX | [Android Jetpack](https://developer.android.com/jetpack/androidx), versions in `app/build.gradle.kts` | Apache-2.0 |
| MediaPipe Tasks Vision | [google-ai-edge/mediapipe](https://github.com/google-ai-edge/mediapipe/tree/v0.10.35), 0.10.35 | Apache-2.0 and upstream notices |
| Hand / Face Landmarker | Google MediaPipe float16 model bundles, version 1; upstream Google Storage URLs in artifact manifest | MediaPipe Apache-2.0; preserve upstream names and notices |
| sherpa-onnx Android AAR | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8), 1.13.8 | Apache-2.0 |
| ONNX Runtime in sherpa AAR | [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) | MIT; exact container pinned by AAR hash |
| Chinese keyword model | sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01-mobile | Apache-2.0 as stated in upstream [model README](app/src/main/assets/kws/README.md) |
| Silero VAD | [snakers4/silero-vad](https://github.com/snakers4/silero-vad), ONNX distributed by sherpa-onnx | MIT |
| SenseVoiceSmall | Alibaba Group, [FunAudioLLM/SenseVoiceSmall](https://huggingface.co/FunAudioLLM/SenseVoiceSmall); sherpa int8 conversion dated 2024-07-17 | **FunASR Model Open Source License Agreement v1.1**, separate from the Apache-2.0 code license |
| Pronunciation data | [pypinyin](https://github.com/mozillazg/python-pinyin), 0.55.0 | MIT; generated finite-command tables, no Python runtime in APK |
| Gradle Wrapper | [Gradle](https://github.com/gradle/gradle), 8.13 | Apache-2.0 |
| JUnit | [JUnit4](https://github.com/junit-team/junit4), 4.13.2 | EPL-1.0; test-only, not bundled in Release APK |

Full bundled notices are under [app/src/main/assets/licenses](app/src/main/assets/licenses). The in-app Privacy screen exposes the combined notice text. Maven/Gradle dependencies retain their published metadata and embedded notices.

## SenseVoice model boundary / 语音模型许可边界

SenseVoiceSmall weights are not licensed by this repository's root LICENSE. The conversion archive points to the FunASR model license. The complete v1.1 text is retained at [FUNASR-MODEL-LICENSE.txt](app/src/main/assets/licenses/FUNASR-MODEL-LICENSE.txt), matching the [upstream MODEL_LICENSE](https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE) checked for this release. It includes attribution and additional terms, so do not describe the complete APK as containing only Apache-2.0 assets. Read the upstream model terms before downloading, using, modifying or redistributing the weights. Names including SenseVoiceSmall and Alibaba Group are preserved in the APK notices.

代码开源不代表可以把第三方模型改成自己的许可证。下载脚本要求显式确认已阅读这些模型条款；社区 APK 包含模型和完整随包说明。自行发行或更换模型时，需重新核对实际使用版本的条款。

## Source and modifications

XiaoKong's camera orchestration, gesture/voice state machines, cursor, command routing, button selection, UI, local diagnostics and integration are project code. Custom keyword and pronunciation tables are generated for the finite XiaoKong command vocabulary. Upstream model binaries are not trained or claimed as authored by XiaoKong.

Third-party names and marks do not imply endorsement or affiliation. If an attribution is incomplete, please open an Issue with the upstream source and affected version.
