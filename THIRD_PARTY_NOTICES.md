# 第三方说明与致谢

## Georgia-ReBORN：灵感来源

- 项目：<https://github.com/TT-ReBORN/Georgia-ReBORN>
- 参考内容：专辑封面与碟片共同展示的视觉及使用体验灵感。
- 许可：MIT；版权声明为 `Copyright (c) 2023 TT`
- Georgia-ReBORN 是 foobar2000 主题。本项目是在 SPW 中实现数字唱片展示的独立插件，不是其官方 SPW 移植版。
- 当前源码包不包含 Georgia-ReBORN 的图形资源或盘面模板。这里的灵感致谢与下述实际代码参考分别记录。

## SPW Animated Album Art：实现参考

- 项目：<https://github.com/Jacob7179/SPW-Animated-Album-Art>
- 参考内容：宿主原生节点遍历、隔离镜像层和倒影渐隐的实现方式。
- 许可：MIT；版权声明为 `Copyright (c) 2026 Jacob`。
- 保留的完整许可证：[SPW-Animated-Album-Art-MIT.txt](licenses/SPW-Animated-Album-Art-MIT.txt)。

参考插件自身的其他素材或功能不因此成为本项目依赖；本源码包不包含其视频或商业唱片素材。


## ReBORN-CHS-Mod：动画实现参考

- 项目：<https://github.com/tigerJX95/ReBORN-CHS-Mod>
- 参考内容： 渐进出碟、收碟完成回调和布局缓动方法。
- 本插件使用独立 Java 状态机实现，参考其平滑出碟和收碟节奏，封面缩放等方法。
- 保留提供包中原始贡献部分的 [MIT 许可声明](licenses/ReBORN-CHS-Mod-MIT.txt)。
- 不随包分发该主题的图片或播放器资源。


## SPW 与宿主依赖

SPW 插件 API、Compose 和 Skia 等由适配的宿主提供。宿主文件作为 `compileOnly` 依赖使用，不打入本插件，不随源码包分发。相关权利及许可归各自权利人，本项目的 MIT 许可证不替代它们的许可。

JDK、Gradle、Python 是外部构建工具，也不随本源码包分发。

## 音乐与图片

歌曲、专辑封面及用户提供的盘面图片归各自权利人所有，不纳入本项目 MIT 许可范围。自动生成盘面不会改变原封面的权利归属。本源码包不附带示例商业音乐或唱片图片。

## 本项目

本项目代码采用 [MIT License](LICENSE)，版权声明为 `Copyright (c) 2026 tigerJX95`。
分发代码或其中的重要部分时，请按 MIT 条款保留相应版权与许可声明，包括适用的第三方声明。
