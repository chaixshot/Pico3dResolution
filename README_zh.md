<div align="center">
<img src="Resource/icon.webp" width="128" height="128"/>

# Pico3dResolution

[English](README.md) | [中文](README_zh.md) | [Русский](README_ru.md)

### 🚀 用于全局调整 Pico VR 头显 3D 渲染分辨率的简单工具。

</div>

## 👓 屏幕截图

![Screenshot](./Resource/screenshot.jpeg)

## 🌟 主要功能

*   **🎯 全局分辨率缩放**：更改所有 3D VR 应用程序的内部渲染分辨率。
*   **📊 优化预设**：从针对性能或清晰度优化的预定义值中进行选择：
    *   **384px**: 超级性能
    *   **752px**: 性能
    *   **1504px**: 默认
    *   **2160px**: 原生分辨率
    *   **2448px**: Pico 特定
    *   **2816px**: 抗锯齿
*   **🔍 实时验证**：在重启前自动验证数据库更改是否成功。
*   **🛠️ PICO uUI 风格**：使用 Jetpack Compose 和 Material 3 构建的原生外观界面。
*   **🌍 多语言支持**：支持 27 种语言，包括英语、中文、俄语、泰语等。
*   **🔐 Root 安全**：使用 `content` 命令安全地修改系统的 PVR 配置。

## ⛏️ 前提条件

*   **Pico VR 头显**（Pico 4、Pico Neo 3 等）
*   **Root 权限**（需要 Magisk）
*   **超级用户权限**：在应用程序提示时授予 Root 权限。

## 📖 如何使用？

1.  **下载并安装** 最新的 `Pico3dResolution.apk`。
2.  **打开应用程序** 并在请求时授予 **Root/超级用户** 权限。
3.  从下拉菜单中 **选择目标分辨率**。
4.  **点击 "Apply & Reboot"（应用并重启）**。
5.  应用程序将更新系统数据库并验证数值。
6.  **等待头显重启**。您的新分辨率现在已生效！

## ⁉️ 常见问题 / 故障排除

*   **为什么要重启？**
    *   Pico VR 服务和合成器仅在启动时分配其内部渲染缓冲区。必须重启以强制系统从数据库读取新的 `sdk_eyebuffer` 数值。
*   **这会影响性能吗？**
    *   是的。更高的分辨率（例如 2160px 或 2816px）会显著增加 GPU 负载。某些游戏可能会出现掉帧或发热增加的情况。
*   **我该如何验证更改？**
    *   您可以使用 **PICO Metrics Tool** 在头显内实时查看活跃的 "EBW"（眼缓冲区宽度）和 "EBH"（眼缓冲区高度）。

## 🛠️ 技术细节

### 工作原理
此应用程序通过使用 Root 权限修改系统的 PVR 配置数据库（`/data/user_de/0/com.pvr.configuration/databases/config.db`）。它使用 Android `content` 命令 API 专门针对 `ConfigBean` 和 `RuleBean` 表中的 `sdk_eyebuffer` 参数。

## 💖 特别鸣谢

*   **[pico-resfix](https://github.com/picoxr/resfix)**：配置目标的灵感来源。
*   **[Jetpack Compose](https://developer.android.com/compose)**：现代 UI 工具包。
*   **[Material 3](https://m3.material.io/)**：设计系统组件。

## 🔗 项目链接

*   **GitHub**: [chaixshot/Pico3dResolution](https://github.com/chaixshot/Pico3dResolution)
*   **开发者**: [hamer](https://github.com/hamer)
