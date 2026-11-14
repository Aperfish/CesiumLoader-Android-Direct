# 0.4.0 发布校验

独立源码目录完成完整负载生成与 APK 构建。完整重建负载内所有解压文件与发布 APK 一致；ZIP 时间戳造成容器摘要不同。使用发布负载重建 Java 后，classes.dex、AndroidManifest.xml、assets/payload.zip 和 assets/mod-list.json 与发布 APK 逐字节一致。签名封装和时间戳使整个重建 APK 的摘要不同。

- 四份缓存补丁目标 SHA-256 与发布配置一致。
- 安装事务：568 项断言通过，包括旧版迁移测试。
- API 注册与路径：13 项断言通过。
- 手势和倍率所有权：25 项断言通过。
- 主线程帧泵：主线程调用及仅订阅路径都通过，每帧恰好一次 Update/LateUpdate。
- APK v2/v3 签名验证通过。
- 0.4.0 的真实设备窗口与完整 root 安装仍未验证。

发布 APK SHA-256：`80edd88c27f3f3fc5ed7c65e98acca13ab0bddf58d9042767af3e2b2fe07f3c2`。
