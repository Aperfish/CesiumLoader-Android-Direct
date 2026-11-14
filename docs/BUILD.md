# 构建

当前构建脚本针对 Windows。需要 Python 3.12、JDK 17 或更新版本及 .NET 8 runtime。

## 只重建安装工具

```powershell
python download-tools.py
python prepare-payload.py --apk "CesiumLoader-Android-ModPack-0.5.0.apk"
python build-apk.py
```

`prepare-payload.py` 默认接受 0.4.0 和 0.5.0 交付 APK 的已知 SHA-256。0.5.0 的摘要为：

```text
3e658b387cbf56e7d2ef0be472816fb2e0355059ff7c3b899d7029c0d8ec8f9d
```

脚本只读取发布包内的负载，验证文件摘要后写到 Git 忽略的 `android/assets/`。这条路径会重建 Java 界面和安装事务，**不会把修改后的 C# 重新编译进已有负载**。

没有原始资源样本时，普通构建明确跳过资源事务测试；`--test` 会失败，不能将跳过当作通过。

## 生成 0.5.0 渠道兼容负载

从 0.4.0 APK 提取负载后，按下节准备四份 `fixtures/original-*.bundle`，使用 Python 3.12 安装本项目的构建依赖，再执行：

```powershell
python prepare-channel.py
python build-apk.py --test
python tests/test-channel.py
```

`prepare-channel.py` 验证原始样本的 SHA-256，应用已有缓存差分，再提取补丁后的运行程序集作为设备端兼容基准。既有 SDK/API 和全部 30 项模组文件保持原字节；兼容运行程序集属于生成负载，不提交源码仓库。没有运行本步骤时，单独用 0.4.0 负载重建 Java 安装器不会获得强制兼容所需的运行程序集。

`tests/test-channel.py` 构造重新打包和程序集不同的渠道缓存，验证完整安装、重装、启停、中断恢复及原版恢复，并用 UnityPy 独立检查其余 TextAsset 内容不变。它是离线构造样本，不代表好游快爆实测。

仅更新 SDK 触屏逻辑时，可在已有完整负载和 `build/<profile>/refs` 引用程序集的基础上运行 `python rebuild-mobile.py`，再构建 APK。它重编译四份 SDK 和 AndroidControls，保留其他 Mod 字节。本版手势只支持双指纵滑和捏合缩放，横向平移不旋转。

## 重新生成 C#、Mod 与缓存补丁

准备自行取得的未修改游戏资源和本地 Mod 输入：

```text
fixtures/
  CesiumLoader-input.zip
  original-0c6650c5e9eccffde61b8397d4694f72.bundle
  original-3c7700aa58877b365d45231b1225fcbb.bundle
  original-7a5e751e708c075b7272acfd3dbe1249.bundle
  original-aadfc2bcce57f6266d07f90b29bea89c.bundle
  legacy-min.bundle             # 可选，旧最小版迁移测试样本
```

输入 ZIP 的现有布局包含 CesiumBootstrap.dll、SDK 及 CombatOddsMod、LanguageUnlockMod、MapPeekMod、NoBotMod。原始输入由构建者自行提供，不包含在 Git 中。

```powershell
python -m pip install --target python-libs -r requirements-build.txt
$env:CESIUM_PC_MODS = "D:\path\to\AstralParty_ModLoader\mods"
python build-profiles.py
python build-modpack.py
python prepare-channel.py
python build-apk.py --test
python tests/test-channel.py
```

`build-profiles.py` 提取各份原始缓存中的 Android 参考程序集，只改写 AstralParty.Runtime.dll，并校验其他 TextAsset 内容未变。`build-modpack.py` 编译当前安卓 SDK、API 和触屏模组，再对本地导入的 DLL 应用已知路径、界面布局和入口适配。缺少所需输入或编译失败会停止构建。

已收录资源摘要见 [profiles/supported.json](../profiles/supported.json)。新增资源版本还需要验证 HybridCLR、AOT 与实际功能，不能只修改资源目录名。

## 签名与输出

- 输出默认位于 `outputs/`，可用 `CESIUM_OUTPUT_DIR` 覆盖。
- `CESIUM_BUILD_TOOLS`、`CESIUM_PYTHON_LIBS` 可覆盖工具与 Python 依赖目录。
- `CESIUM_KEYSTORE` 可指定自己的开发签名文件；脚本默认密码为 `android`。未提供密钥时生成本地开发密钥。
- 发布 APK 的私钥不在仓库中。新密钥签名的安装工具不能覆盖旧签名工具；这不要求卸载游戏。

## 不依赖游戏样本的测试

```powershell
python tests/test-api.py
python tests/test-pump.py
python tests/test-pump.py --subscribe-only
python tests/test-mobile.py
```

这些测试分别覆盖 API 归属/去重/路径、主线程每帧回调、双指手势状态、倍率所有权、暂停和恢复。它们使用显式的 Unity/PlayerLoop 替身，不代表真机渲染验证。

## GitHub Actions 发布

`.github/workflows/release.yml` 使用 GitHub 托管的 Ubuntu runner 发布已经本地构建和签名的 APK，不在云端重建游戏资源，也不保存签名私钥。发布输入位于独立的 `release-input/v0.5.0` 分支；`releases/v0.5.0.json` 固定各分片 Git blob、大小、SHA-256 以及完整 APK 摘要。主分支只包含源码、摘要元数据和发布说明。

更新 main 上的发布元数据会触发任务，也可在 Actions 页面手动运行。任务重组并校验 APK，确认 0.5.0、30 项 Mod 和仅缩放相机标签，再创建草稿、上传 APK 与 `SHA256SUMS.txt`，验证上传摘要后发布为 Pre-release。已有同名资产只有摘要相同才会跳过，内容不同不会覆盖。任务只使用该次运行的 `GITHUB_TOKEN`，权限为 `contents: write`。
