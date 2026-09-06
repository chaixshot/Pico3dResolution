<div align="center">
<img src="Resource/icon.webp" width="128" height="128"/>

# Pico3DResolution

[English](README.md) | [中文](README_zh.md) | [Русский](README_ru.md)

### 🚀 A simple tool to globally adjust 3D rendering resolution for Pico VR headsets

</div>

## 👓 Screenshot

<image src="Resource/screenshot.jpeg" width="400"/>

## 🌟 Key Features

* **🎯 Global Resolution Scaling:** Change the internal rendering resolution for all 3D VR applications.
* **⚡ GPU Power Level Controls:** Manually set the GPU's `max_pwrlevel` and `min_pwrlevel` to lock performance states (ranging from Peak Performance to Power Saving).
* **📐 Advanced Rendering Controls:**
  * **Stencil Mesh:** Optimize GPU resources by preventing rendering of hidden pixels.
  * **Foveated Rendering:** Optimize performance by concentrating resolution in the central focus area.
  * **Texture FOV:** Fine-tune the field of view for rendered eye textures.
* **🔄 One-Click Restore:** A dedicated "Restore Default" button to instantly revert all settings to safe, standard Pico values.
* **🧩 Boot Persistence:** Integrated LSPosed (Xposed) module support to automatically re-apply GPU power levels after every reboot.
* **❓ Interactive Help:** Integrated documentation for each setting to explain technical details and trade-offs.
* **📊 Optimized Presets:** Choose from predefined values optimized for performance or clarity:
  * **384px**: Ultra Performance
  * **752px**: Performance
  * **1504px**: Default
  * **2160px**: Native Resolution
  * **2448px**: Pico Specific
  * **2816px**: Anti-aliasing
* **🔍 Real-time Verification:** Automatically verifies if the database changes were successful before rebooting.
* **🛠️ Modern UI:** Responsive Jetpack Compose interface, optimized for VR landscape mode with a dual-column layout.
* **🌐 Multi-language Support:** Full localization for 27 languages with technical industry terminology.
* **🔐 Root Safety:** Uses `content` commands to safely modify the system's PVR configuration.

## ⛏️ Prerequisites

* **Device:** Pico 4 Headset (Phoenix/China firmware supported).
* **Superuser:** **[Root Access](https://github.com/chaixshot/more-picohaxx)** is required to apply changes to system files.
* **Environment:** **[LSPosed Framework](https://github.com/JingMatrix/Vector/releases/tag/v2.0)** must be installed and active.
* **Permission:** Grant root access when prompted by the app.
* **LSPosed Scope:** Ensure `System Framework (android)` is selected in the LSPosed module scope.

## 📐 How to use?

1. **Download and Install** the latest `Pico3dResolution.apk`.
2. **Open the app** and grant **Root/Superuser** permissions when requested.
3. **Select your target resolution** and/or **GPU Power Levels** from the dropdown menus.
4. **Click "Apply"**.
    * If resolution/rendering settings were changed, the app will show **"Apply & Reboot"** and the headset will restart.
    * If only **Power Levels** were changed, the app will show **"Apply"** and changes take effect immediately without a reboot.
5. **(Optional) Auto-Apply at Boot:** To keep your GPU power levels active after a reboot, enable the module in the **LSPosed Manager** app.
6. **Wait for the process to complete**. Your new settings are now active!

## ⁉️ FAQ / Troubleshooting

* **Why does it need a reboot?**
  * The Pico VR service and compositor only allocate their internal rendering buffers at startup. A reboot is necessary to force the system to read the new `sdk_eyebuffer` values from the database.
* **Will this impact performance?**
  * Yes. Higher resolutions (e.g., 2160px or 2816px) significantly increase GPU load. Some games may experience frame drops or increased heat.
* **How do I verify the change?**
  * You can use the **PICO Metrics Tool** to see the active "EBW" (Eye Buffer Width) and "EBH" (Eye Buffer Height) in real-time inside the headset.

## 🛠️ Technical Details

### How it works

* **Resolution & Rendering:** This app uses root access to modify the system's PVR configuration database (`/data/user_de/0/com.pvr.configuration/databases/config.db`). It targets parameters like `sdk_eyebuffer`, `sdk_enableFFRBySYS`, `sdk_stencilMeshStatus`, and `sdk_EyeTextureFov` in the `RuleBean` and `ConfigBean` tables.
* **GPU Performance:** It modifies the kernel sysfs nodes located at `/sys/class/kgsl/kgsl-3d0/max_pwrlevel` and `min_pwrlevel` to force specific GPU frequency states.
* **Boot Restoration:** An integrated LSPosed module hooks into the `system_server` process to trigger the app's restoration service via a `ContentProvider` query immediately after boot completion.

## 🙏 Special thanks to

* [Jetpack Compose](https://developer.android.com/compose) - Modern UI toolkit.
* [Material 3](https://m3.material.io/) - Design system components.
