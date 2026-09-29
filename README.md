# 跑跑桌面 Launcher 壳

这是一个独立的 Android Launcher 壳，不修改原始 APK。它注册为系统 Home 应用，并在按 Home 键时启动：

`com.paopao.lpyouthlaunch/com.carhome.HomeEntryActivity`

## 使用方法

1. 先在设备上安装原始“跑跑桌面” APK。
2. 用 Android Studio 打开本目录并构建 `app`。
3. 安装生成的壳 APK。
4. 按 Home 键，在系统弹窗中选择“跑跑桌面 Launcher 壳”。

如果目标 APK 未安装或因为车机依赖无法启动，壳会显示提示页。

## Android 9 系统栏辅助

打开“跑跑桌面 Launcher 壳”的应用图标，先通过网络 ADB 执行一次授权：

```bash
adb shell pm grant com.example.carhomewrapper android.permission.WRITE_SECURE_SETTINGS
```

然后在辅助工具中选择：

- “主页模式”：只隐藏跑跑桌面的顶部状态栏，保留底部导航栏。
- “三分屏模式”：隐藏顶部状态栏和底部导航栏。
- “恢复系统原来的系统栏设置”：写回第一次切换前保存的 `policy_control`。

这是 Android 9 的旧式系统栏策略，是否被车机厂商的 SystemUI 接受，需要在实机验证。如果按钮提示成功但画面没有变化，说明该车机的底栏不是 Android 标准导航栏，或者厂商 SystemUI 拦截了 `policy_control`；这时普通辅助 APK 无法强制修改，需要厂商权限、root 或系统签名权限。

工具页还内置了一个轻量 ADB 客户端。车机已开启 TCP ADB 时，可以填入 `127.0.0.1:5555` 或车机 IP/端口，点击“连接 ADB 并授权系统栏控制”。首次连接需要在车机上确认 RSA 授权；成功后它会执行：

```bash
pm grant com.example.carhomewrapper android.permission.WRITE_SECURE_SETTINGS
```

该客户端只执行这一条授权命令，不提供任意 Shell 输入框。

## 开机与模式监听

壳声明为 Home 后，首次 Home/开机会进入一次初始化页面；ADB 授权成功后记录完成状态，后续直接启动跑跑桌面。无障碍服务会继续驻留：

- 在系统设置包内，只处理无线 ADB 相关的明确文字，尝试点击一次开关。
- 在跑跑桌面包内，读取无障碍树暴露的 Compose 文本；看到“三分屏”等明确标记时应用全屏策略，看到“壁纸与车辆信息 / 桌面模式”等主页标记时应用只隐藏顶部栏的策略。
- 如果当前页面没有暴露这些文字，辅助工具不会猜测模式，避免把两个模式切错。此时需要再根据车机实际无障碍树补充标记，或者改用截图识别。

## 自动打开 ADB

壳中已加入与“小零工具箱”相同思路的第一层辅助：

1. 在壳的工具页面打开“无障碍设置”，手动启用“跑跑桌面辅助工具”。
2. 返回工具页面，点击“打开无线调试 / ADB 设置”。
3. 无障碍服务只在系统设置页面中查找“无线调试 / Wi-Fi ADB / 网络调试”等明确文字，并尝试点击对应设置行。

Android 9 标准系统通常没有 Android 11 的“无线调试”页面，因此找不到入口时会回退到“开发者选项”。这一步不能凭普通 APK 强行启动 `adbd`；如果车机已经通过局域网开启 ADB，仍可直接使用上面的 ADB 授权命令。后续若确认车机有厂商自定义的 ADB 页面，再按实际页面文字补充自动点击规则。

## 恢复原桌面

在系统设置中进入“默认应用 / 默认桌面 / Home 应用”，重新选择原来的桌面。

## 注意

这个目标 APK 声明了 `android.car`、Leapmotor 相关 native 依赖和车机权限。在普通手机上，壳本身可以成为 Launcher，但目标应用未必能够正常运行。

## GitHub Actions 打包

仓库中的 `.github/workflows/build-apk.yml` 会在推送到 `main`、Pull Request 或手动运行时构建 debug APK，并上传为 Actions artifact：

`app/build/outputs/apk/debug/app-debug.apk`

构建环境使用 Java 17、Gradle 8.2 和 Android Gradle Plugin 8.2.2。下载 artifact 后，用 `adb install -r app-debug.apk` 安装即可。
