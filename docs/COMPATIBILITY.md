# 适配与验证范围

## 平台

最低构建版本 Android 8（API 26），target 35；这不代表所有 Android 8–16 环境都已验证。实际 Shizuku 文件安装验证覆盖小米 15 / Android 16 和 Android 15 x86_64 模拟器。当前没有 root 真机安装验证，也没有所有渠道服实测。

包名识别为 `com.feimo.astralparty`、`com.feimo.jxpd` 及点分隔渠道后缀；严格模式必须同时匹配资源内容。日服不在范围内。带已知 `libcesium.so` 的改包拒绝叠加。

0.5.0 新增默认开启的“实验性强制兼容”。未匹配整份缓存时，支持解析无压缩/LZ4/LZ4HC UnityFS 和序列化版本 17–22；按内部运行程序集匹配，或使用大小最接近的 3.3.0 基准运行程序集重建缓存。后者可能损失渠道特有行为，不保证登录和玩法兼容。关闭开关继续使用严格资源匹配。好游快爆包名已识别，但本地没有该渠道的实际资源，尚未完成实测。未知内容不是只跳过校验后套用原差分。

## Mod 清单

以下 30 项都完成四份资源的直接类型/方法/字段引用检查，未发现缺失引用；动态反射、AOT 保留、界面布局和实际玩法仍需要设备验证。0.4.0 完整面板尚未真机确认。

| Mod | 功能 | 本项目适配 |
| --- | --- | --- |
| ActivityLogMod | 实时行为日志 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| AngelModeUnlockMod | 天使模式解锁 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| AudioSkinMod | 音效替换（需音效方案） | 路径适配；需要用户音效方案 |
| AutoThanksMod | 自动感谢 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| BattleCardPointsMod | 战斗卡牌点数 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| CardSkinMod | 卡牌皮肤（需皮肤素材） | 路径及旧图鉴布局兼容；需要皮肤素材 |
| CharacterSkinMod | 角色皮肤（需皮肤素材） | 路径适配；需要皮肤素材 |
| CharacterTransparencyMod | 角色半透明（触屏） | 采用 TenkinMori 安卓移植版，接入 API 开关 |
| ChatDurationMod | 消息停留时间 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| CombatOddsMod | 战斗胜率助手 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| ForkDirectionMod | 岔路口看牌 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| FreeCameraMod | 自由相机（双指缩放） | 双指纵滑/捏合缩放、UI 避让与镜头复位；横向平移不旋转 |
| HandViewerMod | 手牌查看 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| HideHudMod | 隐藏 HUD（触屏） | API 触屏显隐入口 |
| HomeBottomBarMod | 首页底栏展开 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| LanguageUnlockMod | 多语言支持 | 路径适配，导入随输入提供的语言数据 |
| MapPeekMod | 选人地图预览 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| PinnedRelicsMod | 关键筹码置顶 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| PvePanelMod | PVE 面板 | API 左栏/手牌/右栏入口 |
| PveWaveForecastMod | PVE 刷怪预报 | API 预报开关入口 |
| QuickChatPlusMod | 快捷短语扩展 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| ShopRelicPeekMod | 商店与筹码看板 | API 清空看板入口 |
| SpeedHackMod | 变速悬浮面板（Unity） | Unity 变速与 API 倍率控制；不使用原生时钟钩子 |
| StarCoinShopMod | 星币商店助手 | API 快捷购买入口 |
| TurnClockMod | 回合时钟 | 明确 Android 原生时钟固定为 1x，避免依赖 Windows UDP 状态 |
| TurnFocusMod | 回合提醒（游戏内） | 改为游戏内提醒，不支持后台置前 |
| UnlockFrameRateMod | 帧率解锁 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| NoBotMod | 不自动补人机 | 保留原 DLL 功能；静态检查通过，玩法待测 |
| AndroidControlsMod | 安卓触屏控制入口 | AndroidApi 状态、受支持配置总开关及触屏动作桥接 |
| CesiumOverlayMod | Cesium 安卓悬浮窗 | 头像入口、左侧模组与右侧动作，FairyGUI 宿主 |

`SpeedPerfProbe` 目录没有 DLL，未收录。其余成品 DLL 的导入与已知字节码修改由 `src/PortMods.cs` 完成；本项目没有宣称重新实现了全部上游 Mod。禁用 DLL 在 `mods/` 与 `disabled_mods/` 间移动，下次游戏启动生效。

## 资源签名

以下为原始资源 SHA-256，完整记录见 `profiles/supported.json`。

| 资源目录 | 原文件 SHA-256 |
| --- | --- |
| `0c6650c5e9eccffde61b8397d4694f72` | `77da946bf7d331351a93ff16707aab426f534b7577a771f21267478b7bc70c87` |
| `3c7700aa58877b365d45231b1225fcbb` | `01ee8b4332fdb024fd4c5b9bfdb38ce25f9d42e0b07941765bc7cd06efdaaaa4` |
| `7a5e751e708c075b7272acfd3dbe1249` | `2ea82144e02b301a07a236a42706611df82a83b85a92c1b1fdd0d7a1e93a91e2` |
| `aadfc2bcce57f6266d07f90b29bea89c` | `be299660bd53e61985ec578154ddd483c88022927ca940e3d30681f706218bb6` |

## 验证记录

- 安装/恢复/启停/升级禁用 DLL/中断恢复/未知缓存/原生改包冲突/路径边界/旧最小版迁移：原工作区 568 项资源事务断言通过。
- AndroidApi：13 项注册、去重、调用方、路径与回调断言通过。
- 触屏与倍率：25 项手势、倍率所有权、暂停及恢复断言通过。
- 帧泵：主线程加载与仅订阅两种路径均有 Update 和 LateUpdate 回调；重复初始化不重复调度。
- 0.3.0 手机日志证实控制模组已初始化，但帧泵没有启动；0.4.0 修复通过离线回归，真实面板仍待确认。
- root 仅检查协议及无 root 的退出路径，未有真实 su 授权后的安装证据。

请区分“能识别 / 能安装 / 入口执行 / 初始化 / 面板可见 / 操作生效 / 全部玩法正确”，这些是不同验证层级。
