# 安装与运行原理

```text
安装工具 APK
  root: su -> app_process -> RootEntry
  Shizuku: UserService -> PrivilegedService
                    |
              InstallerEngine
  包名/当前用户/运行状态/改包冲突检查
  按缓存 SHA-256 选择精确配置
  校验差分 -> 备份 -> 记录 pending -> 写入 -> 校验
  复制 SDK/API/Mod -> 保存启用项
                    |
游戏 LoginSceneController.Awake
  BootstrapBridge -> Android Bootstrap
  先载入 CesiumAndroidApi，再载入 SDK 与 Mod
  AndroidControls/Speed/Overlay -> AndroidApi 注册动作
  主线程 Update/PostLateUpdate -> 触屏窗口与游戏逻辑
```

安装器不重签游戏 APK。补丁目标是已下载 UnityFS 缓存内的 AstralParty.Runtime.dll，在登录场景 Awake 插入一次性引导。SDK、API、Mod、配置和备份位于当前游戏用户空间的 `files/CesiumLoader/`，不要求游戏访问公共 `/sdcard/CesiumLoader`。

Shizuku 与 root 共用 Java 安装事务。root 必须得到 uid 0 确认；Shizuku 服务以获授权的 shell 身份操作。权限只在安装、配置和恢复时使用，Mod 运行不要求 Shizuku 持续在线。

## 0.5.0 实验性渠道缓存重建

严格模式仍按四份已知缓存摘要选择差分。实验开关开启时，对未匹配缓存使用纯 Java `UnityBundle` 解析 UnityFS 块表、LZ4/LZ4HC、序列化类型表及对象表，定位名为 `AstralParty.Runtime.dll` 的 TextAsset。内部运行程序集 SHA-256 命中时选择对应基准；未命中时按程序集字节数的差距选择一个 3.3.0 基准，替换为内置的已打补丁版本。这不是未知程序集的等价 IL 改写，可能改变渠道代码行为。

编辑器只替换运行程序集对象，保留其他对象原始内容；重算对象偏移、长度及 UnityFS 节点，输出未压缩的 UnityFS 块。支持资源序列化版本 17–22，不支持加密或 LZMA 缓存。Java 实现无平台原生库依赖，但这不表示任意 ABI 的游戏代码都兼容。

本渠道原版缓存按自身 SHA-256 备份。`backups/<original-sha256>/channel.json` 记录渠道包名、缓存相对路径、基准 ID、原始/生成摘要和匹配方式。后续检测、重装、配置、中断恢复及恢复均使用此记录，关闭实验开关也能管理已安装的渠道缓存。恢复的目标是本渠道原始缓存，而非基准游戏的缓存。

目录允许替换时使用临时文件与 rename；已有缓存可写但目录不允许新建时，在备份和 pending 记录校验后覆盖已有文件。中断恢复只处理已识别文件或可证明属于此次写入的部分内容；遇到游戏更新或未知内容会拒绝覆盖。

## 主线程回归

旧桌面入口从 boot 线程进入，跨线程 Send 间接启动帧泵。Android 从 Awake 主线程进入，旧 `MainThread.Run` 内联执行，订阅成功但没有帧泵，因此 `OnInitialize` 成功而面板 `OnUpdate` 永远不执行。

修复将帧泵启动加入 `MainThread.Run` 和 `UpdateService.Subscribe` 的契约。回归测试原结果：

```text
initialized=True pump=False panelUpdateReached=False lateTicks=0
```

修复后：

```text
initialized=True pump=True panelUpdateReached=True lateTicks=3
```

重复 Initialize 不会重复注册循环。该测试覆盖调度路径，实际窗口与触控仍需要设备验证。
