# 0.6.1-rc2 社区预览验证

这份说明区分自动检查、模拟器安装与真人体验。当前公开版本并未完成跨品牌真人验收。

## 本次公开源码验证

- 从 Git 的公开文件集合导出新的构建目录，不包含维护者的 `.tools`、内部证据、私有回放数据或旧构建产物。
- 从上游重新下载 6 组运行依赖；校验归档和提取文件 SHA-256。Gradle Wrapper JAR 与官方 8.13 校验值一致。
- JDK 21 / Android Platform 36 / Build Tools 35.0.0：150 项公共 JVM 测试通过，0 失败、0 错误、0 跳过。
- 4 项下载器测试通过，覆盖损坏文件不覆盖旧资源、路径越界、归档额外文件和符号链接。
- Debug / Release Lint：0 Error、0 Fatal；分别有 90 / 83 条 Warning，主要是文本国际化、KTX、依赖更新和 RTL/绘制建议，尚未全部清理。
- Release 构建启用 R8/资源裁剪。APK 校验了 9 个模型/词表文件和随包许可；没有联网权限、debug 标志或开发验收页面。
- ARM64 APK 使用独立发布密钥，签名 v2/v3 验证通过，ZIP 16 KB 页对齐检查通过。APK 哈希和证书指纹随 Release 提供。

## 可复查的范围

首页和语音指令截图来自全新 Android 15 / ARM64 / 16 KB 页模拟器上的签名 Release APK。模拟器安装和页面展示不等于 OPPO 或其他真实手机的自然声音、手部跟踪、点击准确率或温度验收。

公开 GitHub Actions 会独立运行源码检查、下载校验、JVM 测试、Lint 和 Release 构建；以对应提交的 Actions 结果为准。[查看构建](https://github.com/MichealMin928/xiaokong/actions/workflows/android.yml)。

## 尚需社区参与

短口令/唤醒、外放视频干扰、连续指令、鼠标卡顿与拇指点击、跨品牌后台策略，以及 30–60 分钟功耗对照。请按 [TESTING.md](TESTING.md) 提交可复现结果。不把历史内部样本、合成声音或系统动作回执当作公开真人准确率。
