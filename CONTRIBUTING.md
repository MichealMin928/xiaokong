# 参与小空 / Contributing

欢迎报告问题、补充设备测试、改文档、改善无障碍体验或提交代码。中文和英文均可。先查看已有 Issues，避免重复；没有合适条目时使用模板新建。

## 不写代码也能参与

安装 Releases 中的社区预览版，按[测试流程](docs/public/TESTING.md)记录结果。请写清机型、Android 版本、App 版本、语音模式、环境和失败步骤。不要上传账号页面、通知、个人录音、设备序列号、完整系统日志或他人的动作记录。

如果愿意贡献动作样本，先开 Issue 讨论匿名格式、授权和用途；目前不接受直接提交原始音视频或个人采集数据。App 内导出不是自动提交，导出文件也需要人工检查。

## 开发流程

1. 阅读 [README](README.md)、[架构](docs/public/ARCHITECTURE.md)和[构建说明](docs/public/BUILDING.md)。
2. Fork 仓库，从 `main` 新建分支。较大行为变更先在 Issue 中说明目标和验收方法。
3. 每个 PR 解决一个清楚的问题。保留既有手势语义和语音词表；若需要改变，先讨论并同步文档。
4. 执行 `python3 scripts/check-public-tree.py`、`python3 -m unittest discover -s scripts/tests` 和 README 中的 Gradle 检查。
5. PR 写清触发条件、修改后行为、验证结果和未验证部分。仅修改文档不要求运行手机测试。

识别改动需覆盖误触与漏识别：连续上滑、两指朝上但整手下移、收手、丢手重入、拇指靠近导致指尖移动、重复口令及暂停/恢复。模型推理、命令匹配、系统动作完成、页面实际变化是四种不同证据，不要混用。

## 代码和权限边界

- 使用 Kotlin、Android 原生视图，遵循现有目录和格式。避免为局部修复大规模重排代码。
- 不引入账户、遥测、云端转写或自动上传；新增网络或敏感权限必须先讨论。
- 不随 PR 提交密钥、安装包、模型权重、本机 SDK、采集记录或内部测试截图。
- 模型/依赖变更需更新来源、版本、完整许可、校验值和验证说明。
- CI 使用公开的通用回归测试；私人录制回放不作为社区构建的隐含前提。

## 审核与协作

维护者 [@MichealMin928](https://github.com/MichealMin928) 负责初期合并和版本发布。公共沟通使用 Issues 和 PR，不承诺固定响应时限。请遵守[社区准则](CODE_OF_CONDUCT.md)。未复现的问题保持待核实，不关闭为“已修复”。

提交贡献表示你有权按项目 Apache-2.0 许可提交这些内容；不改变第三方材料的原有许可。若使用 AI 辅助，请自行审阅、验证，并在 PR 中说明与评估相关的部分。无需提供私人对话记录。

## English quick guide

Issues and PRs in English are welcome. Fork `main`, keep changes focused, follow the build instructions, and describe validation and remaining uncertainty. Do not contribute private recordings, device identifiers, keys, or model binaries. Discuss recognition changes and new permissions first. Contributions are under Apache-2.0; third-party terms remain unchanged.
