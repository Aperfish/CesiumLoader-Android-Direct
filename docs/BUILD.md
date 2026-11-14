# 构建

当前构建脚本针对 Windows。需要 Python 3.12、JDK 17 或更新版本及 .NET 8 runtime。

## 只重建安装工具

```powershell
python download-tools.py
python prepare-payload.py --apk "CesiumLoader-Android-ModPack-0.4.0.apk"
python build-apk.py
```

`prepare-payload.py` 默认要求 v0.4.0 APK 的精确 SHA-256：

```text
80edd88c27f3f3fc5ed7c65e98acca13ab0bddf58d9042767af3e2b2fe07f3c2
```

脚本只读取发布包内的负载，验证文件摘要后写到 Git 忽略的 `android/assets/`。这条路径会重建 Java 界面和安装事务，**不会把修改后的 C# 重新编译进已有负载**。

没有原始资源样本时，普通构建明确跳过资源事务测试；`--test` 会失败，不能将跳过当作通过。

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
python build-apk.py --test
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
