<div align="center">
<img src="Resource/icon.webp" width="128" height="128"/>

# Pico3DResolution

[English](README.md) | [中文](README_zh.md) | [Русский](README_ru.md)

### 🚀 用于全局调整 Pico VR 头显 3D 渲染分辨率的简单工具

</div>

## 👓 截图

<image src="Resource/screenshot.jpeg" width="400"/>

## 🌟 核心功能

* **🎯 全局分辨率缩放：** 更改所有 3D VR 应用程序的内部渲染分辨率。
* **⚡ GPU 性能档位控制：** 手动设置 GPU 的 `max_pwrlevel` 和 `min_pwrlevel` 来锁定性能状态（从极致性能到节能模式）。
* **📐 高级渲染控制：**
  * **Stencil Mesh：** 通过阻止渲染隐藏像素来优化 GPU 资源。
  * **Foveated Rendering（注视点渲染）：** 通过将分辨率集中在中心视野区域来优化性能。
  * **Texture FOV：** 微调渲染眼图的视场角。
* **🔄 一键恢复默认：** 专用的“Restore Default”按钮，可立即将所有设置重置为安全的 Pico 标准值。
* **🧩 开机自启保持：** 集成 LSPosed (Xposed) 模块支持，在每次重启后自动重新应用 GPU 性能档位。
* **❓ 交互式说明：** 每个设置均内置技术说明，解释技术细节与权衡。
* **📊 优化预设：** 提供针对性能或清晰度优化的预定义数值供选择：
  * **384px**：极致性能
  * **752px**：性能优先
  * **1504px**：系统默认
  * **2160px**：原生分辨率
  * **2448px**：Pico 专属
  * **2816px**：抗锯齿
* **🔍 实时验证：** 重启前自动验证数据库更改是否成功生效。
* **🛠️ 现代 UI：** 基于 Jetpack Compose 构建的响应式界面，专为 VR 横屏模式优化双列布局。
* **🌐 多语言支持：** 完整支持 27 种语言，并配备专业的行业技术术语。
* **🔐 Root 安全：** 使用 `content` 命令安全修改系统的 PVR 配置。

## ⛏️ 必备条件

* **设备：** Pico 4 头戴设备（支持海外版和中国版固件）。
* **超级用户：** 需要 **[Root 权限](https://github.com/chaixshot/more-picohaxx)** 以修改系统文件。
* **环境：** 必须安装并激活 **[LSPosed 框架](https://github.com/JingMatrix/Vector/releases/tag/v2.0)**。
* **权限：** 应用请求时请授予 Root 权限。
* **LSPosed 作用域：** 确保在 LSPosed 模块作用域中勾选了 `System Framework (android)`。

## 📐 如何使用？

1. **下载并安装** 最新的 `Pico3dResolution.apk`。
2. **打开应用** 并在系统提示时授予 **Root / 超级用户** 权限。
3. 在下拉菜单中 **选择目标分辨率** 和/或 **GPU 性能档位**。
4. **点击 "Apply"（应用）**。
    * 如果更改了分辨率或渲染设置，应用将显示 **"Apply & Reboot"（应用并重启）**，头显将自动重启。
    * 如果仅更改了 **GPU 性能档位**，应用将显示 **"Apply"**，更改将立即生效，无需重启。
5. **（可选）开机自动应用：** 若要在重启后保持 GPU 性能档位生效，请在 **LSPosed Manager** 应用中启用该模块。
6. **等待处理完成**。您的新设置现已生效！

## ⁉️ 常见问题 / 故障排查

* **为什么需要重启？**
  * Pico VR 服务与合成器仅在系统启动时分配内部渲染缓冲区。必须重启才能促使系统从数据库读取新的 `sdk_eyebuffer` 值。
* **这会影响性能吗？**
  * 会。更高的分辨率（如 2160px 或 2816px）会显著增加 GPU 负载。部分游戏可能会出现掉帧或发热增加的现象。
* **如何验证修改是否生效？**
  * 您可以使用 **PICO Metrics Tool** 在头显内实时查看当前生效的 "EBW"（Eye Buffer Width）和 "EBH"（Eye Buffer Height）。

## 🛠️ 技术细节

### 工作原理

* **分辨率与渲染：** 本应用使用 Root 权限修改系统 PVR 配置数据库（`/data/user_de/0/com.pvr.configuration/databases/config.db`）。主要修改 `RuleBean` 和 `ConfigBean` 表中的 `sdk_eyebuffer`、`sdk_enableFFRBySYS`、`sdk_stencilMeshStatus` 和 `sdk_EyeTextureFov` 等参数。
* **GPU 性能：** 通过修改内核 sysfs 节点 `/sys/class/kgsl/kgsl-3d0/max_pwrlevel` 和 `min_pwrlevel` 来锁定指定的 GPU 频率档位。
* **开机恢复：** 内置 LSPosed 模块挂载至 `system_server` 进程，在开机引导完成后立即通过 `ContentProvider` 查询触发应用的恢复服务。

## 🙏 特别鸣谢

* [Jetpack Compose](https://developer.android.com/compose) - 现代化 UI 工具包。
* [Material 3](https://m3.material.io/) - 设计系统组件。
