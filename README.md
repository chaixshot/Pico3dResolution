<div align="center">
<img src="Resource/icon.webp" width="128" height="128"/>

# Pico3DResolution

[English](README.md) | [中文](README_zh.md) | [Русский](README_ru.md)

### 🚀 A simple tool to globally adjust 3D rendering resolution for Pico VR headsets.

</div>

## 👓 Screenshot

![Screenshot](./Resource/screenshot.jpeg)

## 🌟 Key Features

*   **🎯 Global Resolution Scaling**: Change the internal rendering resolution for all 3D VR applications.
*   **📐 Advanced Rendering Controls**:
    *   **Stencil Mesh**: Optimize GPU resources by preventing rendering of hidden pixels.
    *   **Foveated Rendering**: Optimize performance by concentrating resolution in the central focus area.
    *   **Texture FOV**: Fine-tune the field of view for rendered eye textures.
*   **❓ Interactive Help**: Integrated documentation for each setting to explain technical details and trade-offs.
*   **📊 Optimized Presets**: Choose from predefined values optimized for performance or clarity:
    *   **384px**: Ultra Performance
    *   **752px**: Performance
    *   **1504px**: Default
    *   **2160px**: Native Resolution
    *   **2448px**: Pico Specific
    *   **2816px**: Anti-aliasing
*   **🔍 Real-time Verification**: Automatically verifies if the database changes were successful before rebooting.
*   **🛠️ Modern UI**: Responsive Jetpack Compose interface, optimized for VR landscape mode with a dual-column layout.
*   **🌍 Multi-language Support**: Full localization for 27 languages with technical industry terminology.
*   **🔐 Root Safety**: Uses `content` commands to safely modify the system's PVR configuration.

## ⛏️ Prerequisites

*   **Device:** Pico 4 Headset (Phoenix/China firmware supported).
*   **Permissions:** **[Root Access](https://pico4.wiki/guides/root/01-root/)** is required to apply changes to system files.
      *   Recommend using [picounlock](https://github.com/chaixshot/more-picohaxx)
*   **Superuser Permission**: Grant root access when prompted by the app.

## 📖 How to use?

1.  **Download and Install** the latest `Pico3dResolution.apk`.
2.  **Open the app** and grant **Root/Superuser** permissions when requested.
3.  **Select your target resolution** from the dropdown menu.
4.  **Click "Apply & Reboot"**.
5.  The app will update the system database and verify the values.
6.  **Wait for the headset to reboot**. Your new resolution is now active!

## ⁉️ FAQ / Troubleshooting

*   **Why does it need a reboot?**
    *   The Pico VR service and compositor only allocate their internal rendering buffers at startup. A reboot is necessary to force the system to read the new `sdk_eyebuffer` values from the database.
*   **Will this impact performance?**
    *   Yes. Higher resolutions (e.g., 2160px or 2816px) significantly increase GPU load. Some games may experience frame drops or increased heat.
*   **How do I verify the change?**
    *   You can use the **PICO Metrics Tool** to see the active "EBW" (Eye Buffer Width) and "EBH" (Eye Buffer Height) in real-time inside the headset.

## 🛠️ Technical Details

### How it works
This app works by using root access to modify the system's PVR configuration database (`/data/user_de/0/com.pvr.configuration/databases/config.db`). It targets parameters like `sdk_eyebuffer`, `sdk_enableFFRBySYS`, `sdk_stencilMeshStatus`, and `sdk_EyeTextureFov` in the `RuleBean` table.

## 💖 Special Thanks

*   **[Jetpack Compose](https://developer.android.com/compose)**: Modern UI toolkit.
*   **[Material 3](https://m3.material.io/)**: Design system components.
