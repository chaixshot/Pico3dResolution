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
*   **⚡ GPU 性能档位控制**：手动设置 GPU 的 `max_pwrlevel` 和 `min_pwrlevel` 来锁定性能状态（从“极致性能”到“极致省电”）。
*   **📐 高级渲染控制**：
    *   **遮罩网格 (Stencil Mesh)**：通过防止渲染隐藏像素来优化 GPU 资源。
    *   **注视点渲染 (Foveated Rendering)**：通过集中中心区域分辨率来优化性能。
    *   **纹理视场角 (Texture FOV)**：精细调整渲染眼图的视场角。
*   **🔄 一键恢复默认**：专用的“恢复默认”按钮，可立即将所有设置恢复为安全的 Pico 标准值。
*   **🧩 开机自动应用**：集成 LSPosed (Xposed) 模块支持，在每次重启后自动重新应用 GPU 性能档位。
*   **❓ 交互式帮助**：每个设置都集成说明按钮，解释技术细节和性能权衡。
*   **📊 优化预设**：从针对性能或清晰度优化的预定义值中进行选择：
    *   **384px**: 超级性能
    *   **752px**: 性能
    *   **1504px**: 默认
    *   **2160px**: 原生分辨率
    *   **2448px**: Pico 特定
    *   **2816px**: 抗锯齿
*   **🔍 实时验证**：在重启前自动验证数据库更改是否成功。
*   **🛠️ 现代 UI**：响应式 Jetpack Compose 界面，针对 VR 横屏模式进行了双列布局优化。
*   **🌍 全面本地化**：支持 27 种语言，并采用了专业的行业术语。
*   **🔐 Root 安全**：使用 `content` 命令安全地修改系统的 PVR 配置。

## ⛏️ 前提条件

*   **Pico VR 头显**（Pico 4、Pico Neo 3 等）
*   **Root 权限**（需要 Magisk）
      *   建议使用 [picounlock](https://github.com/chaixshot/more-picohaxx)
*   **环境：** 必须安装并激活 **[LSPosed 框架](https://github.com/JingMatrix/Vector/releases/tag/v2.0)**。
*   **超级用户权限**：在应用程序提示时授予 Root 权限。

## 📖 如何使用？

1.  **下载并安装** 最新的 `Pico3dResolution.apk`。
2.  **打开应用程序** 并在请求时授予 **Root/超级用户** 权限。
3.  从下拉菜单中 **选择目标分辨率** 或 **GPU 性能档位**。
4.  **点击 "Apply"（应用）**。
      *   如果更改了分辨率/渲染设置，按钮将显示 **"Apply & Reboot"（应用并重启）**，随后头显将自动重启。
      *   如果仅更改了 **GPU 性能档位**，按钮将显示 **"Apply"**，更改将立即生效且无需重启。
5.  **(可选) 开机自动应用：** 要在重启后保持 GPU 性能档位生效，请在 **LSPosed Manager** 应用中启用本模块。
6.  **等待过程完成**。您的新设置现已生效！

## ⁉️ 常见问题 / 故障排除

*   **为什么要重启？**
    *   Pico VR 服务和合成器仅在启动时分配其内部渲染缓冲区。必须重启以强制系统从数据库读取新的 `sdk_eyebuffer` 数值。
*   **这会影响性能吗？**
    *   是的。更高的分辨率（例如 2160px 或 2816px）会显著增加 GPU 负载。某些游戏可能会出现掉帧或发热增加的情况。
*   **我该如何验证更改？**
    *   您可以使用 **PICO Metrics Tool** 在头显内实时查看活跃的 "EBW"（眼缓冲区宽度）和 "EBH"（眼缓冲区高度）。

## 🛠️ 技术细节

### 工作原理
*   **分辨率与渲染**：本应用使用 Root 权限修改系统的 PVR 配置数据库（`/data/user_de/0/com.pvr.configuration/databases/config.db`）。它针对 `RuleBean` 和 `ConfigBean` 表中的 `sdk_eyebuffer`、`sdk_enableFFRBySYS`、`sdk_stencilMeshStatus` 和 `sdk_EyeTextureFov` 等参数。
*   **GPU 性能**：通过修改内核 sysfs 节点 `/sys/class/kgsl/kgsl-3d0/max_pwrlevel` 和 `min_pwrlevel` 来强制指定 GPU 频率状态。
*   **开机恢复**：集成的 LSPosed 模块会挂载到 `system_server` 进程，在开机完成后通过 `ContentProvider` 查询触发应用的恢复服务。

## 💖 特别鸣谢

*   **[pico-resfix](https://github.com/picoxr/resfix)**：配置目标的灵感来源。
*   **[Jetpack Compose](https://developer.android.com/compose)**：现代 UI 工具包。
*   **[Material 3](https://m3.material.io/)**：设计系统组件。

## 🔗 项目链接

*   **GitHub**: [chaixshot/Pico3dResolution](https://github.com/chaixshot/Pico3dResolution)
*   **开发者**: [hamer](https://github.com/hamer)
