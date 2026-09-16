# 构建与发布

## 环境

- JDK 17 或 21；Android SDK Platform 36、Build Tools 35.0.0、platform-tools。
- Python 3.10+，下载脚本只使用标准库。
- Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21，版本在 Wrapper 和构建文件中锁定。
- 将 `ANDROID_HOME` 指向 SDK，或让 Android Studio 生成被 Git 忽略的 `local.properties`。

```bash
sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"
python3 scripts/fetch-artifacts.py --accept-model-licenses
python3 scripts/fetch-artifacts.py --check
./gradlew -PtargetAbi=arm64-v8a :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

先阅读 [第三方许可](../../THIRD_PARTY_NOTICES.md)。脚本从 `config/artifacts.json` 固定来源下载到 `.cache/artifacts`，校验整个归档和每个提取文件；失败时退出，不接受不匹配文件。首次需要约 300 MB 上游下载及额外 Gradle/Android 依赖空间。构建不依赖维护者的本机路径、账号或 API Key。

下载失败时检查上游连通性后重新运行，完整缓存可复用。不要关闭 TLS 或移除校验来绕过错误。Android Studio 同步前同样需要下载 AAR；`assemble` 前需要全部模型，否则应用会缺少运行资源。

## 验证

```bash
python3 scripts/check-public-tree.py
python3 -m unittest discover -s scripts/tests
./gradlew -PtargetAbi=arm64-v8a :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

`verify-apk.py` 需要 SDK 中的 `aapt`，可传 `--aapt /path/to/aapt`。它检查 APK 中的模型、许可、权限和调试入口。它不验证真实识别效果。

`src/debug` 只包含开发验收页面；Release 排除这些入口。历史内部真人回放文件不公开，只有本机存在时才可通过 `-PwithPrivateFixtures=true` 加载 `src/privateTest`。普通贡献者和 CI 无需这些文件。`-PwithMicProbe=true` 可额外编译独立麦克风竞争测试模块，不能作为主 App 发布。

## 安装与签名

Debug APK 使用开发机自己的 debug 密钥，适合本机测试。Release 构建启用 R8 和资源裁剪，默认输出 unsigned APK；签名私钥不在仓库，也不交给公共 PR 工作流。

```bash
# 仅对你选定的测试设备执行，SERIAL 用 adb devices 返回的目标序列号替换。
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

维护者发布时先通过所有检查，用独立保管的发布密钥经 Android SDK `apksigner` 签名，并用 `apksigner verify --verbose --print-certs` 验证；同时运行 `zipalign -c -P 16 -v 4`、APK 检查脚本及安装冒烟检查。发布 APK、SHA256SUMS、签名证书指纹、验证说明和对应 Git 标签。新密钥不能覆盖已有不同签名的包，不要自动卸载用户数据。

## 发布核对

1. 在新的源码副本执行模型下载、JVM 测试、Lint、Release 构建。
2. 检查只包含公开源码；无日志、真人录制、私钥、内部文档和设备配置。
3. 更新版本、CHANGELOG、已知限制；验证签名和资源；记录实际安装/页面检查结果。
4. 推送代码，等待公开 CI 通过。创建带对应标签的 **pre-release**。
5. 从公开发行页核对资产名称、哈希和说明，再邀请参与。人工测试与长期功耗未完成时不要写“稳定版”。
