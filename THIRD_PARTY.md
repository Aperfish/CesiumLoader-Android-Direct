# 来源与许可

## CesiumLoader SDK

- 来源：https://github.com/higashitaniyume/CesiumLoader
- 基准提交：`7cbff6d2204da53e1cce9d52e2a1eb23273f6143`。
- SDK 子目录使用独立 MIT 许可，完整文本保留于 `src/SDK/LICENSE`。
- 本项目修改 Android 路径、输入、固定原生时钟状态、主线程调度，并新增触屏与布局兼容服务。

## 变速动画代码

`src/SpeedHackMod/UnitySpeedScale.cs`、`FixedStepBudget.cs`、`FairyUiTimeScaleBridge.cs` 和 `IndependentAnimationBridge.cs` 来自上述 CesiumLoader 提交的 SpeedHackMod。上游仓库根许可为 AGPL-3.0；本项目保留这些源码，并对项目代码采用 AGPL-3.0。安卓入口与 API 操作为本项目适配。

## AndroidApi、窗口与角色半透明

- 来源：https://github.com/TenkinMori/CesiumLoader-Android-MOD
- 适配时参考提交：`928554a7b36f0f7285e7542c1af5a96d4dda9b72`。
- MIT 文本：`licenses/TenkinMori-Android-MOD-MIT.txt`。
- 包含 AndroidApi、PngDecoder、角色半透明移植代码与窗口图标。
- API 改为游戏私有目录并补充动作去重、调用方归属；窗口结构改由 FairyGUI 承载，保留头像、模组列表和功能列表，并增加翻页。没有将上游 IMGUI 宿主声明为真机验证成功。

## 安装与程序集处理依赖

- Shizuku API 13.1.5：https://github.com/RikkaApps/Shizuku-API ，MIT 文本见 `licenses/Shizuku-API-MIT.txt`。
- Mono.Cecil 0.11.6：https://github.com/jbevain/cecil ，MIT 文本见 `licenses/Mono.Cecil-MIT.txt`。
- UnityPy 1.25.4、bsdiff4 1.4.3、Android/JDK/.NET 构建工具由构建脚本或构建者获取，不作为本项目自有代码重新许可。

## 本地输入与游戏资源

安装包中的成品 Mod 由本地 PC Mod 输入与用户提供的 Bootstrap ZIP 导入。本仓库不将这些 DLL 的作者身份归为本项目，也不重新授予其许可。原 CesiumLoader Mod 对应源码可在上述固定提交的 `mods/` 中查询；BattleCardPointsMod 与 PinnedRelicsMod 的作者项目分别为：

- https://github.com/Aperfish/BattleCardPointsMod
- https://github.com/Aperfish/PinnedRelicsMod

各模组仍遵守各自许可。构建者应保存与所导入二进制匹配的来源与许可；不应以静态 API 检查替代源码或许可确认。

游戏程序集、配置、语言数据、图片、声音、商标与其他游戏资源的权利归原权利人。根许可不覆盖这些游戏资源。仓库不提交原始 UnityFS 样本；Release 的离线负载包含针对已匹配资源的差分补丁与本次模组输入。
