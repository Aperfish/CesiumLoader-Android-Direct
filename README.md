# CesiumLoader Android Direct

吉星派对 Android 模组直装器。使用 **Shizuku 或 root**，对已匹配的游戏缓存安装加载补丁，保留官方游戏 APK 和签名。支持备份、恢复、模组启停和触屏操作。

**当前版本：0.4.0 测试版。** 本项目是独立适配项目，不是游戏官方或上游 CesiumLoader 的官方 Android 发布。

[下载测试版 APK](https://github.com/Aperfish/CesiumLoader-Android-Direct/releases/tag/v0.4.0) · [构建方法](docs/BUILD.md) · [适配范围](docs/COMPATIBILITY.md) · [实现原理](docs/ARCHITECTURE.md)

## 安装使用

1. 安装官方游戏并完成资源下载，随后完全退出游戏。
2. 安装 Release 中的工具 APK。
3. 无 root 时启动 Shizuku，并授权本工具；有 root 时选择 root 后端并在 su 管理器中授权。
4. 勾选模组，点击“一键安装”。工具检查资源签名、备份原版缓存、生成并校验差分结果，再复制模组。
5. 启动游戏，等待游戏就绪。点击左侧可拖动的头像入口，左栏选择 Mod，右栏操作；两栏均可翻页。

更新安装工具后，需要退出游戏并再次点击“一键安装”，才会更新游戏目录中的 DLL。仅覆盖安装工具 APK 不会更新游戏里的模组。

## 触屏功能

- **变速**：在悬浮窗选择变速模组，使用 1–10x 预设、加减和 1x/记忆切换。使用 Unity 时间倍率，系统时钟保持原速；每次游戏启动保持 1x。
- **自由相机**：双指横滑旋转，双指纵滑或捏合缩放；悬浮窗可恢复、拉近、拉远镜头。
- **其他入口**：HUD 显隐、角色半透明、刷怪预报、商店看板、快捷购买和 PVE 面板。
- **回合提醒**：游戏内文字提醒，不切换后台应用。

悬浮窗接入 [TenkinMori 的 AndroidApi](https://github.com/TenkinMori/CesiumLoader-Android-MOD)，保留头像、左侧模组与右侧功能结构；当前免改包方案使用 FairyGUI 作为窗口宿主，不需要系统悬浮窗权限。

## 已验证与限制

- 当前收录 3.3.0 的四份精确资源签名。未知资源、较新的未知缓存、运行中的游戏及已识别的原生加载器改包会被拒绝操作。
- 国服、可识别渠道包名受实际资源签名限制；未验证所有渠道，不含日服。

退出游戏后可在工具中保存启用项，或点击“恢复原版缓存”。恢复只针对工具已识别的缓存，保留配置、日志和备份。

## 源码与负载

仓库包含 Java 安装器、C# 安卓 SDK 适配、触屏 Mod、二进制适配工具和测试。**不提交游戏原始资源、个人日志、构建工具或签名私钥。**

原目录内 27 个 Mod 通过本地输入导入，另保留 NoBot，新增 AndroidControlsMod 和 CesiumOverlayMod，共 30 项。SpeedPerfProbe 没有 DLL，不计入。皮肤、音效替换仍需对应方案素材。

仅重建 Java 安装工具时，可从已发布 APK 提取经 SHA-256 校验的离线负载。重新生成 SDK、Mod 或游戏资源补丁则需要本地匹配的游戏资源、PC Mod 和 Bootstrap 输入。完整步骤见 [BUILD.md](docs/BUILD.md)。

## 许可与来源

本项目代码采用 [AGPL-3.0](LICENSE)。SDK、AndroidApi、窗口图标和部分第三方文件保留各自 MIT 许可。游戏资源、商标及单独导入的 Mod 不因本项目而被重新许可。完整来源与版本见 [THIRD_PARTY.md](THIRD_PARTY.md)。
